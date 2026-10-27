package com.jatta.study

import com.jatta.study.translate.Chrf
import com.jatta.study.translate.TranslationScore
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChrfTest {

    /** Values from a Python implementation of chrF++ written from Popović's definition. */
    @Test
    fun scoresMatchTheReferenceImplementation() {
        assertEquals(0.662441204628705, Chrf.score("I need to go to sleep", "I have to go to sleep."), 1e-12)
        assertEquals(0.802730212927797, Chrf.score("She finish the report on Monday", "She finished the report on Monday."), 1e-12)
        assertEquals(0.580663086913087, Chrf.score("sleep to go to have I", "I have to go to sleep."), 1e-12)
        assertEquals(0.536597739798348, Chrf.score("Мне пора спать", "Мне пора идти спать."), 1e-12)
        assertEquals(0.104836487479469, Chrf.score("Tom saved me.", "I'm the one who rescued Tom."), 1e-12)
        assertEquals(0.0, Chrf.score("cat", "dog"))
    }

    @Test
    fun caseQuotesPunctuationAndContractionsDoNotCount() {
        assertEquals(1.0, Chrf.score("i don’t know", "I do not know!"))
        assertEquals(1.0, Chrf.score("Я ни о чем не жалею", "Я ни о чём не жалею."))
    }

    @Test
    fun theClosestTranslationCountsAndANegationMustMatch() {
        val references = listOf("You have no need to hurry.", "You don't need to hurry.")
        val close = TranslationScore.of("You do not need to hurry", references, passScore = 0.8)
        assertEquals("You don't need to hurry.", close.closest)
        assertTrue(close.passed)

        val flipped = TranslationScore.of("Я сейчас не на дежурстве.", listOf("Я сейчас на службе.", "Я сейчас на дежурстве."), 0.8)
        assertTrue(flipped.score >= 0.8, "close by n-grams")
        assertFalse(flipped.passed, "but the meaning is turned around")
    }
}
