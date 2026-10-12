package com.romannepali.keyboard.suggestion

import java.util.Locale

/**
 * High-speed Roman Nepali to Devanagari script transliterator.
 * Enables the keyboard to offer authentic Devanagari suggestions
 * in real-time as users type Romanized Nepali.
 */
object NepaliTransliteration {

    // Common words direct dictionary map for 100% natural Nepali Devanagari spelling
    private val COMMON_WORDS = mapOf(
        "namaste" to "नमस्ते",
        "namaskar" to "नमस्कार",
        "mero" to "मेरो",
        "meri" to "मेरी",
        "mera" to "मेरा",
        "timi" to "तिमी",
        "timro" to "तिम्रो",
        "tapai" to "तपाईं",
        "tapanko" to "तपाईंको",
        "hami" to "हामी",
        "hamro" to "हाम्रो",
        "u" to "ऊ",
        "usko" to "उसको",
        "unko" to "उनको",
        "ke" to "के",
        "k" to "के",
        "cha" to "छ",
        "chha" to "छ",
        "chaina" to "छैन",
        "chhain" to "छैन",
        "ho" to "हो",
        "hoina" to "होइन",
        "kina" to "किन",
        "kaha" to "कहाँ",
        "kahile" to "कहिले",
        "kasari" to "कसरी",
        "ko" to "को",
        "lai" to "लाई",
        "le" to "ले",
        "ma" to "म",
        "haru" to "हरू",
        "pani" to "पनि",
        "ta" to "त",
        "ani" to "अनि",
        "tara" to "तर",
        "ra" to "र",
        "la" to "ल",
        "huss" to "हुस्",
        "bholi" to "भोलि",
        "aaja" to "आज",
        "aja" to "आज",
        "hijo" to "हिजो",
        "asti" to "अस्ति",
        "ramro" to "राम्रो",
        "naramro" to "नराम्रो",
        "khusi" to "खुसी",
        "khushi" to "खुसी",
        "maya" to "माया",
        "dhanyabaad" to "धन्यवाद",
        "dhanyabad" to "धन्यवाद",
        "sanchai" to "सञ्चै",
        "nepal" to "नेपाल",
        "nepali" to "नेपाली",
        "ghar" to "घर",
        "khana" to "खाना",
        "paani" to "पानी",
        "pani" to "पानी",
        "bhat" to "भात",
        "sathi" to "साथी",
        "bhai" to "भाइ",
        "bahini" to "बहिनी",
        "daju" to "दाजु",
        "didi" to "दिदी",
        "aama" to "आमा",
        "bua" to "बुबा",
        "keta" to "केटा",
        "keti" to "केटी",
        "thik" to "ठिक",
        "garchu" to "गर्छु",
        "garcha" to "गर्छ",
        "garnu" to "गर्नु",
        "janchu" to "जान्छु",
        "aauchu" to "आउँछु",
        "khanchu" to "खान्छु",
        "dherai" to "धेरै",
        "ali" to "अलि",
        "bujhe" to "बुझें",
        "maya" to "माया"
    )

    private val VOWELS = mapOf(
        "aa" to "ा",
        "a" to "",
        "ii" to "ी",
        "ee" to "ी",
        "i" to "ि",
        "uu" to "ू",
        "oo" to "ू",
        "u" to "ु",
        "e" to "े",
        "ai" to "ै",
        "o" to "ो",
        "au" to "ौ",
        "ou" to "ौ",
        "ri" to "ृ"
    )

    private val INDEPENDENT_VOWELS = mapOf(
        "aa" to "आ",
        "a" to "अ",
        "ii" to "ई",
        "ee" to "ई",
        "i" to "इ",
        "uu" to "ऊ",
        "oo" to "ऊ",
        "u" to "उ",
        "e" to "ए",
        "ai" to "ऐ",
        "o" to "ओ",
        "au" to "औ",
        "ou" to "औ"
    )

    private val CONSONANTS = mapOf(
        "k" to "क", "kh" to "ख", "g" to "ग", "gh" to "घ", "ng" to "ङ",
        "ch" to "च", "chh" to "छ", "j" to "ज", "jh" to "झ", "yn" to "ञ",
        "T" to "ट", "Th" to "ठ", "D" to "ड", "Dh" to "ढ", "N" to "ण",
        "t" to "त", "th" to "थ", "d" to "द", "dh" to "ध", "n" to "न",
        "p" to "प", "ph" to "फ", "f" to "फ", "b" to "ब", "bh" to "भ", "m" to "म",
        "y" to "य", "r" to "र", "l" to "ल", "w" to "व", "v" to "व",
        "sh" to "श", "Sh" to "ष", "s" to "स", "h" to "ह",
        "ksh" to "क्ष", "tra" to "त्र", "gya" to "ज्ञ"
    )

    /**
     * Translates a Roman Nepali input string into Devanagari script.
     */
    fun toDevanagari(input: String): String {
        val clean = input.trim().lowercase(Locale.ROOT)
        if (clean.isEmpty()) return ""

        // Check common word dictionary first
        COMMON_WORDS[clean]?.let { return it }

        // Rule-based phonetic transliteration
        val sb = StringBuilder()
        var i = 0
        var isWordStart = true

        while (i < clean.length) {
            // Check 3-letter consonant conjuncts
            if (i + 3 <= clean.length && CONSONANTS.containsKey(clean.substring(i, i + 3))) {
                val c = CONSONANTS[clean.substring(i, i + 3)]!!
                i += 3
                i = appendConsonantVowel(clean, i, c, sb)
                isWordStart = false
                continue
            }

            // Check 2-letter consonants
            if (i + 2 <= clean.length && CONSONANTS.containsKey(clean.substring(i, i + 2))) {
                val c = CONSONANTS[clean.substring(i, i + 2)]!!
                i += 2
                i = appendConsonantVowel(clean, i, c, sb)
                isWordStart = false
                continue
            }

            // Check 1-letter consonants
            val single = clean[i].toString()
            if (CONSONANTS.containsKey(single)) {
                val c = CONSONANTS[single]!!
                i += 1
                i = appendConsonantVowel(clean, i, c, sb)
                isWordStart = false
                continue
            }

            // Check independent vowels at word start or after spaces
            if (isWordStart) {
                var matchedVowel = false
                for (vLen in 2 downTo 1) {
                    if (i + vLen <= clean.length) {
                        val sub = clean.substring(i, i + vLen)
                        INDEPENDENT_VOWELS[sub]?.let { iv ->
                            sb.append(iv)
                            i += vLen
                            matchedVowel = true
                        }
                        if (matchedVowel) break
                    }
                }
                if (matchedVowel) {
                    isWordStart = false
                    continue
                }
            }

            // Fallback: character as-is
            val ch = clean[i]
            if (ch.isWhitespace()) {
                isWordStart = true
            }
            sb.append(ch)
            i++
        }

        return sb.toString()
    }

    private fun appendConsonantVowel(
        text: String,
        startIndex: Int,
        consonant: String,
        out: StringBuilder
    ): Int {
        var idx = startIndex
        out.append(consonant)

        // Check 2-letter vowels
        if (idx + 2 <= text.length && VOWELS.containsKey(text.substring(idx, idx + 2))) {
            val v = VOWELS[text.substring(idx, idx + 2)]!!
            out.append(v)
            return idx + 2
        }

        // Check 1-letter vowels
        if (idx < text.length && VOWELS.containsKey(text[idx].toString())) {
            val v = VOWELS[text[idx].toString()]!!
            out.append(v)
            return idx + 1
        }

        // No vowel follows: append halant if next char is also a consonant
        if (idx < text.length && text[idx].isLetter()) {
            out.append("्")
        }

        return idx
    }
}
