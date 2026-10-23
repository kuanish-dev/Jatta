package com.langengine.engine.session

import com.langengine.engine.EngineConfig

private const val MS_PER_MINUTE = 60_000L

/** The learner's usual accuracy and speed from past sessions; fatigue is measured against it (М16). */
data class Baseline(val accuracy: Double, val medianResponseMs: Long) {
    companion object {
        /** Used until the learner has enough history. Weak: replaced by real numbers after a few sessions. */
        val Default = Baseline(accuracy = 0.85, medianResponseMs = 6_000)
    }
}

enum class BreakReason { Scheduled, Fatigue }

/** What the coach advises after an answer or at the end of a session. */
sealed interface Advice {
    data object Continue : Advice

    /** Stop for [minutes]: on schedule (М14) or because of fatigue (М16). */
    data class Break(val minutes: Int, val reason: BreakReason) : Advice

    /** Fatigue came back after a break: end the session, the rest of the queue waits (М16). */
    data object End : Advice

    /** Minutes without the phone and without new material after learning new items (М15). */
    data class QuietRest(val minutes: Int) : Advice
}

/**
 * Decides when to rest: a break every [EngineConfig.blockMinutes] (М14), a break when accuracy falls
 * and answers slow down against the learner's baseline (М16), the end of the session when that happens
 * again after a break, and quiet rest after new material (М15).
 */
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

    /** The learner is back from a break: a new block starts. */
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
