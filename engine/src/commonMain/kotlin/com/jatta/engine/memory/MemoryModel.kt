package com.jatta.engine.memory

import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.learner.ItemProgress

/** Applies graded answers to items and predicts recall (М1, М2). */
class MemoryModel(private val fsrs: Fsrs) {

    /** Progress after a graded answer on [today]; [previous] is null for an item met for the first time. */
    fun review(previous: ItemProgress?, itemId: String, grade: Grade, today: Int): ItemProgress {
        val memory = if (previous == null) {
            fsrs.first(grade)
        } else {
            fsrs.next(previous.memory, today - previous.lastReviewDay, grade)
        }
        return ItemProgress(itemId, memory, lastReviewDay = today, dueDay = today + fsrs.intervalDays(memory.stability))
    }

    /** Probability that the learner recalls the item on [day]. */
    fun recallOn(progress: ItemProgress, day: Int): Double =
        fsrs.retrievability(progress.memory, day - progress.lastReviewDay)
}
