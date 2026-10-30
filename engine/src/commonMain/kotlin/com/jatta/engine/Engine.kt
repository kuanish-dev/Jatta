package com.jatta.engine

import com.jatta.engine.content.Course
import com.jatta.engine.content.TextCoverage
import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.learner.Learner
import com.jatta.engine.memory.MemoryModel
import com.jatta.engine.plan.DayPlan
import com.jatta.engine.plan.DayPlanner
import com.jatta.engine.plan.TopicGate
import com.jatta.engine.session.Baseline
import com.jatta.engine.session.Grading
import com.jatta.engine.session.Ladder
import com.jatta.engine.session.RestCoach
import com.jatta.engine.session.Session

class Engine(val course: Course, val config: EngineConfig = EngineConfig()) {
    val memory = MemoryModel(Fsrs(config.desiredRetention, config.maximumIntervalDays, config.fsrsParameters))
    val gate = TopicGate(course, memory, config)
    val ladder = Ladder(config)
    val grading = Grading(config)
    val coverage = TextCoverage(config)
    private val planner = DayPlanner(course, memory, gate, config)

    fun planDay(learner: Learner, today: Int): DayPlan = planner.plan(learner, today)

    fun planTopic(learner: Learner, topicId: String, today: Int): DayPlan = planner.topic(topicId, learner, today)

    fun planItems(learner: Learner, itemIds: List<String>, today: Int): DayPlan = planner.items(itemIds, learner, today)

    fun startSession(plan: DayPlan): Session = Session(plan.reviews, plan.newItems, config)

    fun restCoach(baseline: Baseline, startMs: Long): RestCoach = RestCoach(config, baseline, startMs)

    fun recordGrade(learner: Learner, itemId: String, grade: Grade, today: Int): Learner {
        val progress = memory.review(learner.progress[itemId], itemId, grade, today)
        return learner.copy(progress = learner.progress + (itemId to progress))
    }

    fun placeKnown(learner: Learner, itemId: String, today: Int): Learner {
        val progress = memory.review(null, itemId, Grade.Good, today).copy(firstDay = null)
        return learner.copy(progress = learner.progress + (itemId to progress))
    }
}
