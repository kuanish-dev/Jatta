package com.jatta.study.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class TextSource(
    val name: String,
    val work: String,
    val author: String,
    val url: String,
    val license: String,
    val changes: String,
)

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
