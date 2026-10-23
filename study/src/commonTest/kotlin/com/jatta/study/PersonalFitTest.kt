package com.jatta.study

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.FsrsOptimizer
import com.jatta.study.calibration.Calibration
import com.jatta.study.calibration.LogReplay
import com.jatta.study.calibration.PersonalFit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonalFitTest {
    private val config = StudyConfig()
    private val fit = PersonalFit(EngineConfig(), config)
    private val defaults = Fsrs.DEFAULT_PARAMETERS
    private val optimizer = FsrsOptimizer(EngineConfig())
    private val items = List(1000) { "w-$it" }

    @Test
    fun aFitThatPredictsUnseenReviewsBetterIsAdopted() {
        val log = SimulatedLog.of(items, lastDay = 120, seed = 1, truth = SimulatedLog.fastForgetter)
        val result = fit.run(log, defaults, today = 120)!!
        assertTrue(result.check.adopted)
        assertTrue(result.check.heldOut in result.check.reviews / 6..result.check.reviews / 4, "about a fifth held out")
        val days = optimizer.scoredDays(LogReplay.histories(log)).sorted()
        val heldOut = days[days.size - result.check.heldOut]..Int.MAX_VALUE
        val truthLoss = optimizer.loss(SimulatedLog.fastForgetter, LogReplay.histories(log), heldOut)
        val gain = result.check.currentLoss - result.check.fittedLoss
        assertTrue(gain > 0.5 * (result.check.currentLoss - truthLoss), "most of what the true parameters would gain")
        val parameters = assertNotNull(result.parameters)
        assertTrue(parameters[2] < defaults[2], "the first interval shrinks")
        assertTrue(parameters[8] < defaults[8], "memory builds more slowly")
    }

    @Test
    fun aFitThatDoesNotPredictLaterReviewsIsRejected() {
        // the learner forgot fast at first and then like everyone else: the fit learns a past that is gone
        val log = SimulatedLog.of(items, lastDay = 120, seed = 2, truth = SimulatedLog.fastForgetter, after = defaults, switchDay = 70)
        val result = fit.run(log, defaults, today = 120)!!
        assertFalse(result.check.adopted)
        assertNull(result.parameters)
    }

    @Test
    fun theFirstFitWaitsForABatchPlusTheHeldOutReviews() {
        assertEquals(640, fit.firstFitReviews)
        val few = SimulatedLog.of(items.take(150), lastDay = 30, seed = 3, truth = defaults)
        assertTrue(fit.reviews(few) < 640)
        assertFalse(fit.isDue(few, last = null))
        assertNull(fit.run(few, defaults, today = 30), "too few reviews before the held-out ones")
    }

    @Test
    fun aRefitWaitsForNewReviews() {
        val log = SimulatedLog.of(items.take(300), lastDay = 60, seed = 5, truth = defaults)
        assertTrue(fit.isDue(log, last = null))
        val last = fit.run(log, defaults, today = 60)!!.check
        assertEquals(fit.reviews(log), last.reviews)
        assertFalse(fit.isDue(log, last))
        assertEquals(last.reviews + config.refitAfterReviews, fit.nextFitAt(last))
    }

    @Test
    fun itemsPlacedByThePlacementTestAreNotFittedOn() {
        val log = SimulatedLog.of(items.take(10), lastDay = 60, seed = 4, truth = defaults)
        val placedFirst = log.first { it.item == "w-0" }
        val placed = log.map { if (it == placedFirst) it.copy(checkedBy = Calibration.PLACEMENT) else it }
        val w0Reviews = log.count { it.item == "w-0" } - 1
        assertEquals(fit.reviews(log) - w0Reviews, fit.reviews(placed))
    }
}
