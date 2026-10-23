package com.jatta.study.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Where a text comes from; shown with the text, as its license requires. */
@Serializable
data class TextSource(
    val name: String,
    val work: String,
    val author: String,
    val url: String,
    val license: String,
    val changes: String,
)

/**
 * A passage from a real book or article (М11), collected by scripts/collect_texts.py.
 * [rare] lists its words outside the vocabulary assumed for B1; a rare word counts as known once
 * the learner holds it in the course.
 */
@Serializable
data class ReadingText(
    val id: String,
    val title: String,
    val text: String,
    val rare: List<String>,
    val source: TextSource,
)

@Serializable
private data class TextsFile(val assumedKnown: String, val texts: List<ReadingText>)

object ReadingTexts {
    fun read(text: String): List<ReadingText> = Json.decodeFromString<TextsFile>(text).texts
}
