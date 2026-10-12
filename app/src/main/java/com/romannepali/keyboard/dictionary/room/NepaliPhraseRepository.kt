package com.romannepali.keyboard.dictionary.room

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Repository for managing frequently used Nepali phrases for predictive text.
 */
class NepaliPhraseRepository(context: Context) {

    private val phraseDao = NepaliWordDatabase.getDatabase(context).phraseDao()
    private val scope = CoroutineScope(Dispatchers.IO)

    val allPhrases: Flow<List<NepaliPhraseEntity>> = phraseDao.getAllPhrases()

    init {
        scope.launch {
            seedPhrasesIfEmpty()
        }
    }

    suspend fun getPhrasesByCategory(category: String): Flow<List<NepaliPhraseEntity>> =
        withContext(Dispatchers.IO) {
            phraseDao.getPhrasesByCategory(category)
        }

    suspend fun searchPhrases(prefix: String, limit: Int = 10): List<NepaliPhraseEntity> =
        withContext(Dispatchers.IO) {
            seedPhrasesIfEmpty()
            phraseDao.searchPhrases(prefix.lowercase(Locale.ROOT), limit)
        }

    suspend fun getTopPhrases(limit: Int = 15): List<NepaliPhraseEntity> =
        withContext(Dispatchers.IO) {
            seedPhrasesIfEmpty()
            phraseDao.getTopPhrases(limit)
        }

    suspend fun addPhrase(
        phrase: String,
        devanagariPhrase: String,
        category: String = "general"
    ): Boolean = withContext(Dispatchers.IO) {
        val clean = phrase.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return@withContext false
        val entity = NepaliPhraseEntity(
            phrase = clean,
            devanagariPhrase = devanagariPhrase.trim(),
            category = category,
            frequency = 10,
            timestamp = System.currentTimeMillis()
        )
        phraseDao.insertOrUpdate(entity)
        true
    }

    suspend fun recordPhraseUsed(phrase: String) = withContext(Dispatchers.IO) {
        phraseDao.incrementFrequency(phrase.trim().lowercase(Locale.ROOT))
    }

    suspend fun deletePhrase(phrase: String) = withContext(Dispatchers.IO) {
        phraseDao.deleteByPhrase(phrase.trim().lowercase(Locale.ROOT))
    }

    private suspend fun seedPhrasesIfEmpty() {
        if (phraseDao.getCount() > 0) return
        val seed = listOf(
            NepaliPhraseEntity(phrase = "namaste sathi", devanagariPhrase = "नमस्ते साथी", category = "greetings", frequency = 50),
            NepaliPhraseEntity(phrase = "kasto cha tapai lai", devanagariPhrase = "कस्तो छ तपाईंलाई", category = "greetings", frequency = 45),
            NepaliPhraseEntity(phrase = "khana khanu bhayo", devanagariPhrase = "खाना खानु भयो", category = "daily", frequency = 40),
            NepaliPhraseEntity(phrase = "dhanyabad sathi", devanagariPhrase = "धन्यवाद साथी", category = "courtesy", frequency = 45),
            NepaliPhraseEntity(phrase = "subha prabhat", devanagariPhrase = "शुभ प्रभात", category = "greetings", frequency = 35),
            NepaliPhraseEntity(phrase = "subha ratri", devanagariPhrase = "शुभ रात्रि", category = "greetings", frequency = 35),
            NepaliPhraseEntity(phrase = "mero desh nepal", devanagariPhrase = "मेरो देश नेपाल", category = "general", frequency = 48),
            NepaliPhraseEntity(phrase = "timi kata chau", devanagariPhrase = "तिमी कहाँ छौ", category = "questions", frequency = 42),
            NepaliPhraseEntity(phrase = "tapai kata hunuhuncha", devanagariPhrase = "तपाईं कहाँ हुनुहुन्छ", category = "questions", frequency = 40),
            NepaliPhraseEntity(phrase = "bholi bhetla", devanagariPhrase = "भोलि भेटौँला", category = "courtesy", frequency = 38),
            NepaliPhraseEntity(phrase = "sanchai huncha", devanagariPhrase = "सञ्चै हुनुहुन्छ", category = "greetings", frequency = 36),
            NepaliPhraseEntity(phrase = "dherai ramro cha", devanagariPhrase = "धेरै राम्रो छ", category = "general", frequency = 34),
            NepaliPhraseEntity(phrase = "ke gari raheko", devanagariPhrase = "के गरिरहेको छौ", category = "questions", frequency = 32),
            NepaliPhraseEntity(phrase = "pachi kura garau", devanagariPhrase = "पछि कुरा गरौँला", category = "courtesy", frequency = 30)
        )
        phraseDao.insertAll(seed)
    }
}
