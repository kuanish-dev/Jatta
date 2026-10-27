package com.jatta.study.chat

import com.jatta.study.Screen
import com.jatta.study.judge.FreeAnswerJudge
import com.jatta.study.judge.Judgment
import com.jatta.study.judge.WordDiff
import kotlinx.coroutines.withTimeoutOrNull

class ChatFlow(
    private val topic: ChatTopic,
    private val words: List<String>,
    private val partner: ChatPartner,
    private val judge: FreeAnswerJudge,
    private val timeoutMs: Long,
    private val turnsKept: Int,
    private val show: (Screen.Chat) -> Unit,
) {
    private val messages = mutableListOf<ChatMessage>()
    private var typing = false
    private var silent = false

    val busy: Boolean get() = typing || messages.lastOrNull()?.review == Review.Checking

    suspend fun open() = reply()

    suspend fun send(text: String) {
        if (busy || text.isBlank()) return
        val said = messages.lastOrNull { !it.fromLearner }?.text
        val index = messages.size
        messages += ChatMessage(fromLearner = true, text = text.trim(), review = Review.Checking)
        publish()
        val judgment = withTimeoutOrNull(timeoutMs) { judge.reviewChat(said, text.trim()) }
        messages[index] = messages[index].copy(review = reviewOf(judgment, text.trim()))
        publish()
        reply()
    }

    private suspend fun reply() {
        typing = true
        publish()
        val answer = withTimeoutOrNull(timeoutMs) { partner.reply(topic, words, messages.takeLast(turnsKept)) }
        typing = false
        silent = answer == null
        if (answer != null) messages += ChatMessage(fromLearner = false, text = answer)
        publish()
    }

    private fun reviewOf(judgment: Judgment?, text: String): Review = when {
        judgment == null -> Review.Unclear
        judgment.correct -> Review.Fine
        else -> Review.Fixed(WordDiff.parts(text, judgment.corrected!!))
    }

    private fun publish() = show(Screen.Chat(topic, messages.toList(), typing, silent, words))
}
