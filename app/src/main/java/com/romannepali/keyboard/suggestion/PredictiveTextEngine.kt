package com.romannepali.keyboard.suggestion

import android.content.Context
import com.romannepali.keyboard.dictionary.room.NepaliDictionaryRepository
import com.romannepali.keyboard.dictionary.room.NepaliPhraseRepository
import com.romannepali.keyboard.dictionary.room.NepaliWordDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Candidate prediction phrase or word containing both Romanized text and Devanagari script.
 */
data class CandidatePhrase(
    val text: String,
    val devanagari: String = "",
    val frequency: Int = 1,
    val isPhrase: Boolean = false
)

/**
 * PredictiveTextEngine utilizes Room database (phrases and word dictionaries)
 * to fetch candidate phrases based on the current input text.
 * It implements a debounced lookup mechanism to maintain high keyboard typing performance.
 */
class PredictiveTextEngine(
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
) {
    private val database = NepaliWordDatabase.getDatabase(context)
    private val phraseDao = database.phraseDao()
    private val wordDao = database.wordDao()
    private val phraseRepo = NepaliPhraseRepository(context)
    private val dictionaryRepo = NepaliDictionaryRepository(context)

    private val _candidates = MutableStateFlow<List<CandidatePhrase>>(emptyList())
    val candidates: StateFlow<List<CandidatePhrase>> = _candidates.asStateFlow()

    private val _currentQuery = MutableStateFlow("")
    val currentQuery: StateFlow<String> = _currentQuery.asStateFlow()

    private var debounceJob: Job? = null
    var debounceDelayMs: Long = 80L // Debounce window for rapid typing responsiveness

    init {
        scope.launch {
            loadDefaultCandidates()
        }
    }

    /**
     * Called whenever typing occurs. Debounces candidate phrase queries to avoid
     * excessive database round-trips while typing rapidly.
     */
    fun onInputTextChanged(newText: String) {
        val trimmed = newText.trim()
        _currentQuery.value = trimmed

        debounceJob?.cancel()
        if (trimmed.isEmpty()) {
            debounceJob = scope.launch {
                loadDefaultCandidates()
            }
            return
        }

        debounceJob = scope.launch {
            delay(debounceDelayMs)
            val results = withContext(Dispatchers.IO) {
                queryCandidates(trimmed)
            }
            _candidates.value = results
        }
    }

    /**
     * Suspend function to fetch candidate phrases immediately without debouncing.
     */
    suspend fun fetchCandidatePhrasesNow(rawQuery: String): List<CandidatePhrase> =
        withContext(Dispatchers.IO) {
            val results = queryCandidates(rawQuery)
            _candidates.value = results
            results
        }

    private suspend fun queryCandidates(rawQuery: String): List<CandidatePhrase> {
        val query = rawQuery.trim().lowercase(Locale.ROOT)
        if (query.isEmpty()) {
            return getDefaultCandidates()
        }

        val resultList = mutableListOf<CandidatePhrase>()

        // 1. Search matching phrases in Room database
        val matchingPhrases = phraseDao.searchPhrases(query, limit = 6)
        for (phrase in matchingPhrases) {
            resultList.add(
                CandidatePhrase(
                    text = phrase.phrase,
                    devanagari = phrase.devanagariPhrase,
                    frequency = phrase.frequency,
                    isPhrase = true
                )
            )
        }

        // 2. Search matching prefix words in Room database
        val matchingWords = wordDao.getWordsByPrefix(query, limit = 6)
        for (word in matchingWords) {
            if (resultList.none { it.text.equals(word.word, ignoreCase = true) }) {
                resultList.add(
                    CandidatePhrase(
                        text = word.word,
                        devanagari = word.devanagari,
                        frequency = word.frequency,
                        isPhrase = false
                    )
                )
            }
        }

        // 3. Fallback to fuzzy auto-correct candidate suggestions if few matches
        if (resultList.size < 3) {
            val autoCorrections = dictionaryRepo.getAutoCorrectSuggestions(query, maxResults = 5)
            for (candidate in autoCorrections) {
                if (resultList.none { it.text.equals(candidate.word, ignoreCase = true) }) {
                    resultList.add(
                        CandidatePhrase(
                            text = candidate.word,
                            devanagari = candidate.devanagari,
                            frequency = (candidate.confidence * 100).toInt(),
                            isPhrase = false
                        )
                    )
                }
            }
        }

        // Provide Devanagari transliteration fallback if missing
        return resultList.map { candidate ->
            if (candidate.devanagari.isBlank()) {
                candidate.copy(devanagari = NepaliTransliteration.toDevanagari(candidate.text))
            } else {
                candidate
            }
        }
    }

    private suspend fun getDefaultCandidates(): List<CandidatePhrase> {
        val topPhrases = phraseDao.getTopPhrases(limit = 5)
        return topPhrases.map {
            CandidatePhrase(
                text = it.phrase,
                devanagari = it.devanagariPhrase,
                frequency = it.frequency,
                isPhrase = true
            )
        }
    }

    private suspend fun loadDefaultCandidates() {
        val defaults = withContext(Dispatchers.IO) {
            getDefaultCandidates()
        }
        _candidates.value = defaults
    }

    /**
     * Returns the highest-ranked prediction for the current input, or null if none available.
     */
    fun getHighestRankedPrediction(): String? {
        val list = _candidates.value
        return list.firstOrNull()?.text
    }

    /**
     * Records phrase/word usage into Room database to reinforce future rankings.
     */
    fun recordWordOrPhrase(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            if (trimmed.contains(" ")) {
                phraseRepo.recordPhraseUsed(trimmed)
            } else {
                dictionaryRepo.recordWordUsed(trimmed)
            }
        }
    }

    fun close() {
        debounceJob?.cancel()
    }
}
