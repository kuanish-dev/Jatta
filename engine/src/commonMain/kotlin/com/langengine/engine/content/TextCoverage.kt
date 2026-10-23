package com.langengine.engine.content

import com.langengine.engine.EngineConfig

/**
 * Checks that a generated text is made of words the learner already holds (М11).
 * The code checks coverage itself instead of trusting the language model's word.
 */
class TextCoverage(private val config: EngineConfig) {

    data class Report(val share: Double, val unknownWords: Set<String>, val acceptable: Boolean)

    /** [knownWords] are word forms as they appear in text: "go", "went", "don't". */
    fun check(text: String, knownWords: Set<String>): Report {
        val words = WORD.findAll(text.lowercase()).map { it.value }.toList()
        if (words.isEmpty()) return Report(share = 1.0, unknownWords = emptySet(), acceptable = true)
        val known = knownWords.mapTo(HashSet()) { it.lowercase() }
        val unknownTokens = words.filter { it !in known }
        val share = 1 - unknownTokens.size.toDouble() / words.size
        val unknown = unknownTokens.toSet()
        val acceptable = share >= config.minTextCoverage && unknown.size <= config.maxNewWordsPerText
        return Report(share, unknown, acceptable)
    }

    private companion object {
        val WORD = Regex("[a-z]+(?:'[a-z]+)?")
    }
}
