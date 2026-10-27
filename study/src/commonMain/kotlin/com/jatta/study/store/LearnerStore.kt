package com.jatta.study.store

import com.jatta.engine.fsrs.Memory
import com.jatta.engine.learner.ItemProgress
import com.jatta.engine.learner.Learner
import com.jatta.study.ThemeChoice
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
    val firstDay: Int? = null,
)

@Serializable
data class RecentAnswer(val rung: String, val correct: Boolean, val responseMs: Long)

@Serializable
data class AiCheck(
    val agreement: Double,
    val total: Int,
    val wrongAccepted: Int,
    val rightRejected: Int,
    val unusable: Int,
    val enabled: Boolean,
    val translationAgreement: Double? = null,
    val translationsEnabled: Boolean = false,
)

@Serializable
data class SavedPlacement(val estimatedWords: Int? = null, val totalWords: Int? = null, val skipped: Boolean = false)

@Serializable
data class FitCheck(
    val day: Int,
    val reviews: Int,
    val heldOut: Int,
    val currentLoss: Double,
    val fittedLoss: Double,
    val adopted: Boolean,
)

@Serializable
data class SessionStart(val day: Int, val minute: Int)

@Serializable
data class SavedState(
    val progress: List<SavedProgress> = emptyList(),
    val ladderShift: Int = 0,
    val recent: List<RecentAnswer> = emptyList(),
    val aiCheck: AiCheck? = null,
    val sessionStarts: List<SessionStart> = emptyList(),
    val readTexts: List<String> = emptyList(),
    val theme: ThemeChoice = ThemeChoice.System,
    val placement: SavedPlacement? = null,
    val fsrsParameters: List<Double>? = null,
    val fsrsFit: FitCheck? = null,
    val firstDaysRecorded: Boolean = false,
    val lastSummaryDay: Int? = null,
)

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
    val checkedBy: String? = null,
)

@Serializable
data class TranslationEntry(
    val atMs: Long,
    val day: Int,
    val sentence: Int,
    val response: String,
    val score: Double,
    val passed: Boolean,
    val responseMs: Long,
    val checkedBy: String,
)

fun SavedState.toLearner() = Learner(
    progress = progress.associate {
        it.item to ItemProgress(it.item, Memory(it.stability, it.difficulty), it.lastReviewDay, it.dueDay, it.firstDay)
    },
    ladderShift = ladderShift,
)

fun SavedState.withLearner(learner: Learner) = copy(
    progress = learner.progress.values
        .map { SavedProgress(it.itemId, it.memory.stability, it.memory.difficulty, it.lastReviewDay, it.dueDay, it.firstDay) }
        .sortedBy { it.item },
    ladderShift = learner.ladderShift,
    firstDaysRecorded = true,
)

fun SavedState.withFirstDays(firstDays: Map<String, Int>) = copy(
    progress = progress.map { it.copy(firstDay = it.firstDay ?: firstDays[it.item]) },
    firstDaysRecorded = true,
)

class LearnerStore(directory: String) {
    private val dir = Path(directory)
    private val statePath = Path(directory, "learner.json")
    private val tempPath = Path(directory, "learner.json.tmp")
    private val brokenPath = Path(directory, "learner.broken.json")
    private val logPath = Path(directory, "answers.jsonl")
    private val translationsPath = Path(directory, "translations.jsonl")
    private val json = Json { ignoreUnknownKeys = true }

    fun load(): SavedState {
        if (!SystemFileSystem.exists(statePath)) return SavedState()
        val text = SystemFileSystem.source(statePath).buffered().use { it.readString() }
        return runCatching { json.decodeFromString<SavedState>(text) }.getOrElse {
            SystemFileSystem.atomicMove(statePath, brokenPath)
            SavedState()
        }
    }

    fun save(state: SavedState) {
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(tempPath).buffered().use { it.writeString(json.encodeToString(state)) }
        SystemFileSystem.atomicMove(tempPath, statePath)
    }

    fun readLog(): List<LogEntry> = readLines(logPath).mapNotNull { runCatching { json.decodeFromString<LogEntry>(it) }.getOrNull() }

    fun log(entry: LogEntry) = append(logPath, json.encodeToString(entry))

    fun readTranslations(): List<TranslationEntry> =
        readLines(translationsPath).mapNotNull { runCatching { json.decodeFromString<TranslationEntry>(it) }.getOrNull() }

    fun logTranslation(entry: TranslationEntry) = append(translationsPath, json.encodeToString(entry))

    private fun readLines(path: Path): List<String> {
        if (!SystemFileSystem.exists(path)) return emptyList()
        val text = SystemFileSystem.source(path).buffered().use { it.readString() }
        return text.lines().filter { it.isNotBlank() }
    }

    private fun append(path: Path, line: String) {
        SystemFileSystem.createDirectories(dir)
        SystemFileSystem.sink(path, append = true).buffered().use { it.writeString(line + "\n") }
    }
}
