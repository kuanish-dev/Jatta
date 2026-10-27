package com.jatta.engine.session

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Grade

data class Answer(val correct: Boolean, val responseMs: Long)

class Grading(private val config: EngineConfig) {
    fun grade(answer: Answer, medianResponseMs: Long): Grade = when {
        !answer.correct -> Grade.Again
        isSlow(answer, medianResponseMs) -> Grade.Hard
        isFast(answer, medianResponseMs) -> Grade.Easy
        else -> Grade.Good
    }

    fun isConfidentError(answer: Answer, medianResponseMs: Long): Boolean =
        !answer.correct && isFast(answer, medianResponseMs)

    private fun isFast(answer: Answer, medianResponseMs: Long) = answer.responseMs < medianResponseMs * config.fastAnswerRatio

    private fun isSlow(answer: Answer, medianResponseMs: Long) = answer.responseMs > medianResponseMs * config.slowAnswerRatio
}
