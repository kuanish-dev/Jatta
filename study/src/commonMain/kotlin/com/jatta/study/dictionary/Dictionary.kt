package com.jatta.study.dictionary

import com.jatta.engine.Engine
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.study.content.KindFile
import com.jatta.study.content.StudyCourse

enum class ItemState { New, Learning, Held }

data class ItemStatus(val state: ItemState, val recallTomorrow: Double?, val dueInDays: Int?)

data class DictionaryEntry(
    val id: String,
    val kind: KindFile,
    val en: String,
    val ru: String,
    val also: List<String>,
    val recorded: Boolean,
    val topicId: String,
    val topicTitle: String,
    val status: ItemStatus,
)

object Dictionary {
    fun status(progress: ItemProgress?, engine: Engine, today: Int): ItemStatus {
        if (progress == null) return ItemStatus(ItemState.New, null, null)
        val state = if (engine.gate.holds(progress, today)) ItemState.Held else ItemState.Learning
        return ItemStatus(state, engine.memory.recallOn(progress, today + 1), progress.dueDay - today)
    }

    fun entries(content: StudyCourse, engine: Engine, learner: Learner, today: Int): List<DictionaryEntry> =
        content.course.topics.flatMap { topic -> content.course.itemsOf(topic.id).map { entry(content, engine, learner, today, it.id) } }

    fun entriesOf(topicId: String, content: StudyCourse, engine: Engine, learner: Learner, today: Int): List<DictionaryEntry> =
        content.course.itemsOf(topicId).map { entry(content, engine, learner, today, it.id) }

    fun entry(content: StudyCourse, engine: Engine, learner: Learner, today: Int, itemId: String): DictionaryEntry {
        val item = content.items.getValue(itemId)
        val topic = content.topicOf(itemId)
        return DictionaryEntry(
            item.id, item.kind, item.en, item.ru, item.also, item.audio != null, topic.id, topic.title,
            status(learner.progress[itemId], engine, today),
        )
    }

    fun search(entries: List<DictionaryEntry>, query: String): List<DictionaryEntry> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return entries
        fun closeness(entry: DictionaryEntry): Int? = when {
            entry.en.lowercase().startsWith(needle) -> 0
            entry.en.lowercase().contains(needle) || entry.also.any { it.lowercase().contains(needle) } -> 1
            entry.ru.lowercase().contains(needle) -> 2
            else -> null
        }
        return entries.mapNotNull { entry -> closeness(entry)?.let { it to entry } }.sortedBy { it.first }.map { it.second }
    }
}
