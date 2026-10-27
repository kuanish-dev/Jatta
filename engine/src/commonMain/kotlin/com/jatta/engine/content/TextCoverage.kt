package com.jatta.engine.content

import com.jatta.engine.EngineConfig

class TextCoverage(private val config: EngineConfig) {

    data class Report(val share: Double, val unknownWords: Set<String>, val acceptable: Boolean)

    fun check(text: String, knownWords: Set<String>): Report {
        val words = words(text)
        if (words.isEmpty()) return Report(share = 1.0, unknownWords = emptySet(), acceptable = true)
        val known = knownWords.mapTo(HashSet()) { it.lowercase() }
        val unknownTokens = words.filter { it !in known }
        val share = 1 - unknownTokens.size.toDouble() / words.size
        val unknown = unknownTokens.toSet()
        val acceptable = share >= config.minTextCoverage && unknown.size <= config.maxNewWordsPerText
        return Report(share, unknown, acceptable)
    }

    fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value }.toList()

    private companion object {
        val WORD = Regex("[a-z]+(?:'[a-z]+)?")
    }
}
