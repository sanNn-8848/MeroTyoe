package com.romannepali.keyboard.suggestion

class TrieNode {
    val children = HashMap<Char, TrieNode>()
    var isEndOfWord = false
    var frequency = 0
    var word: String? = null
}

class Trie {
    private val root = TrieNode()

    fun insert(word: String, frequency: Int = 1) {
        val clean = word.trim().lowercase()

        if (clean.isEmpty() || frequency <= 0) return

        var current = root

        for (char in clean) {
            current = current.children.getOrPut(char) { TrieNode() }
        }

        current.isEndOfWord = true
        current.frequency = frequency
        current.word = clean
    }

    fun search(prefix: String): List<Pair<String, Int>> {
        val cleanPrefix = prefix.trim().lowercase()

        if (cleanPrefix.isEmpty()) {
            return getAllWords().sortedByDescending { it.second }
        }

        val results = mutableListOf<Pair<String, Int>>()
        var current = root

        for (char in cleanPrefix) {
            current = current.children[char] ?: return emptyList()
        }

        collectWords(current, results)

        return results.sortedByDescending { it.second }
    }

    private fun collectWords(
        node: TrieNode,
        results: MutableList<Pair<String, Int>>
    ) {
        if (node.isEndOfWord) {
            node.word?.let { word ->
                results.add(word to node.frequency)
            }
        }

        for (child in node.children.values) {
            collectWords(child, results)
        }
    }

    fun contains(word: String): Boolean {
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return false

        var current = root

        for (char in clean) {
            current = current.children[char] ?: return false
        }

        return current.isEndOfWord
    }

    fun getFrequency(word: String): Int {
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return 0

        var current = root

        for (char in clean) {
            current = current.children[char] ?: return 0
        }

        return if (current.isEndOfWord) {
            current.frequency
        } else {
            0
        }
    }

    fun getAllWords(): List<Pair<String, Int>> {
        val result = ArrayList<Pair<String, Int>>()

        fun collect(node: TrieNode) {
            if (node.isEndOfWord && node.word != null) {
                result.add(node.word!! to node.frequency)
            }

            for (child in node.children.values) {
                collect(child)
            }
        }

        collect(root)

        return result
    }

    fun remove(word: String): Boolean {
        val clean = word.trim().lowercase()

        if (clean.isEmpty()) return false

        var existed = false

        fun removeRec(node: TrieNode, index: Int): Boolean {
            if (index == clean.length) {
                existed = node.isEndOfWord

                if (!existed) return false

                node.isEndOfWord = false
                node.word = null
                node.frequency = 0

                return node.children.isEmpty()
            }

            val char = clean[index]
            val child = node.children[char] ?: return false

            val prune = removeRec(child, index + 1)

            if (prune) {
                node.children.remove(char)
            }

            return node.children.isEmpty() && !node.isEndOfWord
        }

        removeRec(root, 0)

        return existed
    }

    /**
     * Searches the Trie for dictionary words within [maxDistance] Levenshtein edits
     * of [query]. Performs depth-first traversal with dynamic programming row vectors,
     * pruning subtrees when the minimum row edit distance exceeds [maxDistance].
     */
    fun searchFuzzy(query: String, maxDistance: Int = 2): List<Pair<String, Int>> {
        val clean = query.trim().lowercase()
        if (clean.isEmpty()) return emptyList()

        val results = ArrayList<Pair<String, Int>>()
        val initialRow = IntArray(clean.length + 1) { it }

        fun dfs(node: TrieNode, parentRow: IntArray) {
            for ((char, child) in node.children) {
                val newRow = IntArray(clean.length + 1)
                newRow[0] = parentRow[0] + 1
                var minVal = newRow[0]

                for (j in 1..clean.length) {
                    val cost = if (clean[j - 1] == char) 0 else 1
                    newRow[j] = minOf(
                        newRow[j - 1] + 1,       // insert
                        parentRow[j] + 1,        // delete
                        parentRow[j - 1] + cost  // substitute
                    )
                    if (newRow[j] < minVal) minVal = newRow[j]
                }

                if (child.isEndOfWord && newRow[clean.length] <= maxDistance) {
                    child.word?.let { results.add(it to child.frequency) }
                }

                if (minVal <= maxDistance) {
                    dfs(child, newRow)
                }
            }
        }

        dfs(root, initialRow)
        return results.distinctBy { it.first }.sortedByDescending { it.second }
    }
}