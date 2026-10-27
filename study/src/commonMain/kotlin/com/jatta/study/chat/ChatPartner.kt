package com.jatta.study.chat

import com.jatta.study.judge.OnDeviceModel

class ChatPartner(private val model: OnDeviceModel) {

    suspend fun reply(topic: ChatTopic, words: List<String>, lines: List<ChatMessage>): String? =
        model.generate(prompt(topic, words, lines))?.let(::clean)

    companion object {
        const val NAME = "Alex"

        fun prompt(topic: ChatTopic, words: List<String>, lines: List<ChatMessage>): String = buildString {
            appendLine("You are $NAME, a friendly native English speaker. You are chatting with a learner of English (level B1) so that they can practise.")
            appendLine("Topic: ${topic.en}.")
            appendLine("Rules: write one or two short sentences in simple everyday English, then ask one question. Never correct the learner's English, never explain grammar, never write in Russian.")
            if (words.isNotEmpty()) appendLine("If it fits, use one of these words: ${words.joinToString()}.")
            appendLine()
            if (lines.isEmpty()) {
                appendLine("Start the conversation: say hello and ask one question about the topic.")
            } else {
                appendLine("Conversation:")
                lines.forEach { appendLine((if (it.fromLearner) "Learner: " else "$NAME: ") + it.text) }
            }
            append("$NAME:")
        }

        fun clean(raw: String): String? = raw.trim()
            .removePrefix("$NAME:")
            .substringBefore("\nLearner")
            .substringBefore("Learner:")
            .lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
            .trim('"', '“', '”', ' ')
            .takeIf { it.isNotBlank() }
    }
}
