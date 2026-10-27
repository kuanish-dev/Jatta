package com.jatta.engine.session

import com.jatta.engine.EngineConfig

private const val MS_PER_MINUTE = 60_000L

data class Baseline(val accuracy: Double, val medianResponseMs: Long) {
    companion object {
        val Default = Baseline(accuracy = 0.85, medianResponseMs = 6_000)
    }
}

enum class BreakReason { Scheduled, Fatigue }

sealed interface Advice {
    data object Continue : Advice

    data class Break(val minutes: Int, val reason: BreakReason) : Advice

    data object End : Advice

    data class QuietRest(val minutes: Int) : Advice
}

class RestCoach(private val config: EngineConfig, private val baseline: Baseline, startMs: Long) {
    private var blockStartMs = startMs
    private val recent = ArrayDeque<Answer>()
    private var fatigueBreaks = 0

    fun onAnswer(answer: Answer, nowMs: Long): Advice {
        recent.addLast(answer)
        if (recent.size > config.fatigueWindow) recent.removeFirst()
        if (isFatigued()) {
            recent.clear()
            fatigueBreaks++
            return if (fatigueBreaks == 1) Advice.Break(config.breakMinutes, BreakReason.Fatigue) else Advice.End
        }
        if (nowMs - blockStartMs >= config.blockMinutes * MS_PER_MINUTE) {
            return Advice.Break(config.breakMinutes, BreakReason.Scheduled)
        }
        return Advice.Continue
    }

    fun onBreakFinished(nowMs: Long) {
        blockStartMs = nowMs
    }

    fun afterSession(session: Session): Advice =
        if (session.introducesNew) Advice.QuietRest(config.quietRestMinutes) else Advice.Continue

    private fun isFatigued(): Boolean {
        if (recent.size < config.fatigueWindow) return false
        val accuracy = recent.count { it.correct }.toDouble() / recent.size
        val slowdown = median(recent.map { it.responseMs }).toDouble() / baseline.medianResponseMs - 1
        return accuracy <= baseline.accuracy - config.fatigueAccuracyDrop && slowdown >= config.fatigueSlowdown
    }

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
