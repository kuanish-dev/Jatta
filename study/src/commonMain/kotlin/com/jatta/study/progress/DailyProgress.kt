package com.jatta.study.progress

import com.jatta.study.calibration.Calibration
import com.jatta.study.store.LogEntry
import com.jatta.study.store.TranslationEntry

/** What one learning day brought, from the answer and translation logs (М27). */
data class DaySummary(
    val day: Int,
    /** First answers of the day: each item counts once. */
    val answered: Int,
    val rightFirstTime: Int,
    /** Items met for the first time that day. */
    val newItems: Int,
    val reviews: Int,
    val translated: Int,
    val translatedRight: Int,
    /** Time between the first and the last answer of each sitting, rounded up. */
    val minutes: Int,
)

/** A day of the week strip: studied, a rest day with nothing to do, missed, today still open, or before the start. */
enum class DayMark { Studied, Rest, Missed, Today, Before }

/** [days] in a row without a missed day, today included once studied; [week] — the seven days up to [today], oldest first. */
data class Streak(val days: Int, val studiedToday: Boolean, val week: List<DayMark>, val today: Int)

/**
 * М27: the daily summary and the streak. A day breaks the streak only when there was something to study and the
 * learner didn't: a day with nothing due and nothing new is a rest day. Answers the placement test recorded don't count.
 */
object DailyProgress {
    /** A gap longer than this between answers starts another sitting. */
    private const val SITTING_GAP_MS = 20 * 60_000L

    fun studiedDays(log: List<LogEntry>, translations: List<TranslationEntry>): Set<Int> =
        log.filter { it.checkedBy != Calibration.PLACEMENT }.mapTo(HashSet()) { it.day } + translations.map { it.day }

    /** [nothingToDo] says whether a past day had nothing due and nothing new; it is asked only about days not studied. */
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

    /** The day's summary, or null when nothing was studied that day. */
    fun summary(log: List<LogEntry>, translations: List<TranslationEntry>, day: Int): DaySummary? {
        val studied = log.filter { it.checkedBy != Calibration.PLACEMENT }
        val answers = studied.filter { it.day == day }
        val translated = translations.filter { it.day == day }
        if (answers.isEmpty() && translated.isEmpty()) return null
        val graded = answers.filter { it.grade != null }
        // Known from the placement test means met before, not new that day.
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

    private fun minutes(times: List<Long>): Int {
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
