package com.romannepali.keyboard.dictionary.room

import android.content.Context
import com.romannepali.keyboard.suggestion.NepaliTransliteration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

/** Candidate suggestion produced by the Room auto-correction engine. */
data class AutoCorrectCandidate(
    val word: String,
    val devanagari: String,
    val distance: Int,
    val confidence: Float,
    val isExactOrPrefix: Boolean
)

/**
 * Repository abstracting Room database operations and providing auto-correction
 * algorithms for Romanized Nepali inputs.
 */
class NepaliDictionaryRepository(private val context: Context) {

    private val dao = NepaliWordDatabase.getDatabase(context).wordDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val personalWords: Flow<List<NepaliWordEntity>> = dao.getPersonalWords()
    val learnedWords: Flow<List<NepaliWordEntity>> = dao.getLearnedWords()
    val blockedWords: Flow<List<NepaliWordEntity>> = dao.getBlockedWords()
    val allActiveWords: Flow<List<NepaliWordEntity>> = dao.getAllActiveWords()

    init {
        scope.launch {
            seedInitialDictionaryIfEmpty()
        }
    }

    suspend fun searchPrefix(prefix: String, limit: Int = 10): List<NepaliWordEntity> =
        withContext(Dispatchers.IO) {
            seedInitialDictionaryIfEmpty()
            dao.getWordsByPrefix(prefix.lowercase(Locale.ROOT), limit)
        }

    suspend fun findWord(word: String): NepaliWordEntity? =
        withContext(Dispatchers.IO) {
            seedInitialDictionaryIfEmpty()
            dao.findWord(word.lowercase(Locale.ROOT))
        }

    suspend fun addPersonalWord(word: String, devanagari: String = ""): Boolean =
        withContext(Dispatchers.IO) {
            val cleanWord = word.trim().lowercase(Locale.ROOT)
            if (cleanWord.isEmpty()) return@withContext false
            val dev = if (devanagari.isNotEmpty()) devanagari else NepaliTransliteration.toDevanagari(cleanWord)
            val entity = NepaliWordEntity(
                word = cleanWord,
                devanagari = dev,
                frequency = 100,
                isPersonal = true,
                isLearned = false,
                isBlocked = false,
                timestamp = System.currentTimeMillis()
            )
            dao.insertOrUpdate(entity)
            true
        }

    suspend fun recordWordUsed(word: String) = withContext(Dispatchers.IO) {
        val clean = word.trim().lowercase(Locale.ROOT)
        if (clean.length < 2) return@withContext
        val existing = dao.findWord(clean)
        if (existing != null) {
            dao.incrementFrequency(clean)
        } else {
            val dev = NepaliTransliteration.toDevanagari(clean)
            dao.insertOrUpdate(
                NepaliWordEntity(
                    word = clean,
                    devanagari = dev,
                    frequency = 1,
                    isPersonal = false,
                    isLearned = true,
                    timestamp = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun deleteWord(word: String) = withContext(Dispatchers.IO) {
        dao.deleteByWord(word.lowercase(Locale.ROOT))
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun clearLearnedWords() = withContext(Dispatchers.IO) {
        dao.clearLearnedWords()
    }

    /**
     * Fast auto-correction algorithm:
     * 1. Prefix query against Room dictionary
     * 2. Levenshtein fuzzy distance matching against common Nepali vocabulary
     * Returns top ranked corrections.
     */
    suspend fun getAutoCorrectSuggestions(input: String, maxResults: Int = 5): List<AutoCorrectCandidate> =
        withContext(Dispatchers.IO) {
            seedInitialDictionaryIfEmpty()
            val query = input.lowercase(Locale.ROOT).trim()
            if (query.length < 2) return@withContext emptyList()

            val candidates = mutableListOf<AutoCorrectCandidate>()

            // 1. Prefix & exact matches from Room
            val prefixMatches = dao.getWordsByPrefix(query, maxResults)
            for (entity in prefixMatches) {
                candidates.add(
                    AutoCorrectCandidate(
                        word = entity.word,
                        devanagari = entity.devanagari,
                        distance = if (entity.word == query) 0 else 1,
                        confidence = if (entity.word == query) 1.0f else 0.85f,
                        isExactOrPrefix = true
                    )
                )
            }

            // 2. Fuzzy edit-distance matching if not an exact match
            if (candidates.none { it.word == query }) {
                val topVocabulary = dao.getTopWords(300)
                val fuzzyList = mutableListOf<AutoCorrectCandidate>()
                for (item in topVocabulary) {
                    val dist = computeLevenshtein(query, item.word, maxDist = 2)
                    if (dist in 1..2) {
                        val score = (1.0f - (dist * 0.28f)) + (item.frequency.coerceAtMost(100) / 500f)
                        fuzzyList.add(
                            AutoCorrectCandidate(
                                word = item.word,
                                devanagari = item.devanagari,
                                distance = dist,
                                confidence = score.coerceIn(0.4f, 0.95f),
                                isExactOrPrefix = false
                            )
                        )
                    }
                }
                fuzzyList.sortByDescending { it.confidence }
                candidates.addAll(fuzzyList.take(maxResults - candidates.size))
            }

            candidates.distinctBy { it.word }.take(maxResults)
        }

    private fun computeLevenshtein(s1: String, s2: String, maxDist: Int = 2): Int {
        if (s1 == s2) return 0
        if (abs(s1.length - s2.length) > maxDist) return maxDist + 1
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j
        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[s1.length][s2.length]
    }

    private suspend fun seedInitialDictionaryIfEmpty() {
        if (dao.getCount() > 0) return
        val initialEntities = NepaliSeedData.COMMON_NEPALI_WORDS.map { (roman, dev, freq) ->
            NepaliWordEntity(
                word = roman,
                devanagari = dev,
                frequency = freq,
                isPersonal = false,
                isLearned = false
            )
        }
        dao.insertAll(initialEntities)
    }
}
