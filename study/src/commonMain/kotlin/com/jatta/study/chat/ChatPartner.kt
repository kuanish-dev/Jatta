package com.jatta.study.chat

import com.jatta.study.judge.ChatTurn
import com.jatta.study.judge.OnDeviceModel

class ChatPartner(private val model: OnDeviceModel) {

    suspend fun reply(topic: ChatTopic, lines: List<ChatMessage>): String? = model.chat(persona(topic), turns(lines))?.let(::tidy)

    companion object {
        const val NAME = "Alex"
        private val EMOJI = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{FE0F}\\x{200D}]")
        private val SPEAKER = Regex("^(You|$NAME|Friend)\\s*:\\s*")

        fun persona(topic: ChatTopic): String =
            "You are $NAME, a 34-year-old nurse from Manchester. You love cycling, cooking and old films. " +
                "You are texting with a friend who is learning English (level B1). Today you talk about ${topic.en}. " +
                "Write like a friend in a chat: one to three short sentences in simple English, plain text, under 25 words. " +
                "React to what your friend says, share a little about your own life, and sometimes ask a question."

        fun turns(lines: List<ChatMessage>): List<ChatTurn> =
            listOf(ChatTurn(fromUser = true, text = "Hi!")) + lines.map { ChatTurn(it.fromLearner, it.text) }

        fun tidy(raw: String): String? = raw.trim()
            .replace(SPEAKER, "")
            .substringBefore("\nFriend")
            .replace(EMOJI, "")
            .replace("*", "")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .replace(Regex(" {2,}"), " ")
            .trim('"', '“', '”', ' ')
            .takeIf { it.isNotBlank() }
    }
}
