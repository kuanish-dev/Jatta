package com.jatta.study.translate

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * An English sentence with its Russian translations from Tatoeba (М25), collected by
 * scripts/collect_sentences.py. [en] starts with the sentence, then the other English translations of
 * ru[0]; [ru] holds its Russian translations. [items] are the course words it uses.
 */
@Serializable
data class SentencePair(val id: Int, val en: List<String>, val ru: List<String>, val items: List<String>)

/** content/en/sentences.json; [url] plus a sentence id is the page naming its authors. */
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
