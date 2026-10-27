package com.jatta.study.exercise

import com.jatta.engine.session.Rung

sealed interface Exercise {
    val itemId: String
    val rung: Rung

    val instruction: String

    val hint: String

    val answer: String

    val listen: String

    val recording: String?

    val byEar: Boolean

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
        override val listen: String,
        override val recording: String? = null,
        override val byEar: Boolean = false,
    ) : Exercise

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
        override val listen: String,
        val judgeTask: String? = null,
        override val recording: String? = null,
        override val byEar: Boolean = false,
    ) : Exercise

    data class Free(
        override val itemId: String,
        override val rung: Rung,
        override val instruction: String,
        val task: String,
        val references: List<String>,
        override val hint: String,
        override val answer: String,
        override val listen: String,
        val judgeTask: String,
        override val recording: String? = null,
        override val byEar: Boolean = false,
    ) : Exercise

    data class Speak(
        override val itemId: String,
        override val rung: Rung,
        override val instruction: String,
        val prompt: String,
        override val hint: String,
        override val answer: String,
        override val listen: String,
        override val recording: String? = null,
        override val byEar: Boolean = false,
    ) : Exercise
}
