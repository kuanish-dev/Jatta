package com.jatta.engine

import com.jatta.engine.content.TextCoverage
import com.jatta.engine.fsrs.Memory
import com.jatta.engine.session.Ladder
import com.jatta.engine.session.Rung
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SmallPartsTest {
    private val config = EngineConfig()

    @Test
    fun ladderClimbsWithStabilityAndShiftsWithAccuracy() {
        val ladder = Ladder(config)
        assertEquals(Rung.Recognize, ladder.rungFor(memory = null, shift = 0))
        assertEquals(Rung.FillGap, ladder.rungFor(Memory(5.0, 5.0), shift = 0))
        assertEquals(Rung.Translate, ladder.rungFor(Memory(12.0, 5.0), shift = 0))
        assertEquals(Rung.Speak, ladder.rungFor(Memory(100.0, 5.0), shift = 1))
        assertEquals(Rung.Recognize, ladder.rungFor(Memory(5.0, 5.0), shift = -1))

        assertEquals(1, ladder.adjustShift(0, recentAccuracy = 0.97))
        assertEquals(-1, ladder.adjustShift(0, recentAccuracy = 0.70))
        assertEquals(0, ladder.adjustShift(0, recentAccuracy = 0.85))
        assertEquals(2, ladder.adjustShift(2, recentAccuracy = 1.0))
    }

    @Test
    fun textCoverageCountsUnknownWords() {
        val coverage = TextCoverage(config)
        val short = coverage.check("I like the red apple.", setOf("I", "like", "the", "apple"))
        assertEquals(setOf("red"), short.unknownWords)
        assertFalse(short.acceptable)

        val long = coverage.check(List(39) { "go" }.joinToString(" ") + " fly", setOf("go"))
        assertTrue(long.acceptable)

        val tooManyNew = coverage.check(List(96) { "go" }.joinToString(" ") + " fly swim run jump", setOf("go"))
        assertTrue(tooManyNew.share >= config.minTextCoverage)
        assertFalse(tooManyNew.acceptable)

        assertTrue(coverage.check("Don't go.", setOf("don't", "go")).acceptable)
    }

    @Test
    fun learningDayStartsAtFourInTheMorningLocalTime() {
        val day20260101 = 20_454
        val utc = { day: Int, hour: Int, minute: Int -> (day * 24L * 60 + hour * 60 + minute) * 60_000 }
        val almaty = 5 * 60
        // 02:00 on 2 January in Almaty still belongs to 1 January
        assertEquals(day20260101, learningDay(utc(day20260101, 21, 0), almaty, dayStartHour = 4))
        // 04:30 on 2 January in Almaty is the next learning day
        assertEquals(day20260101 + 1, learningDay(utc(day20260101, 23, 30), almaty, dayStartHour = 4))
    }
}
