package com.langengine.engine.session

import com.langengine.engine.EngineConfig
import com.langengine.engine.fsrs.Grade

/** How sure the learner was: an optional tap after answering (М13). */
enum class Confidence { Knew, Unsure, Guessed }

/** One answer to an exercise. */
data class Answer(val correct: Boolean, val responseMs: Long, val confidence: Confidence? = null)

/**
 * Turns an item's first answer of the day into a grade for the memory model (М2, М13):
 * wrong is Again; right but slow or with doubt is Hard; right, fast and without doubt is Easy;
 * anything else is Good. Speed is judged against the learner's own median for that exercise form.
 */
class Grading(private val config: EngineConfig) {
    fun grade(answer: Answer, medianResponseMs: Long): Grade = when {
        !answer.correct -> Grade.Again
        answer.confidence == Confidence.Unsure || answer.confidence == Confidence.Guessed -> Grade.Hard
        answer.responseMs > medianResponseMs * config.slowAnswerRatio -> Grade.Hard
        answer.responseMs < medianResponseMs * config.fastAnswerRatio -> Grade.Easy
        else -> Grade.Good
    }
}
