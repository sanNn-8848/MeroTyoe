package com.romannepali.keyboard.dictionary.room

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NepaliDictionaryRepositoryTest {

    private lateinit var repository: NepaliDictionaryRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = NepaliDictionaryRepository(context)
    }

    @Test
    fun addAndFindPersonalWord() = runBlocking {
        val success = repository.addPersonalWord("pokharacity", "पोखरा सिटी")
        assertTrue(success)

        val found = repository.findWord("pokharacity")
        assertNotNull(found)
        assertEquals("pokharacity", found?.word)
        assertEquals("पोखरा सिटी", found?.devanagari)
        assertTrue(found?.isPersonal == true)
    }

    @Test
    fun autoCorrection_suggestsCommonNepaliWords() = runBlocking {
        // "namste" typo should auto-correct to "namaste"
        val corrections = repository.getAutoCorrectSuggestions("namste", maxResults = 3)
        assertTrue("Expected auto-correct candidates for 'namste'", corrections.isNotEmpty())
        val best = corrections.first()
        assertEquals("namaste", best.word)
        assertEquals("नमस्ते", best.devanagari)
    }

    @Test
    fun prefixSearch_returnsMatchingWords() = runBlocking {
        val results = repository.searchPrefix("nama", limit = 5)
        assertTrue(results.any { it.word == "namaste" })
    }
}
