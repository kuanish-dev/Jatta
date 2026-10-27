package com.jatta.study

import com.jatta.study.exercise.SpokenAnswer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SpokenAnswerTest {

    @Test
    fun anyRecognizedVariantThatSaysTheSampleCounts() {
        assertTrue(SpokenAnswer.matches(listOf("we need to approve our process", "we need to improve our process"), "We need to improve our process."))
        assertFalse(SpokenAnswer.matches(listOf("we need to approve our process"), "We need to improve our process."))
    }

    @Test
    fun digitsAndSpelledNumbersAreTheSame() {
        assertTrue(SpokenAnswer.matches(listOf("does the thirty bus come here"), "Does the 30 bus come here?"))
        assertTrue(SpokenAnswer.matches(listOf("we have a single for 50 dollars per night"), "We have a single for fifty dollars per night."))
        assertTrue(SpokenAnswer.matches(listOf("it is 125 years old"), "It is one hundred twenty-five years old."))
    }

    @Test
    fun contractionsCountAsTheLongForm() {
        assertTrue(SpokenAnswer.matches(listOf("I'm not ready"), "I am not ready."))
    }

    @Test
    fun theVariantClosestToTheSampleIsShown() {
        assertEquals(
            "we need to improve the process",
            SpokenAnswer.closest(listOf("we need to prove", "we need to improve the process"), "We need to improve our process."),
        )
    }
}
