package com.jatta.engine.memory

import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.learner.ItemProgress

class MemoryModel(private val fsrs: Fsrs) {

    fun review(previous: ItemProgress?, itemId: String, grade: Grade, today: Int): ItemProgress {
        val memory = if (previous == null) {
            fsrs.first(grade)
        } else {
            fsrs.next(previous.memory, today - previous.lastReviewDay, grade)
        }
        return ItemProgress(
            itemId, memory, lastReviewDay = today, dueDay = today + fsrs.intervalDays(memory.stability),
            firstDay = if (previous == null) today else previous.firstDay,
        )
    }

    fun recallOn(progress: ItemProgress, day: Int): Double =
        fsrs.retrievability(progress.memory, day - progress.lastReviewDay)
}
