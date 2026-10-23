package com.jatta.engine.learner

import com.jatta.engine.fsrs.Memory

/** Memory state of one item the learner has already met. Items never shown have no progress. */
data class ItemProgress(
    val itemId: String,
    val memory: Memory,
    val lastReviewDay: Int,
    val dueDay: Int,
)

/** Everything the engine knows about the learner. */
data class Learner(
    val progress: Map<String, ItemProgress> = emptyMap(),
    /** М6: shift of the exercise ladder, learned from recent accuracy. */
    val ladderShift: Int = 0,
)
