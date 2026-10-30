package com.jatta.study.judge

enum class ModelState { Unavailable, Downloadable, Downloading, Ready }

data class ChatTurn(val fromUser: Boolean, val text: String)

interface OnDeviceModel {
    val name: String

    val downloadBytes: Long?

    suspend fun state(): ModelState

    suspend fun download(onProgress: (Long) -> Unit): Boolean

    suspend fun generate(prompt: String): String?

    suspend fun chat(system: String, turns: List<ChatTurn>): String?
}

fun transcript(system: String, turns: List<ChatTurn>): String = buildString {
    appendLine(system)
    appendLine()
    turns.forEach { appendLine((if (it.fromUser) "Friend: " else "You: ") + it.text) }
    append("You:")
}
