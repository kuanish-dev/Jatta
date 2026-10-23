package com.jatta.study.store

import com.jatta.engine.fsrs.Memory
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SavedProgress(
    val item: String,
    val stability: Double,
    val difficulty: Double,
    val lastReviewDay: Int,
    val dueDay: Int,
)

/** One recent first answer, for the learner's own speed medians and fatigue baseline. */
@Serializable
data class RecentAnswer(val rung: String, val correct: Boolean, val responseMs: Long)

/** Result of the last accuracy test of the on-device model; [enabled] once it agrees often enough (М21). */
@Serializable
data class AiCheck(
    val agreement: Double,
    val total: Int,
    val wrongAccepted: Int,
    val rightRejected: Int,
    val unusable: Int,
    val enabled: Boolean,
)

@Serializable
data class SavedState(
    val progress: List<SavedProgress> = emptyList(),
    val ladderShift: Int = 0,
    val recent: List<RecentAnswer> = emptyList(),
    val aiCheck: AiCheck? = null,
)

/** Every answer as it happened, kept to fit the memory model to this learner later. */
@Serializable
data class LogEntry(
    val atMs: Long,
    val day: Int,
    val item: String,
    val rung: String,
    val correct: Boolean,
    val responseMs: Long,
    val grade: String?,
    val withHint: Boolean,
    /** Who decided right or wrong: code, self, ai, or ai-disputed when the learner overruled the model. */
    val checkedBy: String? = null,
)

fun SavedState.toLearner() = Learner(
    progress = progress.associate {
        it.item to ItemProgress(it.item, Memory(it.stability, it.difficulty), it.lastReviewDay, it.dueDay)
    },
    ladderShift = ladderShift,
)

fun Learner.toSaved(recent: List<RecentAnswer>, aiCheck: AiCheck?) = SavedState(
    progress = progress.values
        .map { SavedProgress(it.itemId, it.memory.stability, it.memory.difficulty, it.lastReviewDay, it.dueDay) }
        .sortedBy { it.item },
    ladderShift = ladderShift,
    recent = recent,
    aiCheck = aiCheck,
)

/** Keeps the learner on the device: a JSON snapshot written atomically, plus an append-only answer log. */
class LearnerStore(directory: String) {
    private val dir = Path(directory)
    private val statePath = Path(directory, "learner.json")
    private val tempPath = Path(directory, "learner.json.tmp")
    private val brokenPath = Path(directory, "learner.broken.json")
    private val logPath = Path(directory, "answers.jsonl")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SavedState {
        if (!SystemFileSystem.exists(statePath)) return SavedState()
        val text = SystemFileSystem.source(statePath).buffered().use { it.readString() }
        return runCatching { json.decodeFromString<SavedState>(text) }.getOrElse {
            // Keep the unreadable file for inspection instead of failing on every launch.
            SystemFileSystem.atomicMove(statePath, brokenPath)
            SavedState()
        }
    }

    fun save(state: SavedState) {
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(tempPath).buffered().use { it.writeString(json.encodeToString(state)) }
        SystemFileSystem.atomicMove(tempPath, statePath)
    }

    fun log(entry: LogEntry) {
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(logPath, append = true).buffered().use { it.writeString(json.encodeToString(entry) + "\n") }
    }
}
