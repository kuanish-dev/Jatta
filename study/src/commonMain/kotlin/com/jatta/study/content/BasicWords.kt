package com.jatta.study.content

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BasicWords(val source: String, val rank: Int, val forms: List<String>) {
    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun read(text: String): BasicWords = json.decodeFromString(text)
    }
}
