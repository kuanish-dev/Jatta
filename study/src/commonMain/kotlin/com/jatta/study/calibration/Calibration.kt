package com.jatta.study.calibration

import com.jatta.engine.memory.MemoryModel
import com.jatta.study.StudyConfig
import com.jatta.study.store.LogEntry
import kotlin.math.sqrt

enum class CalibrationVerdict { TooFewReviews, Accurate, Overestimates, Underestimates }

data class CalibrationBin(val from: Double, val to: Double, val count: Int, val predicted: Double, val actual: Double)

data class CalibrationReport(
    val reviews: Int,
    val predicted: Double,
    val actual: Double,
    val low: Double,
    val high: Double,
    val bins: List<CalibrationBin>,
    val verdict: CalibrationVerdict,
    val minReviews: Int,
)

object Calibration {
    private val edges = listOf(0.0, 0.7, 0.8, 0.85, 0.9, 0.95, 1.0)

    fun of(log: List<LogEntry>, memory: MemoryModel, config: StudyConfig): CalibrationReport {
        val scored = mutableListOf<Pair<Double, Boolean>>()
        LogReplay.progress(log, memory) { entry, previous ->
            if (previous != null && entry.checkedBy != PLACEMENT && entry.day > previous.lastReviewDay) {
                scored += memory.recallOn(previous, entry.day) to entry.correct
            }
        }

        val n = scored.size
        val predicted = if (n == 0) 0.0 else scored.sumOf { it.first } / n
        val recalled = scored.count { it.second }
        val actual = if (n == 0) 0.0 else recalled.toDouble() / n
        val (low, high) = wilson(recalled, n)
        val verdict = when {
            n < config.minReviewsForCalibration -> CalibrationVerdict.TooFewReviews
            predicted > high -> CalibrationVerdict.Overestimates
            predicted < low -> CalibrationVerdict.Underestimates
            else -> CalibrationVerdict.Accurate
        }
        val bins = edges.zipWithNext().mapNotNull { (from, to) ->
            val inBin = scored.filter { it.first >= from && (it.first < to || to == 1.0) }
            if (inBin.isEmpty()) null else CalibrationBin(
                from, to, inBin.size,
                predicted = inBin.sumOf { it.first } / inBin.size,
                actual = inBin.count { it.second }.toDouble() / inBin.size,
            )
        }
        return CalibrationReport(n, predicted, actual, low, high, bins, verdict, config.minReviewsForCalibration)
    }

    private fun wilson(k: Int, n: Int): Pair<Double, Double> {
        if (n == 0) return 0.0 to 1.0
        val z = 1.96
        val p = k.toDouble() / n
        val denominator = 1 + z * z / n
        val center = (p + z * z / (2 * n)) / denominator
        val half = z * sqrt(p * (1 - p) / n + z * z / (4.0 * n * n)) / denominator
        return (center - half).coerceAtLeast(0.0) to (center + half).coerceAtMost(1.0)
    }

    const val PLACEMENT = "placement"
}
