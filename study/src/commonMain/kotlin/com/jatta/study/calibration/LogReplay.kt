package com.jatta.study.calibration

import com.jatta.engine.fsrs.Grade
import com.jatta.engine.fsrs.Review
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.memory.MemoryModel
import com.jatta.study.store.LogEntry

object LogReplay {

    fun progress(
        log: List<LogEntry>,
        memory: MemoryModel,
        onReview: (LogEntry, ItemProgress?) -> Unit = { _, _ -> },
    ): Map<String, ItemProgress> {
        val state = mutableMapOf<String, ItemProgress>()
        graded(log).forEach { entry ->
            val previous = state[entry.item]
            onReview(entry, previous)
            val next = memory.review(previous, entry.item, Grade.valueOf(entry.grade!!), entry.day)
            state[entry.item] = if (previous == null && entry.checkedBy == Calibration.PLACEMENT) next.copy(firstDay = null) else next
        }
        return state
    }

    fun firstStudyDays(log: List<LogEntry>): Map<String, Int> = graded(log).groupBy { it.item }
        .filterValues { it.first().checkedBy != Calibration.PLACEMENT }
        .mapValues { (_, entries) -> entries.first().day }

    fun histories(log: List<LogEntry>): List<List<Review>> = graded(log).groupBy { it.item }.values
        .filter { it.first().checkedBy != Calibration.PLACEMENT }
        .map { entries -> entries.map { Review(it.day, Grade.valueOf(it.grade!!)) } }

    private fun graded(log: List<LogEntry>) = log.filter { it.grade != null }.sortedBy { it.atMs }
}
