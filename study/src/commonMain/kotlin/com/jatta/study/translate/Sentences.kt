package com.jatta.study.translate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SentencePair(val id: Int, val en: List<String>, val ru: List<String>, val items: List<String>)

@Serializable
data class SentencesFile(
    val source: String,
    val license: String,
    val url: String,
    val assumedKnown: String,
    val sentences: List<SentencePair>,
) {
    companion object {
        fun read(text: String): SentencesFile = Json.decodeFromString(text)
    }
}
