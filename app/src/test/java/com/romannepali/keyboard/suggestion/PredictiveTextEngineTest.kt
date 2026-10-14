package com.romannepali.keyboard.suggestion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PredictiveTextEngineTest {

    private lateinit var context: Context
    private lateinit var engine: PredictiveTextEngine

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        engine = PredictiveTextEngine(context)
    }

    @After
    fun tearDown() {
        engine.close()
    }

    @Test
    fun fetchCandidatePhrasesNow_returnsMatchingPhrases() = runBlocking {
        val results = engine.fetchCandidatePhrasesNow("namaste")
        assertTrue("Expected candidates for 'namaste'", results.isNotEmpty())
        assertTrue(results.any { it.text.contains("namaste") })
    }

    @Test
    fun debouncedLookup_updatesCandidatesStateFlow() = runBlocking {
        engine.debounceDelayMs = 20L
        engine.onInputTextChanged("dhanyabad")
        // Wait for debounce delay
        delay(60L)

        val candidates = engine.candidates.value
        assertTrue("Candidates should update after debounce", candidates.isNotEmpty())
        val top = engine.getHighestRankedPrediction()
        assertNotNull("Top prediction should be available", top)
    }

    @Test
    fun emptyInput_returnsDefaultCandidates() = runBlocking {
        val candidates = engine.fetchCandidatePhrasesNow("")
        assertTrue("Default candidates should not be empty", candidates.isNotEmpty())
    }

    @Test
    fun recordWordOrPhrase_executesWithoutError() = runBlocking {
        engine.recordWordOrPhrase("tapai kasto")
        engine.recordWordOrPhrase("ramro")
        // Check that querying afterwards works cleanly
        val results = engine.fetchCandidatePhrasesNow("tapai")
        assertTrue(results.isNotEmpty())
    }
}
