package com.jatta.study.progress

import com.jatta.study.calibration.Calibration
import com.jatta.study.store.LogEntry
import com.jatta.study.store.TranslationEntry

data class DaySummary(
    val day: Int,
    val answered: Int,
    val rightFirstTime: Int,
    val newItems: Int,
    val reviews: Int,
    val translated: Int,
    val translatedRight: Int,
    val minutes: Int,
)

enum class DayMark { Studied, Rest, Missed, Today, Before }

data class Streak(val days: Int, val studiedToday: Boolean, val week: List<DayMark>, val today: Int)

object DailyProgress {
    private const val SITTING_GAP_MS = 20 * 60_000L

    fun studiedDays(log: List<LogEntry>, translations: List<TranslationEntry>): Set<Int> =
        log.filter { it.checkedBy != Calibration.PLACEMENT }.mapTo(HashSet()) { it.day } + translations.map { it.day }

    fun streak(studied: Set<Int>, today: Int, nothingToDo: (Int) -> Boolean): Streak {
        val first = studied.minOrNull()
        val studiedToday = today in studied
        val rest = HashMap<Int, Boolean>()
        fun isRest(day: Int) = rest.getOrPut(day) { nothingToDo(day) }
        var days = if (studiedToday) 1 else 0
        var day = today - 1
        while (first != null && day >= first) {
            when {
                day in studied -> days++
                !isRest(day) -> break
            }
            day--
        }
        val week = (today - 6..today).map { d ->
            when {
                d in studied -> DayMark.Studied
                d == today -> DayMark.Today
                first == null || d < first -> DayMark.Before
                isRest(d) -> DayMark.Rest
                else -> DayMark.Missed
            }
        }
        return Streak(days, studiedToday, week, today)
    }

    fun summary(log: List<LogEntry>, translations: List<TranslationEntry>, day: Int): DaySummary? {
        val studied = log.filter { it.checkedBy != Calibration.PLACEMENT }
        val answers = studied.filter { it.day == day }
        val translated = translations.filter { it.day == day }
        if (answers.isEmpty() && translated.isEmpty()) return null
        val graded = answers.filter { it.grade != null }
        val firstDays = log.filter { it.grade != null }.groupBy { it.item }.mapValues { (_, entries) -> entries.minOf { it.day } }
        val newItems = graded.map { it.item }.distinct().count { firstDays[it] == day }
        return DaySummary(
            day = day,
            answered = graded.size,
            rightFirstTime = graded.count { it.correct },
            newItems = newItems,
            reviews = graded.size - newItems,
            translated = translated.size,
            translatedRight = translated.count { it.passed },
            minutes = minutes(answers.map { it.atMs } + translated.map { it.atMs }),
        )
    }

    internal fun minutes(times: List<Long>): Int {
        val sorted = times.sorted()
        var total = 0L
        var start = sorted.first()
        sorted.zipWithNext().forEach { (a, b) ->
            if (b - a > SITTING_GAP_MS) {
                total += a - start
                start = b
            }
        }
        total += sorted.last() - start
        return ((total + 59_999) / 60_000).toInt().coerceAtLeast(1)
    }
}
