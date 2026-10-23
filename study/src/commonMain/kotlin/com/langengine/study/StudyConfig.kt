package com.langengine.study

import com.langengine.engine.session.Rung

/** Numbers of the study flow around the engine. Engine mechanics live in EngineConfig. */
data class StudyConfig(
    /** Recent first answers kept for the learner's own medians and baseline. */
    val recentAnswersKept: Int = 300,
    /** Answers needed before the learner's own accuracy and speed replace the defaults (М16). */
    val minAnswersForBaseline: Int = 30,
    /** Answers on a rung needed before its own median speed replaces the default (М2, М13). */
    val minAnswersForRungMedian: Int = 10,
    /** Usual answer time per rung until there is history. Weak: replaced by the learner's medians. */
    val defaultMedianMs: Map<Rung, Long> = mapOf(
        Rung.Recognize to 5_000,
        Rung.FillGap to 8_000,
        Rung.Translate to 12_000,
        Rung.Compose to 30_000,
        Rung.Speak to 10_000,
    ),
    /** First answers in a session needed before the ladder may shift (М6). */
    val minAnswersForLadderShift: Int = 15,
)
