package com.jatta.study

import com.jatta.study.content.ItemFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffPart
import com.jatta.study.judge.ModelState
import com.jatta.study.placement.PlacementResult
import com.jatta.study.reading.Reading
import com.jatta.study.store.AiCheck

/** What the app shows. The UI only draws these and calls StudyController back. */
sealed interface Screen {
    data class Home(
        val courseTitle: String,
        val reviews: Int,
        val newItems: Int,
        val postponed: Int,
        val topics: List<TopicStatus>,
        val ai: AiStatus,
        val progress: CourseProgress,
        /** The placement test's vocabulary estimate, if the learner took it (М23). */
        val vocabulary: VocabularyEstimate?,
        /** A real text the learner can read now (М11), or null when none fits yet. */
        val reading: Reading?,
    ) : Screen

    /** М23: before the first lesson, the offer to find where the course should start. */
    data class PlacementIntro(val words: Int, val questions: Int) : Screen

    /** М23: do you know this word? Some of them don't exist. */
    data class PlacementWord(val word: String, val steps: Steps) : Screen

    /** М23: a grammar question of the placement test; [id] keeps consecutive questions distinct. */
    data class PlacementGrammar(val id: Int, val exercise: Exercise.Choice, val steps: Steps) : Screen

    data class PlacementDone(val result: PlacementResult) : Screen

    /** Reading a real text; words met in the course are pointed out (М11). */
    data class Read(val reading: Reading) : Screen

    /** A new item met for the first time; [topicIntro] is set for the first item of a topic (М10). */
    data class Present(
        val item: ItemFile,
        val topicTitle: String,
        val topicIntro: String?,
        val banner: Banner?,
        val steps: Steps,
    ) : Screen

    /**
     * [id] makes every shown question distinct, so the UI resets its input. [hint] is set on the second
     * try; [confidentError] when the wrong answer came fast, a mistake made with confidence (М13).
     */
    data class Question(
        val id: Int,
        val exercise: Exercise,
        val hint: String?,
        val banner: Banner?,
        val steps: Steps,
        val confidentError: Boolean = false,
    ) : Screen

    /** The on-device model is checking a free answer. */
    data class Checking(val steps: Steps) : Screen

    /** The model found a mistake: what changed, and the course's explanation (М12, М22). */
    data class Judged(val exercise: Exercise, val diff: List<DiffPart>, val note: String, val steps: Steps) : Screen

    /** The learner compares a free answer with the model answer and says whether it was right. */
    data class SelfCheck(val exercise: Exercise, val response: String?, val steps: Steps) : Screen

    /** Wrong after the hint: the answer and a short explanation (М12). */
    data class Reveal(val exercise: Exercise, val note: String, val steps: Steps) : Screen

    data class Rest(val kind: RestKind, val minutes: Int, val untilMs: Long) : Screen

    data class Done(val answered: Int, val rightFirstTime: Int, val endedEarly: Boolean) : Screen
}

/**
 * How far the session is. Every finished step counts as done, and an item that comes back adds a
 * step to the queue, so the bar only moves forward.
 */
data class Steps(val done: Int, val remaining: Int) {
    val fraction: Float get() = if (done + remaining == 0) 1f else done.toFloat() / (done + remaining)
}

/** М23: about [words] of the [total] most frequent English words are known. */
data class VocabularyEstimate(val words: Int, val total: Int)

/** М1, М2: how much of the course holds in memory now (recall tomorrow ≥ 85%), not merely how much was seen. */
data class CourseProgress(val held: Int, val met: Int, val total: Int)

/** The on-device model as the home screen shows it. */
data class AiStatus(
    val model: ModelState,
    /** Bytes downloaded while the model is downloading. */
    val downloadedBytes: Long?,
    /** Cases checked so far while the accuracy test runs. */
    val testedDone: Int?,
    val testTotal: Int,
    val lastTest: AiCheck?,
)

enum class Banner { Correct, CheckedByAi, CorrectWithHint }

enum class RestKind { Scheduled, Fatigue, Quiet }

enum class TopicState { Locked, Open, Mastered }

data class TopicStatus(val title: String, val state: TopicState, val held: Int, val total: Int)
