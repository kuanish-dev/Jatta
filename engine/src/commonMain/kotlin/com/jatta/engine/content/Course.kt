package com.jatta.engine.content

/** A grammar topic, a vocabulary set or a group of phrases: the unit of teaching order (М7). */
data class Topic(
    val id: String,
    val title: String,
    /** Topics that must hold in memory before this one opens (М7). */
    val requires: Set<String> = emptySet(),
    /** Topics learners mix up with this one; their reviews are interleaved (М9). */
    val confusableWith: Set<String> = emptySet(),
)

enum class ItemKind { Word, Phrase, Rule }

/** One thing to remember: a word, a phrase or a rule. Each item is scheduled on its own (М1). */
data class Item(
    val id: String,
    val topicId: String,
    val kind: ItemKind,
    /** Position in the language's frequency list, lower first (М8). Null keeps the declared order. */
    val frequencyRank: Int? = null,
)

/** The whole course with topics in teaching order. Fails fast on broken references. */
class Course(val topics: List<Topic>, val items: List<Item>) {
    private val topicById = topics.associateBy { it.id }
    private val itemById = items.associateBy { it.id }
    private val itemsByTopic = items
        .groupBy { it.topicId }
        .mapValues { (_, list) -> list.sortedWith(compareBy(nullsLast()) { it.frequencyRank }) }

    init {
        require(topicById.size == topics.size) { "Duplicate topic ids" }
        require(itemById.size == items.size) { "Duplicate item ids" }
        topics.forEachIndexed { index, topic ->
            topic.requires.forEach { required ->
                val requiredIndex = topics.indexOfFirst { it.id == required }
                require(requiredIndex in 0 until index) {
                    "Topic ${topic.id} requires $required, which must come earlier in the course"
                }
            }
            topic.confusableWith.forEach {
                require(it in topicById) { "Topic ${topic.id} is confusable with unknown topic $it" }
            }
        }
        items.forEach { require(it.topicId in topicById) { "Item ${it.id} belongs to unknown topic ${it.topicId}" } }
    }

    fun topic(id: String): Topic = topicById.getValue(id)

    fun item(id: String): Item = itemById.getValue(id)

    /** Items of a topic, most frequent first (М8). */
    fun itemsOf(topicId: String): List<Item> = itemsByTopic[topicId].orEmpty()
}
