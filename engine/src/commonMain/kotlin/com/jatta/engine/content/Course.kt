package com.jatta.engine.content

data class Topic(
    val id: String,
    val title: String,
    val requires: Set<String> = emptySet(),
    val confusableWith: Set<String> = emptySet(),
)

enum class ItemKind { Word, Phrase, Rule }

data class Item(
    val id: String,
    val topicId: String,
    val kind: ItemKind,
    val frequencyRank: Int? = null,
)

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

    fun itemsOf(topicId: String): List<Item> = itemsByTopic[topicId].orEmpty()
}
