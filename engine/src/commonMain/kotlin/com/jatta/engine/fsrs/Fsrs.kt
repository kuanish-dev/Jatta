package com.jatta.engine.fsrs

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round

enum class Grade(val value: Int) { Again(1), Hard(2), Good(3), Easy(4) }

data class Memory(val stability: Double, val difficulty: Double)

class Fsrs(
    private val desiredRetention: Double,
    private val maximumIntervalDays: Int,
    private val w: List<Double> = DEFAULT_PARAMETERS,
) {
    init {
        require(w.size == 21) { "FSRS-6 needs 21 parameters, got ${w.size}" }
    }

    private val decay = -w[20]
    private val factor = 0.9.pow(1 / decay) - 1

    fun first(grade: Grade): Memory = Memory(
        stability = clampStability(w[grade.value - 1]),
        difficulty = clampDifficulty(initialDifficulty(grade)),
    )

    fun next(memory: Memory, elapsedDays: Int, grade: Grade): Memory {
        val stability = if (elapsedDays < 1) {
            shortTermStability(memory.stability, grade)
        } else {
            nextStability(memory, retrievability(memory, elapsedDays), grade)
        }
        return Memory(stability, nextDifficulty(memory.difficulty, grade))
    }

    fun retrievability(memory: Memory, elapsedDays: Int): Double =
        (1 + factor * max(0, elapsedDays) / memory.stability).pow(decay)

    fun intervalDays(stability: Double): Int {
        val days = round(stability / factor * (desiredRetention.pow(1 / decay) - 1)).toInt()
        return days.coerceIn(1, maximumIntervalDays)
    }

    private fun initialDifficulty(grade: Grade): Double = w[4] - exp(w[5] * (grade.value - 1)) + 1

    private fun nextDifficulty(difficulty: Double, grade: Grade): Double {
        val delta = -(w[6] * (grade.value - 3))
        val damped = difficulty + (10.0 - difficulty) * delta / 9.0
        val reverted = w[7] * initialDifficulty(Grade.Easy) + (1 - w[7]) * damped
        return clampDifficulty(reverted)
    }

    private fun shortTermStability(stability: Double, grade: Grade): Double {
        var increase = exp(w[17] * (grade.value - 3 + w[18])) * stability.pow(-w[19])
        if (grade != Grade.Again) increase = max(increase, 1.0)
        return clampStability(stability * increase)
    }

    private fun nextStability(memory: Memory, retrievability: Double, grade: Grade): Double {
        val next = if (grade == Grade.Again) {
            forgetStability(memory, retrievability)
        } else {
            recallStability(memory, retrievability, grade)
        }
        return clampStability(next)
    }

    private fun forgetStability(memory: Memory, retrievability: Double): Double {
        val longTerm = w[11] *
            memory.difficulty.pow(-w[12]) *
            ((memory.stability + 1).pow(w[13]) - 1) *
            exp((1 - retrievability) * w[14])
        val shortTerm = memory.stability / exp(w[17] * w[18])
        return min(longTerm, shortTerm)
    }

    private fun recallStability(memory: Memory, retrievability: Double, grade: Grade): Double {
        val hardPenalty = if (grade == Grade.Hard) w[15] else 1.0
        val easyBonus = if (grade == Grade.Easy) w[16] else 1.0
        return memory.stability * (
            1 + exp(w[8]) *
                (11 - memory.difficulty) *
                memory.stability.pow(-w[9]) *
                (exp((1 - retrievability) * w[10]) - 1) *
                hardPenalty *
                easyBonus
            )
    }

    private fun clampDifficulty(difficulty: Double) = difficulty.coerceIn(1.0, 10.0)

    private fun clampStability(stability: Double) = max(stability, 0.001)

    companion object {
        val DEFAULT_PARAMETERS = listOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666, 0.796,
            1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658, 0.1542,
        )

        val LOWER_BOUNDS = listOf(
            0.001, 0.001, 0.001, 0.001, 1.0, 0.001, 0.001, 0.001, 0.0, 0.0, 0.001,
            0.001, 0.001, 0.001, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 0.1,
        )
        val UPPER_BOUNDS = listOf(
            100.0, 100.0, 100.0, 100.0, 10.0, 4.0, 4.0, 0.75, 4.5, 0.8, 3.5,
            5.0, 0.25, 0.9, 4.0, 1.0, 6.0, 2.0, 2.0, 0.8, 0.8,
        )
    }
}
