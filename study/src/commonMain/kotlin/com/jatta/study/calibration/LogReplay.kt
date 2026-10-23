package com.jatta.study.calibration

import com.jatta.engine.fsrs.Grade
import com.jatta.engine.fsrs.Review
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.memory.MemoryModel
import com.jatta.study.store.LogEntry

/** The answer log read back as memory: only graded answers, the first of each day, change memory. */
object LogReplay {

    /** Memory of every logged item; [onReview] sees each graded answer with the memory just before it. */
    fun progress(
        log: List<LogEntry>,
        memory: MemoryModel,
        onReview: (LogEntry, ItemProgress?) -> Unit = { _, _ -> },
    ): Map<String, ItemProgress> {
        val state = mutableMapOf<String, ItemProgress>()
        graded(log).forEach { entry ->
            val previous = state[entry.item]
            onReview(entry, previous)
            state[entry.item] = memory.review(previous, entry.item, Grade.valueOf(entry.grade!!), entry.day)
        }
        return state
    }

    /**
     * Review histories to fit the memory model on. Items placed as known by the placement test are left
     * out: their history starts from a guess, not from the first meeting the model describes.
     */
    fun histories(log: List<LogEntry>): List<List<Review>> = graded(log).groupBy { it.item }.values
        .filter { it.first().checkedBy != Calibration.PLACEMENT }
        .map { entries -> entries.map { Review(it.day, Grade.valueOf(it.grade!!)) } }

    private fun graded(log: List<LogEntry>) = log.filter { it.grade != null }.sortedBy { it.atMs }
}
