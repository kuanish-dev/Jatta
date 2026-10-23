package com.jatta.engine.content

import com.jatta.engine.EngineConfig

/**
 * Checks that a reading text is made of words the learner already knows (М11).
 * The code checks coverage itself instead of trusting anyone's word for it.
 */
class TextCoverage(private val config: EngineConfig) {

    data class Report(val share: Double, val unknownWords: Set<String>, val acceptable: Boolean)

    /** [knownWords] are word forms as they appear in text: "go", "went", "don't". */
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

    /** The text's words, lower-cased, in the form coverage is counted in. */
    fun words(text: String): List<String> = WORD.findAll(text.lowercase()).map { it.value }.toList()

    private companion object {
        val WORD = Regex("[a-z]+(?:'[a-z]+)?")
    }
}
