package com.romannepali.keyboard.suggestion

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, high-performance Levenshtein and Damerau-Levenshtein distance algorithm
 * optimized for Android keyboard input method engines.
 *
 * Characteristics:
 * - O(min(N, M)) space complexity using 1D rolling buffers instead of (N+1)*(M+1) matrix allocation.
 * - Early boundary pruning: terminates immediately if length difference exceeds [maxDistance].
 * - Early row termination: breaks dynamic programming loop when the minimum row value exceeds [maxDistance].
 * - Supports Damerau adjacent character transpositions (e.g. "namsate" <-> "namaste") for accurate typing typo recovery.
 * - Zero external dependencies, allocation-conscious for low memory footprint on high-speed typing loops.
 */
object Levenshtein {

    /**
     * Candidate correction suggested based on Levenshtein dictionary proximity.
     */
    data class Candidate(
        val word: String,
        val distance: Int,
        val similarity: Double,
        val frequency: Int,
        val score: Double
    )

    /**
     * Computes the bounded standard Levenshtein distance between [s1] and [s2].
     * If the distance exceeds [maxDistance], returns [maxDistance] + 1 immediately.
     */
    fun distance(s1: CharSequence, s2: CharSequence, maxDistance: Int = Int.MAX_VALUE): Int {
        if (s1 == s2) return 0
        val len1 = s1.length
        val len2 = s2.length
        if (abs(len1 - len2) > maxDistance) return maxDistance + 1
        if (len1 == 0) return min(len2, maxDistance + 1)
        if (len2 == 0) return min(len1, maxDistance + 1)

        // Ensure s2 is the shorter sequence to minimize buffer memory allocation
        val (a, b) = if (len1 < len2) s2 to s1 else s1 to s2
        val aLen = a.length
        val bLen = b.length

        var prev = IntArray(bLen + 1) { it }
        var curr = IntArray(bLen + 1)

        for (i in 1..aLen) {
            curr[0] = i
            var minInRow = curr[0]
            val aChar = a[i - 1]

            for (j in 1..bLen) {
                val cost = if (aChar == b[j - 1]) 0 else 1
                curr[j] = minOf(
                    curr[j - 1] + 1,       // insertion
                    prev[j] + 1,           // deletion
                    prev[j - 1] + cost     // substitution
                )
                if (curr[j] < minInRow) {
                    minInRow = curr[j]
                }
            }

            if (minInRow > maxDistance) {
                return maxDistance + 1
            }

            val temp = prev
            prev = curr
            curr = temp
        }

        val res = prev[bLen]
        return if (res <= maxDistance) res else maxDistance + 1
    }

    /**
     * Computes bounded Damerau-Levenshtein distance supporting insertions, deletions,
     * substitutions, and adjacent transpositions (critical for touch typing typos).
     */
    fun damerauDistance(s1: CharSequence, s2: CharSequence, maxDistance: Int = 2): Int {
        if (s1 == s2) return 0
        val len1 = s1.length
        val len2 = s2.length
        if (abs(len1 - len2) > maxDistance) return maxDistance + 1
        if (len1 == 0) return min(len2, maxDistance + 1)
        if (len2 == 0) return min(len1, maxDistance + 1)

        var prevPrev = IntArray(len2 + 1)
        var prev = IntArray(len2 + 1) { it }
        var curr = IntArray(len2 + 1)

        for (i in 1..len1) {
            curr[0] = i
            var minInRow = curr[0]
            val s1Char = s1[i - 1]

            for (j in 1..len2) {
                val cost = if (s1Char == s2[j - 1]) 0 else 1
                var valCurrent = minOf(
                    curr[j - 1] + 1,        // insertion
                    prev[j] + 1,            // deletion
                    prev[j - 1] + cost      // substitution
                )

                // Adjacent transposition check
                if (i > 1 && j > 1 && s1Char == s2[j - 2] && s1[i - 2] == s2[j - 1]) {
                    valCurrent = min(valCurrent, prevPrev[j - 2] + 1)
                }

                curr[j] = valCurrent
                if (valCurrent < minInRow) minInRow = valCurrent
            }

            if (minInRow > maxDistance) {
                return maxDistance + 1
            }

            val temp = prevPrev
            prevPrev = prev
            prev = curr
            curr = temp
        }

        val res = prev[len2]
        return if (res <= maxDistance) res else maxDistance + 1
    }

    /**
     * Normalized similarity ratio between 0.0 (completely distinct) and 1.0 (exact match).
     */
    fun similarity(s1: CharSequence, s2: CharSequence, maxDistance: Int = 2): Double {
        if (s1 == s2) return 1.0
        val maxLen = max(s1.length, s2.length)
        if (maxLen == 0) return 1.0
        val dist = damerauDistance(s1, s2, maxDistance)
        if (dist > maxDistance) return 0.0
        return (1.0 - dist.toDouble() / maxLen).coerceIn(0.0, 1.0)
    }

    /**
     * Calculates dictionary proximity score combining edit distance, normalized similarity,
     * and logarithmic frequency weight.
     */
    fun calculateProximityScore(distance: Int, similarity: Double, frequency: Int): Double {
        val freqWeight = if (frequency > 0) Math.log(1.0 + frequency) / 10.0 else 0.0
        val distPenalty = distance * 0.35
        return (similarity * 0.75 - distPenalty + freqWeight).coerceAtLeast(0.0)
    }

    /**
     * Suggests candidate corrections for a potentially misspelled word from a dictionary
     * collection based on Levenshtein proximity.
     */
    fun suggestCorrections(
        query: String,
        dictionaryWords: Iterable<Pair<String, Int>>,
        maxDistance: Int = 2,
        limit: Int = 5
    ): List<Candidate> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.length < 2) return emptyList()

        val candidates = ArrayList<Candidate>()

        for ((word, freq) in dictionaryWords) {
            if (abs(word.length - cleanQuery.length) > maxDistance) continue
            val dist = damerauDistance(cleanQuery, word, maxDistance)
            if (dist in 1..maxDistance) {
                val sim = (1.0 - dist.toDouble() / max(cleanQuery.length, word.length)).coerceIn(0.0, 1.0)
                if (sim >= 0.50) {
                    val score = calculateProximityScore(dist, sim, freq)
                    candidates.add(
                        Candidate(
                            word = word,
                            distance = dist,
                            similarity = sim,
                            frequency = freq,
                            score = score
                        )
                    )
                }
            }
        }

        return candidates
            .sortedWith(
                compareByDescending<Candidate> { it.score }
                    .thenBy { it.distance }
                    .thenByDescending { it.frequency }
            )
            .take(limit)
    }
}
