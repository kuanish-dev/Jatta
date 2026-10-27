package com.jatta.study.content

import com.jatta.engine.content.ItemKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CourseFile(
    val language: String,
    val title: String,
    val topics: List<String>,
)

@Serializable
data class TopicFile(
    val id: String,
    val title: String,
    val intro: String = "",
    val requires: List<String> = emptyList(),
    val confusableWith: List<String> = emptyList(),
    val items: List<ItemFile>,
)

@Serializable
enum class KindFile(val engineKind: ItemKind) {
    @SerialName("word")
    Word(ItemKind.Word),

    @SerialName("phrase")
    Phrase(ItemKind.Phrase),

    @SerialName("rule")
    Rule(ItemKind.Rule),
}

@Serializable
data class ItemFile(
    val id: String,
    val kind: KindFile,
    val en: String,
    val ru: String,
    val also: List<String> = emptyList(),
    val rank: Int? = null,
    val note: String = "",
    val examples: List<ExampleFile> = emptyList(),
    val audio: AudioFile? = null,
)

@Serializable
data class AudioFile(
    val file: String,
    val by: String,
    val license: String,
    val source: String,
)

@Serializable
data class ExampleFile(
    val en: String,
    val ru: String,
    val gap: String? = null,
    val cue: String? = null,
    val wrong: List<String> = emptyList(),
    val also: List<String> = emptyList(),
)
