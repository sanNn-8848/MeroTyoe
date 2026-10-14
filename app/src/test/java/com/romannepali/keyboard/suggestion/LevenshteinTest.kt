package com.romannepali.keyboard.suggestion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevenshteinTest {

    @Test
    fun testBasicLevenshteinDistance() {
        assertEquals(0, Levenshtein.distance("namaste", "namaste"))
        assertEquals(1, Levenshtein.distance("namste", "namaste")) // deletion
        assertEquals(1, Levenshtein.distance("namastee", "namaste")) // insertion
        assertEquals(1, Levenshtein.distance("kamaste", "namaste")) // substitution
        assertEquals(2, Levenshtein.distance("namsate", "namaste")) // 2 edits in standard Levenshtein
    }

    @Test
    fun testDamerauTranspositionDistance() {
        // Transposition of adjacent characters is 1 edit in Damerau
        assertEquals(1, Levenshtein.damerauDistance("namsate", "namaste"))
        assertEquals(1, Levenshtein.damerauDistance("tapaiko", "tapaiko"))
        assertEquals(1, Levenshtein.damerauDistance("tapaiok", "tapaiko"))
        assertEquals(1, Levenshtein.damerauDistance("tapiko", "tapaiko"))
    }

    @Test
    fun testMaxDistanceEarlyExit() {
        // Words with difference > maxDistance return maxDistance + 1 without full evaluation
        val maxDist = 2
        val dist = Levenshtein.damerauDistance("nepal", "internationalization", maxDistance = maxDist)
        assertTrue(dist > maxDist)
    }

    @Test
    fun testSimilarityRatio() {
        val simExact = Levenshtein.similarity("namaste", "namaste")
        assertEquals(1.0, simExact, 0.001)

        val simTypo = Levenshtein.similarity("namsate", "namaste")
        assertTrue("Similarity for typo namsate should be high", simTypo >= 0.85)

        val simDifferent = Levenshtein.similarity("nepal", "xyz", maxDistance = 2)
        assertEquals(0.0, simDifferent, 0.001)
    }

    @Test
    fun testSuggestCorrectionsFromDictionary() {
        val dictionary = listOf(
            "namaste" to 1000,
            "namaskar" to 300,
            "dhanyabad" to 800,
            "tapai" to 950,
            "tapaiko" to 700,
            "nepal" to 1200
        )

        // Misspelled: "namsate" -> should suggest "namaste" at rank 1
        val suggestions = Levenshtein.suggestCorrections(
            query = "namsate",
            dictionaryWords = dictionary,
            maxDistance = 2,
            limit = 3
        )

        assertTrue(suggestions.isNotEmpty())
        assertEquals("namaste", suggestions.first().word)
        assertEquals(1, suggestions.first().distance)
        assertTrue(suggestions.first().similarity > 0.8)

        // Misspelled: "nepall" -> should suggest "nepal"
        val nepalSuggestions = Levenshtein.suggestCorrections(
            query = "nepall",
            dictionaryWords = dictionary,
            maxDistance = 2,
            limit = 3
        )
        assertTrue(nepalSuggestions.isNotEmpty())
        assertEquals("nepal", nepalSuggestions.first().word)
    }

    @Test
    fun testTrieFuzzySearch() {
        val trie = Trie()
        trie.insert("namaste", 1000)
        trie.insert("namaskar", 500)
        trie.insert("dhanyabad", 800)
        trie.insert("nepal", 1200)

        val fuzzyResults = trie.searchFuzzy("namsate", maxDistance = 2)
        assertTrue(fuzzyResults.any { it.word == "namaste" && it.distance == 1 })
    }
}
