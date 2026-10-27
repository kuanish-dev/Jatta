package com.jatta.study

import com.jatta.study.exercise.AnswerCheck
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AnswerCheckTest {

    @Test
    fun caseAndPunctuationDoNotMatter() {
        assertTrue(AnswerCheck.matches("i worked late yesterday", listOf("I worked late yesterday.")))
        assertTrue(AnswerCheck.matches("  I worked   late yesterday!! ", listOf("I worked late yesterday.")))
    }

    @Test
    fun contractionsAndCurlyQuotesMatchTheLongForm() {
        assertTrue(AnswerCheck.matches("I did not see your message", listOf("I didn't see your message.")))
        assertTrue(AnswerCheck.matches("I’ve finished the report", listOf("I have finished the report.")))
        assertTrue(AnswerCheck.matches("I cannot come", listOf("I can't come.")))
        assertTrue(AnswerCheck.matches("They won't call", listOf("They will not call.")))
    }

    @Test
    fun russianIsKeptAndYoIsReadAsYe() {
        assertTrue(AnswerCheck.matches("я ни о чем не жалею", listOf("Я ни о чём не жалею.")))
        assertFalse(AnswerCheck.matches("Я обо всём жалею", listOf("Я ни о чём не жалею.")))
    }

    @Test
    fun differentWordsDoNotMatch() {
        assertFalse(AnswerCheck.matches("I work late yesterday", listOf("I worked late yesterday.")))
        assertFalse(AnswerCheck.matches("didn't saw", listOf("didn't see")))
    }
}
