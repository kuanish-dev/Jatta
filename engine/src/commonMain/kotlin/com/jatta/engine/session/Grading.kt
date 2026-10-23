package com.jatta.engine.session

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Grade

/** One answer to an exercise. */
data class Answer(val correct: Boolean, val responseMs: Long)

/**
 * Turns an item's first answer of the day into a grade for the memory model (М2, М13).
 * Confidence is read from speed instead of asking: an answer that comes easily is one the learner
 * is sure of. Wrong is Again; right but slow is Hard; right and fast is Easy; anything else is Good.
 * Speed is judged against the learner's own median for that exercise form.
 */
class Grading(private val config: EngineConfig) {
    fun grade(answer: Answer, medianResponseMs: Long): Grade = when {
        !answer.correct -> Grade.Again
        isSlow(answer, medianResponseMs) -> Grade.Hard
        isFast(answer, medianResponseMs) -> Grade.Easy
        else -> Grade.Good
    }

    /** A fast wrong answer: a mistake made with confidence, which is corrected best when pointed out (М13). */
    fun isConfidentError(answer: Answer, medianResponseMs: Long): Boolean =
        !answer.correct && isFast(answer, medianResponseMs)

    private fun isFast(answer: Answer, medianResponseMs: Long) = answer.responseMs < medianResponseMs * config.fastAnswerRatio

    private fun isSlow(answer: Answer, medianResponseMs: Long) = answer.responseMs > medianResponseMs * config.slowAnswerRatio
}
