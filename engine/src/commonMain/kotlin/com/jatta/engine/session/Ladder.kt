package com.jatta.engine.session

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Memory

/** Exercise forms from easiest to hardest (М5). */
enum class Rung { Recognize, FillGap, Translate, Compose, Speak }

/** Picks the exercise form for an item and keeps accuracy in the useful band (М5, М6). */
class Ladder(private val config: EngineConfig) {

    /** The rung climbs with memory stability and is shifted by the learner's recent accuracy. */
    fun rungFor(memory: Memory?, shift: Int): Rung {
        val base = if (memory == null) 0 else config.rungStabilityDays.count { memory.stability >= it }
        return Rung.entries[(base + shift).coerceIn(0, Rung.entries.lastIndex)]
    }

    /** One step harder when nearly everything is right, one step easier when too much is wrong. */
    fun adjustShift(shift: Int, recentAccuracy: Double): Int {
        val next = when {
            recentAccuracy > config.ladderUpAccuracy -> shift + 1
            recentAccuracy < config.ladderDownAccuracy -> shift - 1
            else -> shift
        }
        return next.coerceIn(-config.maxLadderShift, config.maxLadderShift)
    }
}
