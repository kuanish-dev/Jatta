package com.jatta.study

import com.jatta.study.chat.Change
import com.jatta.study.chat.ChatMessage
import com.jatta.study.chat.ChatPartner
import com.jatta.study.chat.ChatTopic
import com.jatta.study.chat.Review
import com.jatta.study.chat.changesOf
import com.jatta.study.judge.FreeAnswerJudge
import com.jatta.study.judge.WordDiff
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatTest {

    @Test
    fun aCorrectionIsListedAsWrongToRightChanges() {
        val diff = WordDiff.parts("Yesterday I go to office and buyed a coffee", "Yesterday I went to the office and bought a coffee")
        assertEquals(listOf(Change("go", "went"), Change(null, "the"), Change("buyed", "bought")), changesOf(diff))
    }

    @Test
    fun theCorrectedMessageKeepsTheLearnersOwnWriting() {
        val fixed = Review.Fixed(WordDiff.parts("Yes, I like it, but my boss are strict.", "yes i like it but my boss is strict"))
        assertEquals("Yes, I like it, but my boss is strict.", fixed.corrected)
    }

    @Test
    fun aPunctuationOnlyFixIsNotAMistake() = runTest {
        val judge = FreeAnswerJudge(ReplyingModel { "VERDICT: WRONG\nFIXED: I work in a bank. I am a developer." }, StudyConfig())
        assertEquals(true, judge.reviewChat("What do you do?", "I work in a bank, I am a developer")?.correct)
    }

    @Test
    fun thePartnersReplyIsCutToItsOwnTurn() {
        assertEquals("Hi! How was your day?", ChatPartner.clean("Alex: Hi! How was your day?\nLearner: fine\nAlex: Good."))
        assertEquals("Nice. Where did you go?", ChatPartner.clean("\"Nice.\nWhere did you go?\""))
        assertNull(ChatPartner.clean("  \n "))
    }

    @Test
    fun thePartnerSeesTheTopicTheWordsAndTheTalk() {
        val topic = ChatTopic("work", "Работа", "the learner's job")
        val prompt = ChatPartner.prompt(topic, listOf("deadline"), listOf(ChatMessage(false, "Hi!"), ChatMessage(true, "Hello")))
        assertTrue("Topic: the learner's job." in prompt)
        assertTrue("deadline" in prompt)
        assertTrue(prompt.endsWith("Alex: Hi!\nLearner: Hello\nAlex:"))
    }
}
