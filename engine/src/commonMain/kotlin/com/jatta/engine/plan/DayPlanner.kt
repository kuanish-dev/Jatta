package com.jatta.engine.plan

import com.jatta.engine.EngineConfig
import com.jatta.engine.content.Course
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.engine.memory.MemoryModel

data class DayPlan(
    val reviews: List<String>,
    val newItems: List<String>,
    val postponedReviews: Int,
)

class DayPlanner(
    private val course: Course,
    private val memory: MemoryModel,
    private val gate: TopicGate,
    private val config: EngineConfig,
) {
    fun plan(learner: Learner, today: Int): DayPlan {
        val due = learner.progress.values
            .filter { it.dueDay <= today }
            .sortedWith(compareBy<ItemProgress> { memory.recallOn(it, today) }.thenBy { it.itemId })
        val limit = (config.dailyReviewNorm * config.backlogStopFactor).toInt()
        val reviews = due.take(limit)
        val newItems = if (due.size > limit) emptyList() else newItems(learner, today)
        return DayPlan(interleave(reviews), newItems, postponedReviews = due.size - reviews.size)
    }

    fun topic(topicId: String, learner: Learner, today: Int): DayPlan = items(course.itemsOf(topicId).map { it.id }, learner, today)

    fun items(itemIds: List<String>, learner: Learner, today: Int): DayPlan {
        val items = itemIds.map { course.item(it) }
        val reviews = items.mapNotNull { learner.progress[it.id] }
            .sortedWith(compareBy<ItemProgress> { memory.recallOn(it, today) }.thenBy { it.itemId })
            .map { it.itemId }
        val newItems = items.filter { it.id !in learner.progress }.take(newAllowance(learner, today)).map { it.id }
        return DayPlan(reviews, newItems, postponedReviews = 0)
    }

    private fun newItems(learner: Learner, today: Int): List<String> = course.topics.asSequence()
        .filter { gate.isOpen(it, learner, today) }
        .flatMap { course.itemsOf(it.id) }
        .filter { it.id !in learner.progress }
        .take(newAllowance(learner, today))
        .map { it.id }
        .toList()

    fun newAllowance(learner: Learner, today: Int): Int =
        (config.newItemsPerDay - learner.progress.values.count { it.firstDay == today }).coerceAtLeast(0)

    private fun interleave(reviews: List<ItemProgress>): List<String> {
        val clusters = confusionClusters()
        return reviews
            .groupBy { clusters.getValue(course.item(it.itemId).topicId) }
            .values
            .flatMap { group -> roundRobin(group.groupBy { course.item(it.itemId).topicId }.values) }
            .map { it.itemId }
    }

    private fun <T> roundRobin(lists: Collection<List<T>>): List<T> {
        val longest = lists.maxOfOrNull { it.size } ?: 0
        return (0 until longest).flatMap { index -> lists.mapNotNull { it.getOrNull(index) } }
    }

    private fun confusionClusters(): Map<String, String> {
        val root = course.topics.associate { it.id to it.id }.toMutableMap()
        fun find(id: String): String = if (root.getValue(id) == id) id else find(root.getValue(id))
        val order = course.topics.withIndex().associate { (index, topic) -> topic.id to index }
        course.topics.forEach { topic ->
            topic.confusableWith.forEach { other ->
                val (a, b) = find(topic.id) to find(other)
                if (a != b) {
                    if (order.getValue(a) < order.getValue(b)) root[b] = a else root[a] = b
                }
            }
        }
        return course.topics.associate { it.id to find(it.id) }
    }
}
