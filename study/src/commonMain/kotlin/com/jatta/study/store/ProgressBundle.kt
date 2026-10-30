package com.jatta.study.store

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ProgressBundle(
    val app: String = APP,
    val version: Int = VERSION,
    val exportedAtMs: Long,
    val state: SavedState,
    val answers: List<LogEntry>,
    val translations: List<TranslationEntry>,
) {
    companion object {
        const val APP = "jatta"
        const val VERSION = 1
        private val json = Json { ignoreUnknownKeys = true }

        fun write(bundle: ProgressBundle): String = json.encodeToString(bundle)

        fun read(text: String): ProgressBundle? = runCatching { json.decodeFromString<ProgressBundle>(text) }.getOrNull()
            ?.takeIf { it.app == APP && it.version in 1..VERSION }
    }
}

data class ImportSummary(val items: Int, val answers: Int, val translations: Int)
