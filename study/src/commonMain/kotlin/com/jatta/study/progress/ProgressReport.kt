package com.jatta.study.progress

import com.jatta.engine.Engine
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.study.calibration.Calibration
import com.jatta.study.store.LogEntry
import com.jatta.study.store.TranslationEntry

data class DayActivity(val day: Int, val mark: DayMark, val answers: Int, val minutes: Int)

data class PartProgress(val title: String, val held: Int, val met: Int, val total: Int)

data class ProgressReport(
    val streak: Streak,
    val longestStreak: Int,
    val studyDays: Int,
    val answers: Int,
    val rightFirstTime: Int,
    val minutes: Int,
    val translated: Int,
    val translatedRight: Int,
    val calendar: List<DayActivity>,
    val firstDay: Int,
    val heldByDay: List<Int>,
    val metByDay: List<Int>,
    val parts: List<PartProgress>,
) {
    companion object {
        const val CALENDAR_WEEKS = 12

        fun of(
            log: List<LogEntry>,
            translations: List<TranslationEntry>,
            engine: Engine,
            parts: List<Pair<String, Set<String>>>,
            today: Int,
        ): ProgressReport {
            val studied = DailyProgress.studiedDays(log, translations)
            val first = studied.minOrNull() ?: today
            val graded = log.filter { it.grade != null }.sortedBy { it.atMs }.groupBy { it.day }
            val progress = mutableMapOf<String, ItemProgress>()
            graded.filterKeys { it < first }.values.flatten().forEach { apply(engine, progress, it) }
            val marks = HashMap<Int, DayMark>()
            val held = mutableListOf<Int>()
            val met = mutableListOf<Int>()
            for (day in first..today) {
                marks[day] = when {
                    day in studied -> DayMark.Studied
                    day == today -> DayMark.Today
                    engine.planDay(Learner(progress.toMap()), day).let { it.reviews.isEmpty() && it.newItems.isEmpty() } -> DayMark.Rest
                    else -> DayMark.Missed
                }
                graded[day].orEmpty().forEach { apply(engine, progress, it) }
                held += progress.values.count { engine.gate.holds(it, day) }
                met += progress.size
            }
            val counted = log.filter { it.checkedBy != Calibration.PLACEMENT }
            val firstAnswers = counted.filter { it.grade != null }
            val byDay = counted.groupBy { it.day }
            val translatedByDay = translations.groupBy { it.day }
            fun minutesOf(day: Int): Int {
                val times = byDay[day].orEmpty().map { it.atMs } + translatedByDay[day].orEmpty().map { it.atMs }
                return if (times.isEmpty()) 0 else DailyProgress.minutes(times)
            }
            val calendarStart = today - (today + 3).mod(7) - 7 * (CALENDAR_WEEKS - 1)
            return ProgressReport(
                streak = DailyProgress.streak(studied, today) { marks[it] == DayMark.Rest },
                longestStreak = longest(first..today, marks),
                studyDays = studied.size,
                answers = firstAnswers.size,
                rightFirstTime = firstAnswers.count { it.correct },
                minutes = studied.sumOf { minutesOf(it) },
                translated = translations.size,
                translatedRight = translations.count { it.passed },
                calendar = (calendarStart..today).map { day ->
                    DayActivity(day, marks[day] ?: DayMark.Before, byDay[day].orEmpty().count { it.grade != null }, minutesOf(day))
                },
                firstDay = first,
                heldByDay = if (studied.isEmpty()) emptyList() else held,
                metByDay = if (studied.isEmpty()) emptyList() else met,
                parts = parts.map { (title, ids) ->
                    PartProgress(title, ids.count { engine.gate.holds(progress[it], today) }, ids.count { it in progress }, ids.size)
                },
            )
        }

        private fun apply(engine: Engine, progress: MutableMap<String, ItemProgress>, entry: LogEntry) {
            progress[entry.item] = engine.memory.review(progress[entry.item], entry.item, Grade.valueOf(entry.grade!!), entry.day)
        }

        private fun longest(days: IntRange, marks: Map<Int, DayMark>): Int {
            var best = 0
            var run = 0
            for (day in days) {
                when (marks[day]) {
                    DayMark.Studied -> run++
                    DayMark.Missed -> run = 0
                    else -> Unit
                }
                best = maxOf(best, run)
            }
            return best
        }
    }
}
