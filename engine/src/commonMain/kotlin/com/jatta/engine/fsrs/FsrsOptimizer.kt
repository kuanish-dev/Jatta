package com.jatta.engine.fsrs

import com.jatta.engine.EngineConfig
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

data class Review(val day: Int, val grade: Grade)

class FsrsOptimizer(private val config: EngineConfig) {

    fun scoredDays(histories: List<List<Review>>): List<Int> = histories.flatMap { history ->
        history.take(config.optimizerMaxReviewsPerItem).zipWithNext()
            .filter { (previous, review) -> review.day > previous.day }
            .map { (_, review) -> review.day }
    }

    fun reviewCount(histories: List<List<Review>>, days: IntRange = ALL_DAYS): Int = scoredDays(histories).count { it in days }

    fun loss(parameters: List<Double>, histories: List<List<Review>>, days: IntRange = ALL_DAYS): Double =
        lossSum(parameters, histories, days) / reviewCount(histories, days)

    fun fit(histories: List<List<Review>>, days: IntRange = ALL_DAYS): List<Double>? {
        val reviews = reviewCount(histories, days)
        if (reviews < config.optimizerBatchReviews) return null
        val random = Random(SEED)
        val parameters = Fsrs.DEFAULT_PARAMETERS.toDoubleArray()
        val stepsPerEpoch = ceil(reviews.toDouble() / config.optimizerBatchReviews).toInt()
        val adam = Adam(parameters.size, config.optimizerLearningRate, stepsPerEpoch * config.optimizerEpochs)
        var best = parameters.toList()
        var bestLoss = Double.POSITIVE_INFINITY
        repeat(config.optimizerEpochs) {
            batches(histories.shuffled(random), days).forEach { batch ->
                adam.step(parameters, gradient(parameters, batch, days))
                clip(parameters)
            }
            val epochLoss = loss(parameters.toList(), histories, days)
            if (epochLoss < bestLoss) {
                bestLoss = epochLoss
                best = parameters.toList()
            }
        }
        return best
    }

    private fun batches(histories: List<List<Review>>, days: IntRange): List<List<List<Review>>> {
        val batches = mutableListOf<List<List<Review>>>()
        var batch = mutableListOf<List<Review>>()
        var reviews = 0
        histories.forEach { history ->
            val count = reviewCount(listOf(history), days)
            if (count == 0) return@forEach
            batch += history
            reviews += count
            if (reviews >= config.optimizerBatchReviews) {
                batches += batch
                batch = mutableListOf()
                reviews = 0
            }
        }
        if (batch.isNotEmpty()) batches += batch
        return batches
    }

    private fun gradient(parameters: DoubleArray, batch: List<List<Review>>, days: IntRange): DoubleArray =
        DoubleArray(parameters.size) { i ->
            val h = DIFFERENCE_STEP * max(1.0, abs(parameters[i]))
            val up = parameters.copyOf().also { it[i] += h }
            val down = parameters.copyOf().also { it[i] -= h }
            (lossSum(up.toList(), batch, days) - lossSum(down.toList(), batch, days)) / (2 * h)
        }

    private fun clip(parameters: DoubleArray) {
        for (i in parameters.indices) parameters[i] = parameters[i].coerceIn(Fsrs.LOWER_BOUNDS[i], Fsrs.UPPER_BOUNDS[i])
    }

    private fun lossSum(parameters: List<Double>, histories: List<List<Review>>, days: IntRange): Double {
        val fsrs = Fsrs(config.desiredRetention, config.maximumIntervalDays, parameters)
        return histories.sumOf { lossSum(fsrs, it, days) }
    }

    private fun lossSum(fsrs: Fsrs, history: List<Review>, days: IntRange): Double {
        var memory: Memory? = null
        var lastDay = 0
        var sum = 0.0
        history.take(config.optimizerMaxReviewsPerItem).forEach { review ->
            val elapsed = review.day - lastDay
            memory = memory?.let { before ->
                if (elapsed > 0 && review.day in days) {
                    sum += logLoss(fsrs.retrievability(before, elapsed), recalled = review.grade != Grade.Again)
                }
                fsrs.next(before, elapsed, review.grade)
            } ?: fsrs.first(review.grade)
            lastDay = review.day
        }
        return sum
    }

    private fun logLoss(predicted: Double, recalled: Boolean): Double =
        -max(ln(if (recalled) predicted else 1 - predicted), -100.0)

    private class Adam(size: Int, private val rate: Double, private val totalSteps: Int) {
        private val first = DoubleArray(size)
        private val second = DoubleArray(size)
        private var steps = 0

        fun step(parameters: DoubleArray, gradient: DoubleArray) {
            val learningRate = rate * (1 + cos(PI * min(steps, totalSteps) / totalSteps)) / 2
            steps++
            for (i in parameters.indices) {
                first[i] = BETA1 * first[i] + (1 - BETA1) * gradient[i]
                second[i] = BETA2 * second[i] + (1 - BETA2) * gradient[i] * gradient[i]
                val firstUnbiased = first[i] / (1 - BETA1.pow(steps))
                val secondUnbiased = second[i] / (1 - BETA2.pow(steps))
                parameters[i] -= learningRate * firstUnbiased / (sqrt(secondUnbiased) + EPSILON)
            }
        }
    }

    companion object {
        val ALL_DAYS = Int.MIN_VALUE..Int.MAX_VALUE
        private const val SEED = 42
        private const val DIFFERENCE_STEP = 1e-6
        private const val BETA1 = 0.9
        private const val BETA2 = 0.999
        private const val EPSILON = 1e-8
    }
}
