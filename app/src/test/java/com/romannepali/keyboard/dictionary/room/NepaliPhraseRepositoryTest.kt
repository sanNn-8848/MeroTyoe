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
class NepaliPhraseRepositoryTest {

    private lateinit var repository: NepaliPhraseRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        repository = NepaliPhraseRepository(context)
    }

    @Test
    fun addAndSearchPhrase() = runBlocking {
        val success = repository.addPhrase(
            phrase = "shubha bihani sathi",
            devanagariPhrase = "शुभ बिहानी साथी",
            category = "greetings"
        )
        assertTrue(success)

        val found = repository.searchPhrases("shubha bihani", limit = 3)
        assertTrue(found.isNotEmpty())
        assertEquals("shubha bihani sathi", found.first().phrase)
        assertEquals("शुभ बिहानी साथी", found.first().devanagariPhrase)
    }

    @Test
    fun getTopPhrases_returnsPreSeededPhrases() = runBlocking {
        val top = repository.getTopPhrases(5)
        assertTrue(top.isNotEmpty())
    }
}
