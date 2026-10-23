package com.jatta.study.exercise

/** Compares typed answers the forgiving way: case, punctuation, curly quotes and contractions don't matter. */
object AnswerCheck {

    fun matches(response: String, accepted: List<String>): Boolean {
        val normalized = normalize(response)
        return accepted.any { normalize(it) == normalized }
    }

    fun normalize(text: String): String {
        var s = text.lowercase().replace('’', '\'').replace('‘', '\'')
        CONTRACTIONS.forEach { (short, long) -> s = s.replace(short, long) }
        return s.replace(NOT_TEXT, " ").replace(SPACES, " ").trim()
    }

    /** Order matters: the special negatives go before the general "n't". */
    private val CONTRACTIONS = listOf(
        "can't" to "can not",
        "cannot" to "can not",
        "won't" to "will not",
        "n't" to " not",
        "'m" to " am",
        "'re" to " are",
        "'ve" to " have",
        "'ll" to " will",
        "'d" to " would",
    )
    private val NOT_TEXT = Regex("[^a-z0-9' ]")
    private val SPACES = Regex("\\s+")
}
