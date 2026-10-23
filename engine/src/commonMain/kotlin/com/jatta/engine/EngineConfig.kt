package com.jatta.engine

/**
 * Every tunable number of the engine in one place.
 *
 * Each comment names the mechanic (М1–М22 in docs/mechanics.html) and how well it is backed:
 * strong, medium or weak. Weak values are starting points to calibrate on the learner's own logs.
 */
data class EngineConfig(
    /** М2: recall probability at which a review falls due. Weak (FSRS default). */
    val desiredRetention: Double = 0.90,
    /** М2: upper bound for any interval. */
    val maximumIntervalDays: Int = 36_500,
    /** М7: an item "holds" when its recall probability tomorrow is at least this. Weak. */
    val heldRecall: Double = 0.85,
    /** М7: share of a topic's items that must hold before topics built on it open. Weak. */
    val topicMasteryShare: Double = 0.85,
    /** М18: new items introduced per day. Weak. */
    val newItemsPerDay: Int = 12,
    /** М18: number of reviews an ordinary day is sized for. Weak. */
    val dailyReviewNorm: Int = 100,
    /** М18: more due reviews than norm × this stops new items; the excess waits for later days. Weak. */
    val backlogStopFactor: Double = 1.5,
    /** М4: queue steps between presenting a new item and the first recall of it. */
    val newItemRecallGap: Int = 3,
    /** М5: memory stability, in days, needed to climb from each rung to the next. Medium. */
    val rungStabilityDays: List<Double> = listOf(3.0, 10.0, 30.0, 90.0),
    /** М6: accuracy above which exercises get one step harder. Weak. */
    val ladderUpAccuracy: Double = 0.95,
    /** М6: accuracy below which exercises get one step easier. Weak. */
    val ladderDownAccuracy: Double = 0.75,
    /** М6: how far the ladder may shift from the stability-based rung. */
    val maxLadderShift: Int = 2,
    /** М2, М13: a right answer faster than the learner's median × this is graded Easy. Weak. */
    val fastAnswerRatio: Double = 0.6,
    /** М2, М13: a right answer slower than the learner's median × this is graded Hard. Weak. */
    val slowAnswerRatio: Double = 1.8,
    /** М14: minutes of study before a scheduled break. Weak. */
    val blockMinutes: Int = 25,
    /** М14, М16: length of a break. Weak. */
    val breakMinutes: Int = 5,
    /** М15: quiet rest after a session that brought new items. Weak. */
    val quietRestMinutes: Int = 7,
    /** М16: number of recent answers the fatigue monitor looks at. Weak. */
    val fatigueWindow: Int = 10,
    /** М16: drop in accuracy below the learner's baseline that signals fatigue. Weak. */
    val fatigueAccuracyDrop: Double = 0.15,
    /** М16: growth of the median answer time over the baseline that signals fatigue. Weak. */
    val fatigueSlowdown: Double = 0.30,
    /** М11: share of known words a generated text must have. Strong. */
    val minTextCoverage: Double = 0.95,
    /** М11: new words a generated text may bring. */
    val maxNewWordsPerText: Int = 3,
    /** Local hour when a new learning day starts, so studying after midnight counts for the evening before. */
    val dayStartHour: Int = 4,
)
