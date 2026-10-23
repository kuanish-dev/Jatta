package com.jatta.study

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.Grade
import com.jatta.study.store.LogEntry
import kotlin.random.Random

/**
 * Answer logs of a learner who meets [newPerDay] new items a day and reviews each one whenever the
 * default model says it is due, up to [lastDay]. Recall follows [truth] before [switchDay] and
 * [after] from then on.
 */
object SimulatedLog {
    private val config = EngineConfig()
    private const val DAY_MS = 86_400_000L

    /** Forgets new items about three times faster, and builds memory more slowly, than the defaults expect. */
    val fastForgetter = Fsrs.DEFAULT_PARAMETERS.toMutableList().apply {
        this[2] = 0.7
        this[8] = 1.2
    }

    fun of(
        itemIds: List<String>,
        lastDay: Int,
        seed: Int,
        truth: List<Double>,
        after: List<Double> = truth,
        switchDay: Int = Int.MAX_VALUE,
        newPerDay: Int = 12,
        firstDay: Int = 0,
    ): List<LogEntry> {
        val random = Random(seed)
        val app = Fsrs(config.desiredRetention, config.maximumIntervalDays)
        val models = listOf(truth, after).map { Fsrs(config.desiredRetention, config.maximumIntervalDays, it) }
        return itemIds.flatMapIndexed { index, item ->
            var day = firstDay + index / newPerDay
            var believed = app.first(Grade.Good)
            val real = models.map { it.first(Grade.Good) }.toMutableList()
            val log = mutableListOf(entry(item, day, Grade.Good, index))
            while (day + app.intervalDays(believed.stability) <= lastDay) {
                val elapsed = app.intervalDays(believed.stability)
                day += elapsed
                val now = if (day < switchDay) 0 else 1
                val grade = if (random.nextDouble() < models[now].retrievability(real[now], elapsed)) Grade.Good else Grade.Again
                log += entry(item, day, grade, index)
                believed = app.next(believed, elapsed, grade)
                models.indices.forEach { real[it] = models[it].next(real[it], elapsed, grade) }
            }
            log
        }.sortedBy { it.atMs }
    }

    private fun entry(item: String, day: Int, grade: Grade, index: Int) =
        LogEntry(day * DAY_MS + index, day, item, "Recognize", grade != Grade.Again, 4_000, grade.name, false, "code")
}
