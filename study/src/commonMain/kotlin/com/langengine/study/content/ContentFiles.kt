package com.langengine.study.content

import com.langengine.engine.content.ItemKind
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** content/<language>/course.json: the course title and its topics in teaching order. */
@Serializable
data class CourseFile(
    val language: String,
    val title: String,
    val topics: List<String>,
)

/** content/<language>/topics/<id>.json. */
@Serializable
data class TopicFile(
    val id: String,
    val title: String,
    /** Short explicit rule or overview shown before the first item of the topic (М10). */
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

/** A word, phrase or rule with the examples its exercises are built from. */
@Serializable
data class ItemFile(
    val id: String,
    val kind: KindFile,
    /** The word or phrase; for a rule, its English pattern. */
    val en: String,
    /** Translation; for a rule, the rule in one short line. */
    val ru: String,
    /** Other accepted English answers when translating [ru]. */
    val also: List<String> = emptyList(),
    /** Position in a frequency list, lower first (М8). Leave out until taken from a real list. */
    val rank: Int? = null,
    /** One or two lines shown when the item is presented and after a wrong answer (М10, М12). */
    val note: String = "",
    val examples: List<ExampleFile> = emptyList(),
)

@Serializable
data class ExampleFile(
    val en: String,
    val ru: String,
    /** Whole word(s) of [en] that exercises blank out. */
    val gap: String? = null,
    /** Shown next to the blank, e.g. the base form "work" for "worked". */
    val cue: String? = null,
    /** Wrong options for the choice exercise. */
    val wrong: List<String> = emptyList(),
    /** Other accepted English translations of [ru]. */
    val also: List<String> = emptyList(),
)
