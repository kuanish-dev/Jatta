package com.jatta.engine

import com.jatta.engine.fsrs.Grade
import com.jatta.engine.session.Answer
import com.jatta.engine.session.Grading
import com.jatta.engine.session.Outcome
import com.jatta.engine.session.Session
import com.jatta.engine.session.Step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionTest {
    private val config = EngineConfig()
    private val median = 4_000L
    private val right = Answer(correct = true, responseMs = 4_000)
    private val wrong = Answer(correct = false, responseMs = 4_000)

    @Test
    fun rightAnswerFinishesTheItemWithItsGrade() {
        val session = Session(listOf("a", "b"), emptyList(), config)
        assertEquals(Step.Recall("a"), session.current)
        assertEquals(Outcome.Done(Grade.Good), session.answer(right, median))
        assertEquals(Step.Recall("b"), session.current)
    }

    @Test
    fun mistakeGivesAHintThenTheAnswerAndTheItemComesBackAtTheEnd() {
        val session = Session(listOf("a", "b"), emptyList(), config)
        assertEquals(Outcome.Hint(Grade.Again), session.answer(wrong, median))
        assertEquals(Step.Recall("a", withHint = true), session.current)
        assertEquals(Outcome.Reveal, session.answer(wrong, median))
        assertEquals(Step.Recall("b"), session.current)
        session.answer(right, median)
        assertEquals(Step.Recall("a"), session.current)
        assertEquals(Outcome.Done(firstGrade = null), session.answer(right, median))
        assertNull(session.current)
    }

    @Test
    fun rightAnswerWithTheHintStillNeedsAnUnaidedAnswerLater() {
        val session = Session(listOf("a", "b"), emptyList(), config)
        session.answer(wrong, median)
        assertEquals(Outcome.RetryLater, session.answer(right, median))
        assertEquals(Step.Recall("b"), session.current)
        session.answer(right, median)
        assertEquals(Step.Recall("a"), session.current)
    }

    @Test
    fun reviewsComeBeforeNewItemsAndNewItemsAreRecalledAfterAGap() {
        val session = Session(listOf("r"), listOf("n1", "n2", "n3", "n4", "n5"), config)
        val seen = mutableListOf<Step>()
        while (true) {
            val step = session.current ?: break
            seen += step
            when (step) {
                is Step.Present -> session.presented()
                is Step.Recall -> session.answer(right, median)
            }
        }
        assertEquals(
            listOf(
                Step.Recall("r"),
                Step.Present("n1"), Step.Present("n2"), Step.Present("n3"), Step.Present("n4"),
                Step.Recall("n1"), Step.Present("n5"),
                Step.Recall("n2"), Step.Recall("n3"), Step.Recall("n4"), Step.Recall("n5"),
            ),
            seen,
        )
    }

    @Test
    fun confidenceIsReadFromSpeed() {
        val grading = Grading(config)
        assertEquals(Grade.Again, grading.grade(wrong, median))
        assertEquals(Grade.Good, grading.grade(right, median))
        assertEquals(Grade.Hard, grading.grade(Answer(true, 7_300), median))
        assertEquals(Grade.Easy, grading.grade(Answer(true, 2_000), median))

        assertTrue(grading.isConfidentError(Answer(false, 2_000), median))
        assertFalse(grading.isConfidentError(Answer(false, 4_000), median))
        assertFalse(grading.isConfidentError(Answer(true, 2_000), median))
    }
}
