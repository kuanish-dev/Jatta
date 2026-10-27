package com.jatta.study.chat

import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.DiffPart
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ChatTopic(val id: String, val ru: String, val en: String, val words: String? = null)

@Serializable
data class ChatTopicsFile(val topics: List<ChatTopic>) {
    companion object {
        fun read(text: String): List<ChatTopic> = Json.decodeFromString<ChatTopicsFile>(text).topics
    }
}

data class ChatMessage(val fromLearner: Boolean, val text: String, val review: Review? = null)

sealed interface Review {
    data object Checking : Review

    data object Fine : Review

    data class Fixed(val diff: List<DiffPart>) : Review {
        val changes: List<Change> get() = changesOf(diff)

        val corrected: String get() = diff.filter { it.kind != DiffKind.Removed }
            .joinToString(" ") { if (it.kind == DiffKind.Same) it.written else it.text }
    }

    data object Unclear : Review
}

data class Change(val wrong: String?, val right: String?)

fun changesOf(diff: List<DiffPart>): List<Change> {
    val changes = mutableListOf<Change>()
    val wrong = mutableListOf<String>()
    val right = mutableListOf<String>()
    fun flush() {
        if (wrong.isNotEmpty() || right.isNotEmpty()) {
            changes += Change(wrong.joinToString(" ").ifEmpty { null }, right.joinToString(" ").ifEmpty { null })
        }
        wrong.clear()
        right.clear()
    }
    diff.forEach { part ->
        when (part.kind) {
            DiffKind.Same -> flush()
            DiffKind.Removed -> wrong += part.text
            DiffKind.Added -> right += part.text
        }
    }
    flush()
    return changes
}
