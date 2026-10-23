package com.langengine.study.exercise

import com.langengine.engine.session.Rung

/** A concrete exercise for one item on one rung of the ladder (М5). */
sealed interface Exercise {
    val itemId: String
    val rung: Rung

    /** What to do, in Russian: "Что значит это слово?". */
    val instruction: String

    /** Shown on the second try after a mistake: a hint, not the answer (М12). */
    val hint: String

    /** The right answer, shown on reveal and for self-checking. */
    val answer: String

    /** Pick one option; checked by code. */
    data class Choice(
        override val itemId: String,
        override val rung: Rung,
        override val instruction: String,
        val question: String,
        val translation: String?,
        val options: List<String>,
        val correctIndex: Int,
        override val hint: String,
        override val answer: String,
    ) : Exercise

    /**
     * Type the answer; checked by code against [accepted]. When [openEnded] is true a mismatch may still
     * be right (a free translation), so the learner compares with the model answer instead.
     */
    data class Typed(
        override val itemId: String,
        override val rung: Rung,
        override val instruction: String,
        val question: String,
        val translation: String?,
        val cue: String?,
        val accepted: List<String>,
        val openEnded: Boolean,
        override val hint: String,
        override val answer: String,
    ) : Exercise

    /** Write an own sentence; judged by the on-device model when there is one, otherwise by the learner. */
    data class Free(
        override val itemId: String,
        override val rung: Rung,
        override val instruction: String,
        val task: String,
        val references: List<String>,
        override val hint: String,
        override val answer: String,
    ) : Exercise

    /** Say the sentence aloud, then compare with the model answer. */
    data class Speak(
        override val itemId: String,
        override val rung: Rung,
        override val instruction: String,
        val prompt: String,
        override val hint: String,
        override val answer: String,
    ) : Exercise
}
