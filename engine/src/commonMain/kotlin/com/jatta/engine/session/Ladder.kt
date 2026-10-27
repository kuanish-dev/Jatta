package com.jatta.engine.session

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Memory

enum class Rung { Recognize, FillGap, Translate, Compose, Speak }

class Ladder(private val config: EngineConfig) {

    fun rungFor(memory: Memory?, shift: Int): Rung {
        val base = if (memory == null) 0 else config.rungStabilityDays.count { memory.stability >= it }
        return Rung.entries[(base + shift).coerceIn(0, Rung.entries.lastIndex)]
    }

    fun adjustShift(shift: Int, recentAccuracy: Double): Int {
        val next = when {
            recentAccuracy > config.ladderUpAccuracy -> shift + 1
            recentAccuracy < config.ladderDownAccuracy -> shift - 1
            else -> shift
        }
        return next.coerceIn(-config.maxLadderShift, config.maxLadderShift)
    }
}
