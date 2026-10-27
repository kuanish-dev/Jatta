package com.jatta.engine.plan

import com.jatta.engine.EngineConfig
import com.jatta.engine.content.Course
import com.jatta.engine.content.Topic
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.engine.memory.MemoryModel

class TopicGate(
    private val course: Course,
    private val memory: MemoryModel,
    private val config: EngineConfig,
) {
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
