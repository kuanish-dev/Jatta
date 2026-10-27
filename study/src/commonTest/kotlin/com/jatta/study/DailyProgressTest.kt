package com.jatta.study

import com.jatta.study.calibration.Calibration
import com.jatta.study.progress.DailyProgress
import com.jatta.study.progress.DayMark
import com.jatta.study.store.LogEntry
import com.jatta.study.store.TranslationEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DailyProgressTest {
    private val today = 100

    @Test
    fun theStreakCountsDaysInARowAndTodayOnceStudied() {
        val studied = setOf(96, 97, 98, 99)
        assertEquals(4, DailyProgress.streak(studied, today) { false }.days, "today is still open")
        assertEquals(5, DailyProgress.streak(studied + today, today) { false }.days)
    }

    @Test
    fun aMissedDayBreaksTheStreakButARestDayDoesNot() {
        val studied = setOf(94, 95, 97, 99)
        assertEquals(1, DailyProgress.streak(studied, today) { false }.days, "98 was missed")
        val streak = DailyProgress.streak(studied, today) { it == 96 || it == 98 }
        assertEquals(4, streak.days, "nothing to do on 96 and 98")
        assertEquals(
            listOf(DayMark.Studied, DayMark.Studied, DayMark.Rest, DayMark.Studied, DayMark.Rest, DayMark.Studied, DayMark.Today),
            streak.week,
        )
        assertEquals(0, DailyProgress.streak(emptySet(), today) { true }.days, "no study yet, no streak")
    }

    @Test
    fun theSummaryCountsFirstAnswersNewItemsTranslationsAndTime() {
        val minute = 60_000L
        fun answer(item: String, day: Int, at: Long, correct: Boolean, graded: Boolean = true, by: String = "code") =
            LogEntry(day * 86_400_000L + at, day, item, "Recognize", correct, 3_000, if (graded) "Good" else null, false, by)
        val log = listOf(
            answer("w-old", 98, 0, correct = true),
            answer("w-placed", 98, 0, correct = true, by = Calibration.PLACEMENT),
            answer("w-old", 99, 0, correct = false),
            answer("w-old", 99, minute, correct = true, graded = false),
            answer("w-new", 99, 2 * minute, correct = true),
            answer("w-placed", 99, 10 * minute, correct = true),
            answer("w-other", 99, 300 * minute, correct = true),
        )
        val translations = listOf(TranslationEntry(99 * 86_400_000L + 305 * minute, 99, 1, "x", 0.9, true, 1_000, "code"))
        val summary = DailyProgress.summary(log, translations, 99)!!
        assertEquals(4, summary.answered)
        assertEquals(3, summary.rightFirstTime)
        assertEquals(2, summary.newItems, "w-new and w-other; w-placed was known from the test")
        assertEquals(2, summary.reviews)
        assertEquals(1, summary.translated)
        assertEquals(15, summary.minutes, "10 minutes in the morning and 5 in the evening")
        assertNull(DailyProgress.summary(log, translations, 97))
    }
}
