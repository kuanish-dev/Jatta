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
import com.jatta.engine.session.Ladder
import com.jatta.engine.session.RestCoach
import com.jatta.engine.session.Session

/**
 * The engine for one course. Pure: no clock, storage or network. The app passes in the learning day
 * and the time of each answer, and stores what comes back.
 */
class Engine(val course: Course, val config: EngineConfig = EngineConfig()) {
    val memory = MemoryModel(Fsrs(config.desiredRetention, config.maximumIntervalDays))
    val gate = TopicGate(course, memory, config)
    val ladder = Ladder(config)
    val coverage = TextCoverage(config)
    private val planner = DayPlanner(course, memory, gate, config)

    fun planDay(learner: Learner, today: Int): DayPlan = planner.plan(learner, today)

    fun startSession(plan: DayPlan): Session = Session(plan.reviews, plan.newItems, config)

    fun restCoach(baseline: Baseline, startMs: Long): RestCoach = RestCoach(config, baseline, startMs)

    /** Stores an item's first graded answer of the day in the learner's memory state. */
    fun recordGrade(learner: Learner, itemId: String, grade: Grade, today: Int): Learner {
        val progress = memory.review(learner.progress[itemId], itemId, grade, today)
        return learner.copy(progress = learner.progress + (itemId to progress))
    }
}
