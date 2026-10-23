package com.langengine.study

import com.langengine.study.content.ItemFile
import com.langengine.study.exercise.Exercise

/** What the app shows. The UI only draws these and calls StudyController back. */
sealed interface Screen {
    data class Home(
        val courseTitle: String,
        val reviews: Int,
        val newItems: Int,
        val postponed: Int,
        val topics: List<TopicStatus>,
    ) : Screen

    /** A new item met for the first time; [topicIntro] is set for the first item of a topic (М10). */
    data class Present(
        val item: ItemFile,
        val topicTitle: String,
        val topicIntro: String?,
        val banner: Banner?,
        val remaining: Int,
    ) : Screen

    /** [id] makes every shown question distinct, so the UI resets its input. [hint] is set on the second try. */
    data class Question(
        val id: Int,
        val exercise: Exercise,
        val hint: String?,
        val banner: Banner?,
        val remaining: Int,
    ) : Screen

    /** The learner compares a free answer with the model answer and says whether it was right. */
    data class SelfCheck(val exercise: Exercise, val response: String?, val remaining: Int) : Screen

    /** Wrong after the hint: the answer and a short explanation (М12). */
    data class Reveal(val exercise: Exercise, val note: String, val remaining: Int) : Screen

    data class Rest(val kind: RestKind, val minutes: Int, val untilMs: Long) : Screen

    data class Done(val answered: Int, val rightFirstTime: Int, val endedEarly: Boolean) : Screen
}

enum class Banner { Correct, CorrectWithHint }

enum class RestKind { Scheduled, Fatigue, Quiet }

enum class TopicState { Locked, Open, Mastered }

data class TopicStatus(val title: String, val state: TopicState, val held: Int, val total: Int)
