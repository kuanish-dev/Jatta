package com.jatta.study.calibration

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.FsrsOptimizer
import com.jatta.study.StudyConfig
import com.jatta.study.store.FitCheck
import com.jatta.study.store.LogEntry
import kotlin.math.ceil

/** A finished fit: the check, and the parameters to use from now on when the fit passed it. */
data class FitResult(val check: FitCheck, val parameters: List<Double>?)

/**
 * Fits the memory model to the learner's own answer log (М2) and adopts the fit only if it predicts
 * reviews it has not seen better than the parameters in use: the latest reviews are held out, the
 * model is fitted on the rest, and both are scored on the held-out ones, the way memory models are
 * compared in srs-benchmark. A fit that passes is refitted on every review before it is adopted.
 */
class PersonalFit(engineConfig: EngineConfig, private val config: StudyConfig) {
    private val optimizer = FsrsOptimizer(engineConfig)

    /** Reviews needed for the first fit: one optimizer batch to learn from plus the held-out share. */
    val firstFitReviews: Int = ceil(engineConfig.optimizerBatchReviews / (1 - config.fitHoldoutShare)).toInt()

    /** Reviews the memory model can be fitted on. */
    fun reviews(log: List<LogEntry>): Int = optimizer.reviewCount(LogReplay.histories(log))

    /** Reviews at which the next fit runs. */
    fun nextFitAt(last: FitCheck?): Int = maxOf(firstFitReviews, (last?.reviews ?: 0) + config.refitAfterReviews)

    fun isDue(log: List<LogEntry>, last: FitCheck?): Boolean = reviews(log) >= nextFitAt(last)

    /** Null when the reviews before the held-out ones are fewer than one optimizer batch. */
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
