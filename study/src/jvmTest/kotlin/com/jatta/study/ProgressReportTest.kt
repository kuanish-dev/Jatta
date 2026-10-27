package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.fsrs.Grade
import com.jatta.study.progress.DayMark
import com.jatta.study.progress.ProgressReport
import com.jatta.study.store.LogEntry
import com.jatta.study.store.TranslationEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProgressReportTest {
    private val content = RealContent.load()
    private val engine = Engine(content.course)
    private val words = content.course.itemsOf("work-words").map { it.id }

    private fun answer(item: String, day: Int, minute: Int, grade: Grade = Grade.Good) =
        LogEntry(day * 86_400_000L + minute * 60_000L, day, item, "Recognize", grade != Grade.Again, 3_000, grade.name, false, "code")

    @Test
    fun theReportFollowsTheLogDayByDay() {
        val log = words.take(6).mapIndexed { i, id -> answer(id, 10, i) } +
            words.take(6).map { answer(it, 11, 1) } +
            words.drop(6).mapIndexed { i, id -> answer(id, 13, i * 2) }
        val translations = listOf(TranslationEntry(13 * 86_400_000L + 30 * 60_000L, 13, 1, "x", 0.9, true, 1_000, "code"))
        val parts = listOf("Слова" to words.toSet(), "Всё" to content.items.keys)
        val report = ProgressReport.of(log, translations, engine, parts, today = 14)

        assertEquals(3, report.studyDays)
        assertEquals(2, report.longestStreak, "12 had new items to learn and was missed")
        assertEquals(1, report.streak.days, "13, with today still open")
        assertEquals(log.size, report.answers)
        assertEquals(listOf(10, 11, 12, 13, 14).size, report.heldByDay.size)
        assertEquals(6, report.heldByDay.first(), "six words learned on day 10 hold that evening")
        assertEquals(12, report.heldByDay[3])
        assertEquals(10, report.firstDay)
        assertEquals(listOf(6, 6, 6, 12, 12), report.metByDay)
        assertTrue(report.heldByDay.zip(report.metByDay).all { (held, met) -> held <= met })
        assertEquals(12, report.parts.first().met)
        assertEquals(words.size, report.parts.first().total)
        assertEquals(84, report.calendar.size + (7 - 1 - (14 + 3).mod(7)), "twelve weeks up to today")
        assertEquals(0, (report.calendar.first().day + 3).mod(7), "the calendar starts on a Monday")
        assertEquals(DayMark.Missed, report.calendar.single { it.day == 12 }.mark)
        assertEquals(DayMark.Today, report.calendar.last().mark)
        assertEquals(1, report.translatedRight)
        assertTrue(report.minutes >= 3)
    }
}
