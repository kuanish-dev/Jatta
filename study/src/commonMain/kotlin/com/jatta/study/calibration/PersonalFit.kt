package com.jatta.study.calibration

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.FsrsOptimizer
import com.jatta.study.StudyConfig
import com.jatta.study.store.FitCheck
import com.jatta.study.store.LogEntry
import kotlin.math.ceil

data class FitResult(val check: FitCheck, val parameters: List<Double>?)

class PersonalFit(engineConfig: EngineConfig, private val config: StudyConfig) {
    private val optimizer = FsrsOptimizer(engineConfig)

    val firstFitReviews: Int = ceil(engineConfig.optimizerBatchReviews / (1 - config.fitHoldoutShare)).toInt()

    fun reviews(log: List<LogEntry>): Int = optimizer.reviewCount(LogReplay.histories(log))

    fun nextFitAt(last: FitCheck?): Int = maxOf(firstFitReviews, (last?.reviews ?: 0) + config.refitAfterReviews)

    fun isDue(log: List<LogEntry>, last: FitCheck?): Boolean = reviews(log) >= nextFitAt(last)

    fun run(log: List<LogEntry>, current: List<Double>, today: Int): FitResult? {
        val histories = LogReplay.histories(log)
        val days = optimizer.scoredDays(histories).sorted()
        if (days.isEmpty()) return null
        val splitDay = days[(days.size * (1 - config.fitHoldoutShare)).toInt().coerceAtMost(days.lastIndex)]
        val earlier = Int.MIN_VALUE until splitDay
        val later = splitDay..Int.MAX_VALUE
        val candidate = optimizer.fit(histories, earlier) ?: return null
        val check = FitCheck(
            day = today,
            reviews = days.size,
            heldOut = optimizer.reviewCount(histories, later),
            currentLoss = optimizer.loss(current, histories, later),
            fittedLoss = optimizer.loss(candidate, histories, later),
            adopted = false,
        ).let { it.copy(adopted = it.fittedLoss < it.currentLoss) }
        return FitResult(check, if (check.adopted) optimizer.fit(histories) else null)
    }
}
