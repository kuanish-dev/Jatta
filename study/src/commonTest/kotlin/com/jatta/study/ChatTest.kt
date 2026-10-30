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
    fun thePartnersReplyIsPlainTextOfItsOwnTurn() {
        assertEquals("Hi! How was your day?", ChatPartner.tidy("You: Hi! How was your day? 😊\nFriend: fine\nYou: Good."))
        assertEquals("Plov sounds delicious! Do you play guitar often?", ChatPartner.tidy("**Plov** sounds delicious! 😋\n\nDo you play guitar often? 🎸"))
        assertNull(ChatPartner.tidy("  \n "))
    }

    @Test
    fun thePartnerHasACharacterTheTopicAndTheRealTurns() {
        val topic = ChatTopic("work", "Работа", "the learner's job")
        assertTrue("Today you talk about the learner's job." in ChatPartner.persona(topic))
        val turns = ChatPartner.turns(listOf(ChatMessage(false, "Hey! How is work?"), ChatMessage(true, "Busy")))
        assertEquals(listOf(true to "Hi!", false to "Hey! How is work?", true to "Busy"), turns.map { it.fromUser to it.text })
    }
}
