package com.jatta.engine.plan

import com.jatta.engine.EngineConfig
import com.jatta.engine.content.Course
import com.jatta.engine.content.Topic
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.engine.memory.MemoryModel

/** Opens a topic only when the topics it builds on hold in memory (М7). */
class TopicGate(
    private val course: Course,
    private val memory: MemoryModel,
    private val config: EngineConfig,
) {
    /**
     * An item holds when the learner would still recall it tomorrow. Judging by tomorrow and not today
     * keeps an item that was just failed from looking learned: right after any review recall is 100%.
     */
    fun holds(progress: ItemProgress?, today: Int): Boolean =
        progress != null && memory.recallOn(progress, today + 1) >= config.heldRecall

    fun isMastered(topicId: String, learner: Learner, today: Int): Boolean {
        val items = course.itemsOf(topicId)
        val held = items.count { holds(learner.progress[it.id], today) }
        return held >= config.topicMasteryShare * items.size
    }

    fun isOpen(topic: Topic, learner: Learner, today: Int): Boolean =
        topic.requires.all { isMastered(it, learner, today) }
}
