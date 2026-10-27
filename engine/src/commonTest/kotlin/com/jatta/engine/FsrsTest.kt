package com.jatta.engine

import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.fsrs.Memory
import kotlin.math.abs
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FsrsTest {

    @Test
    fun allGood() = check(FsrsVectors.allGood)

    @Test
    fun mixed() = check(FsrsVectors.mixed)

    @Test
    fun easyThenLapses() = check(FsrsVectors.easyThenLapses)

    @Test
    fun offSchedule() = check(FsrsVectors.offSchedule)

    @Test
    fun lowRetention() = check(FsrsVectors.lowRetention)

    @Test
    fun retrievabilityFallsWithTime() {
        val fsrs = Fsrs(desiredRetention = 0.9, maximumIntervalDays = 36_500)
        val memory = fsrs.first(Grade.Good)
        val expected = mapOf(0 to 1.0, 1 to 0.9468474993825461, 3 to 0.8809479557659419, 10 to 0.7743669167614039, 30 to 0.6675263338730357)
        expected.forEach { (days, recall) -> assertClose(recall, fsrs.retrievability(memory, days), "after $days days") }
    }

    private fun check(case: FsrsVectors.Case) {
        val fsrs = Fsrs(desiredRetention = case.retention, maximumIntervalDays = 36_500)
        var memory: Memory? = null
        var lastDay = 0
        case.steps.forEachIndexed { index, step ->
            val next = memory?.let { fsrs.next(it, step.day - lastDay, step.grade) } ?: fsrs.first(step.grade)
            assertClose(step.stability, next.stability, "stability at step $index")
            assertClose(step.difficulty, next.difficulty, "difficulty at step $index")
            assertEquals(step.interval, fsrs.intervalDays(next.stability), "interval at step $index")
            memory = next
            lastDay = step.day
        }
    }

    private fun assertClose(expected: Double, actual: Double, what: String) =
        assertTrue(abs(expected - actual) <= 1e-9 * max(1.0, abs(expected)), "$what: expected $expected, got $actual")
}
