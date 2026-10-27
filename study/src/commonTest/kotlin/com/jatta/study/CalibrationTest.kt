package com.jatta.study

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Fsrs
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.memory.MemoryModel
import com.jatta.study.calibration.Calibration
import com.jatta.study.calibration.CalibrationVerdict
import com.jatta.study.store.LogEntry
import kotlin.math.abs
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CalibrationTest {
    private val config = EngineConfig()
    private val memory = MemoryModel(Fsrs(config.desiredRetention, config.maximumIntervalDays))
    private val studyConfig = StudyConfig()

    private fun entry(item: String, day: Int, grade: Grade, checkedBy: String = "code") =
        LogEntry(day * 86_400_000L, day, item, "Recognize", grade != Grade.Again, 3_000, grade.name, false, checkedBy)

    /**
     * Learners who meet each item on day 0 and then review it on its due days, recalling with the
     * model's prediction shifted by [shift]. Returns the log and the number of reviews with a prediction.
     */
    private fun simulate(items: Int, reviewsPerItem: Int, shift: Double): Pair<List<LogEntry>, Int> {
        val random = Random(7)
        val log = mutableListOf<LogEntry>()
        var scored = 0
        repeat(items) { i ->
            val item = "w-$i"
            var progress: ItemProgress = memory.review(null, item, Grade.Good, 0)
            log += entry(item, 0, Grade.Good)
            repeat(reviewsPerItem) {
                val day = progress.dueDay
                val recalled = random.nextDouble() < memory.recallOn(progress, day) + shift
                val grade = if (recalled) Grade.Good else Grade.Again
                log += entry(item, day, grade)
                progress = memory.review(progress, item, grade, day)
                scored++
            }
        }
        return log.sortedBy { it.atMs } to scored
    }

    @Test
    fun aLearnerWhoRecallsAsPredictedFindsTheModelAccurate() {
        val (log, scored) = simulate(items = 150, reviewsPerItem = 3, shift = 0.0)
        val report = Calibration.of(log, memory, studyConfig)
        assertEquals(scored, report.reviews, "first meetings have no prediction")
        assertEquals(CalibrationVerdict.Accurate, report.verdict)
        assertTrue(abs(report.predicted - report.actual) < 0.03)
        assertTrue(report.predicted in report.low..report.high)
        assertEquals(report.reviews, report.bins.sumOf { it.count })
    }

    @Test
    fun aLearnerWhoForgetsMoreThanPredictedIsCaught() {
        val (log, _) = simulate(items = 150, reviewsPerItem = 3, shift = -0.15)
        assertEquals(CalibrationVerdict.Overestimates, Calibration.of(log, memory, studyConfig).verdict)
    }

    @Test
    fun aLearnerWhoRemembersBetterThanPredictedIsCaught() {
        val (log, _) = simulate(items = 150, reviewsPerItem = 3, shift = 0.08)
        assertEquals(CalibrationVerdict.Underestimates, Calibration.of(log, memory, studyConfig).verdict)
    }

    @Test
    fun fewReviewsGiveNoVerdict() {
        val (log, scored) = simulate(items = 5, reviewsPerItem = 2, shift = -0.5)
        val report = Calibration.of(log, memory, studyConfig)
        assertTrue(scored < studyConfig.minReviewsForCalibration)
        assertEquals(CalibrationVerdict.TooFewReviews, report.verdict)
    }

    @Test
    fun placedItemsAreReplayedButNotScoredAndLaterAnswersOfADayAreIgnored() {
        val log = listOf(
            entry("w-a", 0, Grade.Good, checkedBy = Calibration.PLACEMENT),
            // a later answer of the same day carries no grade and is not a review
            LogEntry(1_000, 0, "w-a", "Recognize", false, 3_000, null, false, "code"),
            entry("w-a", 2, Grade.Again),
            // the topic gone through again the same day (М26) changes memory but predicts nothing
            entry("w-a", 2, Grade.Good),
        )
        val report = Calibration.of(log, memory, studyConfig)
        assertEquals(1, report.reviews)
        assertEquals(memory.recallOn(memory.review(null, "w-a", Grade.Good, 0), 2), report.predicted)
        assertEquals(0.0, report.actual)
    }
}
