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
    /** Learning day the item was first studied; null when the placement test found it known (М18, М23). */
    val firstDay: Int? = null,
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

/** The placement test's outcome; [skipped] when the learner chose to start from the beginning (М23). */
@Serializable
data class SavedPlacement(val estimatedWords: Int? = null, val totalWords: Int? = null, val skipped: Boolean = false)

/**
 * The last fit of the memory model to the learner (М2): how many reviews it had, how many of the latest
 * were kept out to test it, the log loss on those with the parameters in use and with the fitted ones,
 * and whether the fitted ones were adopted.
 */
@Serializable
data class FitCheck(
    val day: Int,
    val reviews: Int,
    val heldOut: Int,
    val currentLoss: Double,
    val fittedLoss: Double,
    val adopted: Boolean,
)

/** When a session started: the learning day and the local clock time in minutes (М19, М20). */
@Serializable
data class SessionStart(val day: Int, val minute: Int)

@Serializable
data class SavedState(
    val progress: List<SavedProgress> = emptyList(),
    val ladderShift: Int = 0,
    val recent: List<RecentAnswer> = emptyList(),
    val aiCheck: AiCheck? = null,
    val sessionStarts: List<SessionStart> = emptyList(),
    /** Reading texts already read (М11). */
    val readTexts: List<String> = emptyList(),
    val theme: ThemeChoice = ThemeChoice.System,
    val placement: SavedPlacement? = null,
    /** М2: FSRS parameters fitted to this learner; null while the defaults are in use. */
    val fsrsParameters: List<Double>? = null,
    val fsrsFit: FitCheck? = null,
    /** States saved before items kept their first study day get those days once from the answer log. */
    val firstDaysRecorded: Boolean = false,
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
    /** Who decided right or wrong: code, self, ai, ai-disputed when the learner overruled the model, or placement. */
    val checkedBy: String? = null,
)

/**
 * A sentence translated at the end of a session (М25). Kept apart from the answer log: that log is what
 * the memory model is checked and fitted on, and translations are extra practice, not scheduled reviews.
 */
@Serializable
data class TranslationEntry(
    val atMs: Long,
    val day: Int,
    val sentence: Int,
    /** en-ru or ru-en. */
    val direction: String,
    val response: String,
    /** chrF++ against the closest human translation, 0–1. */
    val score: Double,
    val passed: Boolean,
    val responseMs: Long,
    /** code when close enough to a human translation, otherwise ai, ai-disputed or self. */
    val checkedBy: String,
)

fun SavedState.toLearner() = Learner(
    progress = progress.associate {
        it.item to ItemProgress(it.item, Memory(it.stability, it.difficulty), it.lastReviewDay, it.dueDay, it.firstDay)
    },
    ladderShift = ladderShift,
)

/** The saved state with the learner's memory replaced by [learner]; everything else kept. */
fun SavedState.withLearner(learner: Learner) = copy(
    progress = learner.progress.values
        .map { SavedProgress(it.itemId, it.memory.stability, it.memory.difficulty, it.lastReviewDay, it.dueDay, it.firstDay) }
        .sortedBy { it.item },
    ladderShift = learner.ladderShift,
    firstDaysRecorded = true,
)

/** An older state with each item's first study day taken from [firstDays], the answer log's record. */
fun SavedState.withFirstDays(firstDays: Map<String, Int>) = copy(
    progress = progress.map { it.copy(firstDay = it.firstDay ?: firstDays[it.item]) },
    firstDaysRecorded = true,
)

/** Keeps the learner on the device: a JSON snapshot written atomically, plus append-only logs of answers and translations. */
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

    /** The answer log, oldest first; a line that can't be read is skipped. */
    fun readLog(): List<LogEntry> = readLines(logPath).mapNotNull { runCatching { json.decodeFromString<LogEntry>(it) }.getOrNull() }

    fun log(entry: LogEntry) = append(logPath, json.encodeToString(entry))

    /** The translations log (М25), oldest first. */
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
