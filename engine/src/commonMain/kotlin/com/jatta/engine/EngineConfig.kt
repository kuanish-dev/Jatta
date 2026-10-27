package com.jatta.engine

import com.jatta.engine.fsrs.Fsrs

data class EngineConfig(
    val desiredRetention: Double = 0.90,
    val maximumIntervalDays: Int = 36_500,
    val fsrsParameters: List<Double> = Fsrs.DEFAULT_PARAMETERS,
    val optimizerEpochs: Int = 5,
    val optimizerBatchReviews: Int = 512,
    val optimizerLearningRate: Double = 0.04,
    val optimizerMaxReviewsPerItem: Int = 64,
    val heldRecall: Double = 0.85,
    val topicMasteryShare: Double = 0.85,
    val newItemsPerDay: Int = 12,
    val dailyReviewNorm: Int = 100,
    val backlogStopFactor: Double = 1.5,
    val newItemRecallGap: Int = 3,
    val rungStabilityDays: List<Double> = listOf(3.0, 10.0, 30.0, 90.0),
    val ladderUpAccuracy: Double = 0.95,
    val ladderDownAccuracy: Double = 0.75,
    val maxLadderShift: Int = 2,
    val fastAnswerRatio: Double = 0.6,
    val slowAnswerRatio: Double = 1.8,
    val blockMinutes: Int = 25,
    val breakMinutes: Int = 5,
    val quietRestMinutes: Int = 7,
    val fatigueWindow: Int = 10,
    val fatigueAccuracyDrop: Double = 0.15,
    val fatigueSlowdown: Double = 0.30,
    val minTextCoverage: Double = 0.95,
    val maxNewWordsPerText: Int = 3,
    val dayStartHour: Int = 4,
)
