package com.jatta.engine

import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.FsrsOptimizer
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.fsrs.Review
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FsrsOptimizerTest {
    private val config = EngineConfig()
    private val optimizer = FsrsOptimizer(config)
    private val defaults = Fsrs.DEFAULT_PARAMETERS

    /** A learner who forgets new items about three times faster than the defaults expect. */
    private val fastForgetter = defaults.toMutableList().apply { this[2] = 0.7 }

    /**
     * Items met on day 0, reviewed when the default model says they are due, and recalled with the
     * probability [truth] gives, as the app would see a learner whose memory follows [truth].
     */
    private fun simulate(truth: List<Double>, items: Int, reviewsPerItem: Int, seed: Int): List<List<Review>> {
        val random = Random(seed)
        val app = Fsrs(config.desiredRetention, config.maximumIntervalDays)
        val learner = Fsrs(config.desiredRetention, config.maximumIntervalDays, truth)
        return List(items) { item ->
            var day = item / 20
            var believed = app.first(Grade.Good)
            var real = learner.first(Grade.Good)
            val history = mutableListOf(Review(day, Grade.Good))
            repeat(reviewsPerItem) {
                val elapsed = app.intervalDays(believed.stability)
                day += elapsed
                val grade = if (random.nextDouble() < learner.retrievability(real, elapsed)) Grade.Good else Grade.Again
                history += Review(day, grade)
                believed = app.next(believed, elapsed, grade)
                real = learner.next(real, elapsed, grade)
            }
            history
        }
    }

    @Test
    fun lossMatchesTheReferenceOptimizer() {
        val histories = FsrsOptimizerVectors.histories
        assertEquals(FsrsOptimizerVectors.DEFAULT_LOSS, optimizer.loss(defaults, histories), 1e-12)
        assertEquals(FsrsOptimizerVectors.OTHER_LOSS, optimizer.loss(FsrsOptimizerVectors.other, histories), 1e-12)
    }

    @Test
    fun aFastForgetterIsFoundInTheReviews() {
        val histories = simulate(fastForgetter, items = 400, reviewsPerItem = 4, seed = 1)
        val fitted = optimizer.fit(histories)!!
        val unseen = simulate(fastForgetter, items = 400, reviewsPerItem = 4, seed = 2)
        assertTrue(fitted[2] < defaults[2], "the first interval shrinks")
        assertTrue(optimizer.loss(fitted, unseen) < optimizer.loss(defaults, unseen), "new reviews are predicted better")
        fitted.forEachIndexed { i, w -> assertTrue(w in Fsrs.LOWER_BOUNDS[i]..Fsrs.UPPER_BOUNDS[i], "parameter $i in bounds") }
    }

    @Test
    fun fewerReviewsThanOneBatchFitNothing() {
        val histories = simulate(fastForgetter, items = 100, reviewsPerItem = 5, seed = 3)
        assertTrue(optimizer.reviewCount(histories) < config.optimizerBatchReviews)
        assertNull(optimizer.fit(histories))
    }

    @Test
    fun onlyReviewsOnTheChosenDaysCount() {
        val histories = simulate(defaults, items = 50, reviewsPerItem = 3, seed = 4)
        val days = optimizer.scoredDays(histories)
        assertEquals(150, days.size, "every review after the first meeting")
        val split = days.sorted()[100]
        assertEquals(days.size, optimizer.reviewCount(histories, Int.MIN_VALUE until split) + optimizer.reviewCount(histories, split..Int.MAX_VALUE))
        assertTrue(abs(optimizer.loss(defaults, histories, split..Int.MAX_VALUE) - optimizer.loss(defaults, histories)) > 0)
    }
}
