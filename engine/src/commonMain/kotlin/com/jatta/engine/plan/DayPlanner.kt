package com.jatta.engine.plan

import com.jatta.engine.EngineConfig
import com.jatta.engine.content.Course
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.engine.memory.MemoryModel

/** What to study today. */
data class DayPlan(
    /** Due reviews, most forgotten first, with confusable topics interleaved. */
    val reviews: List<String>,
    /** New items in teaching order. */
    val newItems: List<String>,
    /** Due reviews moved to the following days because today is over the limit (М18). */
    val postponedReviews: Int,
)

/** Builds the day: reviews first, then new items if the learner is ready for them (М1, М7, М8, М9, М18). */
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

    /** М18: the limit is per day, so items already started today in an earlier session count against it. */
    private fun newItems(learner: Learner, today: Int): List<String> {
        val startedToday = learner.progress.values.count { it.firstDay == today }
        return course.topics.asSequence()
            .filter { gate.isOpen(it, learner, today) }
            .flatMap { course.itemsOf(it.id) }
            .filter { it.id !in learner.progress }
            .take((config.newItemsPerDay - startedToday).coerceAtLeast(0))
            .map { it.id }
            .toList()
    }

    /**
     * Reviews of topics that learners confuse are mixed one by one, so each answer needs telling
     * them apart (М9). Groups of unrelated topics keep their urgency order.
     */
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

    /** Topic id → id of the first topic in its group of mutually confusable topics. */
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
