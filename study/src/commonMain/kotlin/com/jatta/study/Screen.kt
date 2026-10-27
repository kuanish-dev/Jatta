package com.jatta.study

import com.jatta.study.calibration.CalibrationReport
import com.jatta.study.chat.ChatMessage
import com.jatta.study.chat.ChatTopic
import com.jatta.study.content.ItemFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffPart
import com.jatta.study.judge.ModelState
import com.jatta.study.placement.PlacementResult
import com.jatta.study.progress.DaySummary
import com.jatta.study.progress.ProgressReport
import com.jatta.study.progress.Streak
import com.jatta.study.reading.Reading
import com.jatta.study.store.AiCheck
import com.jatta.study.store.FitCheck
import com.jatta.study.translate.TranslationTask

sealed interface Screen {
    data class Home(
        val courseTitle: String,
        val reviews: Int,
        val newItems: Int,
        val postponed: Int,
        val topics: List<TopicStatus>,
        val ai: AiStatus,
        val progress: CourseProgress,
        val vocabulary: VocabularyEstimate?,
        val reading: Reading?,
        val streak: Streak,
        val chatReady: Boolean = false,
    ) : Screen

    data class DailySummary(val yesterday: DaySummary?, val restDay: Boolean, val streak: Streak) : Screen

    data class Progress(val report: ProgressReport) : Screen

    data class PlacementIntro(val words: Int, val questions: Int) : Screen

    data class PlacementWord(val word: String, val steps: Steps) : Screen

    data class PlacementGrammar(val id: Int, val exercise: Exercise.Choice, val steps: Steps) : Screen

    data class PlacementDone(val result: PlacementResult) : Screen

    data class Topic(
        val topic: TopicStatus,
        val intro: String,
        val reviews: Int,
        val early: Int,
        val newToday: Int,
        val newLater: Int,
        val missing: List<String>,
    ) : Screen

    data class Read(val reading: Reading) : Screen

    data class Present(
        val item: ItemFile,
        val topicTitle: String,
        val topicIntro: String?,
        val banner: Banner?,
        val steps: Steps,
    ) : Screen

    data class Question(
        val id: Int,
        val exercise: Exercise,
        val hint: String?,
        val banner: Banner?,
        val steps: Steps,
        val confidentError: Boolean = false,
    ) : Screen

    data class Checking(val steps: Steps) : Screen

    data class Judged(val exercise: Exercise, val diff: List<DiffPart>, val note: String, val steps: Steps) : Screen

    data class SelfCheck(val exercise: Exercise, val response: String?, val steps: Steps, val heard: List<DiffPart>? = null) : Screen

    data class Heard(val exercise: Exercise, val translation: String, val banner: Banner, val steps: Steps) : Screen

    data class Reveal(val exercise: Exercise, val note: String, val steps: Steps) : Screen

    data class Rest(val kind: RestKind, val minutes: Int, val untilMs: Long) : Screen

    data class Translate(val id: Int, val task: TranslationTask, val steps: Steps) : Screen

    data class Translated(
        val task: TranslationTask,
        val response: String,
        val score: Double,
        val target: String,
        val diff: List<DiffPart>,
        val outcome: TranslationOutcome,
        val steps: Steps,
    ) : Screen

    data class Done(
        val answered: Int,
        val rightFirstTime: Int,
        val endedEarly: Boolean,
        val translated: Int = 0,
        val translatedRight: Int = 0,
        val streak: Int = 0,
        val streakGrew: Boolean = false,
    ) : Screen

    data class Calibration(val report: CalibrationReport, val fit: FitStatus) : Screen

    data class ChatTopics(val topics: List<ChatTopic>) : Screen

    data class Chat(
        val topic: ChatTopic,
        val messages: List<ChatMessage>,
        val typing: Boolean,
        val silent: Boolean,
        val words: List<String>,
    ) : Screen
}

val Screen.canGoBack: Boolean get() = this !is Screen.Home && this !is Screen.PlacementIntro

data class Steps(val done: Int, val remaining: Int) {
    val fraction: Float get() = if (done + remaining == 0) 1f else done.toFloat() / (done + remaining)
}

data class VocabularyEstimate(val words: Int, val total: Int)

data class CourseProgress(val held: Int, val met: Int, val total: Int)

data class AiStatus(
    val model: ModelState,
    val modelName: String?,
    val downloadTotalBytes: Long?,
    val downloadedBytes: Long?,
    val downloadFailed: Boolean,
    val testedDone: Int?,
    val testTotal: Int,
    val lastTest: AiCheck?,
)

enum class Banner { Correct, CheckedByAi, CorrectWithHint }

enum class TranslationOutcome { Close, RightByAi, WrongByAi, LearnerDecides }

enum class RestKind { Scheduled, Fatigue, Quiet }

enum class TopicState { Locked, Open, Mastered }

data class TopicStatus(val id: String, val title: String, val state: TopicState, val held: Int, val total: Int)

data class FitStatus(val personal: Boolean, val reviews: Int, val nextFitAt: Int, val last: FitCheck?)
