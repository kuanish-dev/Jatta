package com.jatta.engine

import com.jatta.engine.session.Advice
import com.jatta.engine.session.Answer
import com.jatta.engine.session.Baseline
import com.jatta.engine.session.BreakReason
import com.jatta.engine.session.RestCoach
import com.jatta.engine.session.Session
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RestCoachTest {
    private val config = EngineConfig()
    private val baseline = Baseline(accuracy = 0.9, medianResponseMs = 4_000)
    private val minute = 60_000L

    private val tired = List(10) { Answer(correct = it % 2 == 0, responseMs = 6_000) }

    @Test
    fun breakAfterEachBlock() {
        val coach = RestCoach(config, baseline, startMs = 0)
        assertEquals(Advice.Continue, coach.onAnswer(Answer(true, 4_000), nowMs = 24 * minute))
        assertEquals(Advice.Break(5, BreakReason.Scheduled), coach.onAnswer(Answer(true, 4_000), nowMs = 25 * minute))
        coach.onBreakFinished(nowMs = 30 * minute)
        assertEquals(Advice.Continue, coach.onAnswer(Answer(true, 4_000), nowMs = 31 * minute))
    }

    @Test
    fun fatigueEarnsABreakAndTheSecondTimeEndsTheSession() {
        val coach = RestCoach(config, baseline, startMs = 0)
        val first = tired.map { coach.onAnswer(it, nowMs = minute) }
        assertEquals(List(9) { Advice.Continue } + Advice.Break(5, BreakReason.Fatigue), first)
        coach.onBreakFinished(nowMs = 7 * minute)
        val second = tired.map { coach.onAnswer(it, nowMs = 8 * minute) }
        assertEquals(Advice.End, second.last())
    }

    @Test
    fun slowButAccurateAnswersAreNotFatigue() {
        val coach = RestCoach(config, baseline, startMs = 0)
        val advice = List(10) { coach.onAnswer(Answer(true, 9_000), nowMs = minute) }
        assertTrue(advice.all { it == Advice.Continue })
    }

    @Test
    fun quietRestOnlyAfterNewMaterial() {
        val coach = RestCoach(config, baseline, startMs = 0)
        assertEquals(Advice.QuietRest(7), coach.afterSession(Session(listOf("a"), listOf("n"), config)))
        assertEquals(Advice.Continue, coach.afterSession(Session(listOf("a"), emptyList(), config)))
    }
}
