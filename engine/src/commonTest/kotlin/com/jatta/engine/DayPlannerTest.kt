package com.jatta.engine

import com.jatta.engine.content.Course
import com.jatta.engine.content.Topic
import com.jatta.engine.learner.Learner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class DayPlannerTest {
    private val today = 100
    private val engine = Engine(Fixture.course)

    @Test
    fun freshLearnerGetsOnlyTheFirstTopicMostFrequentWordsFirst() {
        val plan = engine.planDay(Learner(), today)
        assertEquals(emptyList(), plan.reviews)
        assertEquals(Fixture.basics, plan.newItems)
    }

    @Test
    fun topicsOpenOnlyWhenTheirBaseHolds() {
        val nineHeld = Learner(Fixture.basics.take(9).associateWith { Fixture.held(it, today) })
        assertEquals(
            listOf("w1", "ps1", "ps2", "ps3", "ps4", "pc1", "pc2", "pc3", "pc4"),
            engine.planDay(nineHeld, today).newItems,
        )

        val eightHeld = Learner(Fixture.basics.take(8).associateWith { Fixture.held(it, today) })
        assertEquals(listOf("w2", "w1"), engine.planDay(eightHeld, today).newItems)
    }

    @Test
    fun newItemsAreLimitedPerDay() {
        val small = Engine(Fixture.course, EngineConfig(newItemsPerDay = 3))
        assertEquals(listOf("w10", "w9", "w8"), small.planDay(Learner(), today).newItems)
    }

    @Test
    fun mostForgottenReviewsComeFirst() {
        val learner = Learner(
            mapOf(
                "w10" to Fixture.due("w10", today, daysAgo = 3),
                "w9" to Fixture.due("w9", today, daysAgo = 20),
                "w8" to Fixture.due("w8", today, daysAgo = 8),
            ),
        )
        assertEquals(listOf("w9", "w8", "w10"), engine.planDay(learner, today).reviews)
    }

    @Test
    fun backlogOverTheLimitPostponesReviewsAndStopsNewItems() {
        val strict = Engine(Fixture.course, EngineConfig(dailyReviewNorm = 4))
        val learner = Learner(Fixture.basics.take(8).associateWith { Fixture.due(it, today, daysAgo = 5) })
        val plan = strict.planDay(learner, today)
        assertEquals(6, plan.reviews.size)
        assertEquals(2, plan.postponedReviews)
        assertEquals(emptyList(), plan.newItems)
    }

    @Test
    fun confusableTopicsAreInterleaved() {
        val progress = Fixture.basics.associateWith { Fixture.held(it, today) } +
            listOf("ps1", "ps2", "ps3", "pc1", "pc2", "pc3").associateWith { Fixture.due(it, today, daysAgo = 5) }
        val plan = engine.planDay(Learner(progress), today)
        assertEquals(listOf("pc1", "ps1", "pc2", "ps2", "pc3", "ps3"), plan.reviews)
    }

    @Test
    fun learnedBasicsOpenGrammarTheNextDay() {
        var learner = Learner()
        Fixture.basics.forEach { learner = engine.recordGrade(learner, it, com.jatta.engine.fsrs.Grade.Good, today) }
        val tomorrow = engine.planDay(learner, today + 1)
        assertEquals(emptyList(), tomorrow.reviews)
        assertTrue("ps1" in tomorrow.newItems && "pc1" in tomorrow.newItems)
        assertTrue(tomorrow.newItems.none { it.startsWith("past") })
        assertEquals(Fixture.basics.toSet(), engine.planDay(learner, today + 2).reviews.toSet())
    }

    @Test
    fun courseRejectsAPrerequisiteThatComesLater() {
        assertFailsWith<IllegalArgumentException> {
            Course(listOf(Topic("b", "B", requires = setOf("a")), Topic("a", "A")), emptyList())
        }
    }
}
