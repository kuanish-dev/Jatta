package com.jatta.engine.learner

import com.jatta.engine.fsrs.Memory

data class ItemProgress(
    val itemId: String,
    val memory: Memory,
    val lastReviewDay: Int,
    val dueDay: Int,
    val firstDay: Int? = null,
)

data class Learner(
    val progress: Map<String, ItemProgress> = emptyMap(),
    val ladderShift: Int = 0,
)
