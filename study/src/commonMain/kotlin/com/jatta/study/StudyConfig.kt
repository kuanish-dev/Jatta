package com.jatta.study

import com.jatta.engine.session.Rung

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
    /** М22: a correction may change at most this share of the learner's words, otherwise it is a rewrite. */
    val judgeMaxEditShare: Double = 0.4,
    /** М22: but at least this many words, so short sentences can still be fixed. */
    val judgeMinEditsAllowed: Int = 3,
    /** How long to wait for the on-device model before falling back to self-check. */
    val judgeTimeoutMs: Long = 20_000,
    /** М21: share of known verdicts the model must match before it checks answers in lessons. Weak. */
    val minJudgeAgreement: Double = 0.9,
    /** М19, М20: reminder time until the learner has a usual one, minutes after midnight. Weak. */
    val defaultStudyMinute: Int = 19 * 60,
    /** М19, М20: past session starts that set the usual study time. */
    val sessionStartsKept: Int = 14,
    /** How far ahead to look for the next day with something to study. */
    val reminderLookaheadDays: Int = 60,
    /** М23: a frequency band counts as known from this corrected share of known words. Weak. */
    val placementKnownBandShare: Double = 0.85,
    /** М23: above this share of "known" pseudowords, single yes answers are not trusted. Weak. */
    val placementMaxFalseAlarms: Double = 0.25,
    /** М23: choice questions per grammar topic; all right places the topic as known. */
    val placementQuestionsPerTopic: Int = 2,
    /** М2: reviews needed before judging how well the memory model predicts the learner. Weak. */
    val minReviewsForCalibration: Int = 30,
)
