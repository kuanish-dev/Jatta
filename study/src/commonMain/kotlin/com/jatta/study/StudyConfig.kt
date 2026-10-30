package com.jatta.study

import com.jatta.engine.session.Rung

data class StudyConfig(
    val recentAnswersKept: Int = 300,
    val minAnswersForBaseline: Int = 30,
    val minAnswersForRungMedian: Int = 10,
    val defaultMedianMs: Map<Rung, Long> = mapOf(
        Rung.Recognize to 5_000,
        Rung.FillGap to 8_000,
        Rung.Translate to 12_000,
        Rung.Compose to 30_000,
        Rung.Speak to 10_000,
    ),
    val listeningExtraMs: Long = 4_000,
    val minAnswersForLadderShift: Int = 15,
    val judgeMaxEditShare: Double = 0.4,
    val judgeMinEditsAllowed: Int = 3,
    val judgeTimeoutMs: Long = 20_000,
    val minJudgeAgreement: Double = 0.9,
    val defaultStudyMinute: Int = 19 * 60,
    val sessionStartsKept: Int = 14,
    val reminderLookaheadDays: Int = 60,
    val placementKnownBandShare: Double = 0.85,
    val placementMaxFalseAlarms: Double = 0.25,
    val placementQuestionsPerTopic: Int = 2,
    val minReviewsForCalibration: Int = 30,
    val fitHoldoutShare: Double = 0.2,
    val refitAfterReviews: Int = 512,
    val translationsPerSession: Int = 4,
    val translationPassScore: Double = 0.8,
    val translationRetryDays: Int = 3,
    val chatTimeoutMs: Long = 45_000,
    val chatTurnsKept: Int = 8,
)
