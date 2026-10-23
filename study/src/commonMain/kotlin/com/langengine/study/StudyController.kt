package com.langengine.study

import com.langengine.engine.Engine
import com.langengine.engine.EngineConfig
import com.langengine.engine.learningDay
import com.langengine.engine.session.Advice
import com.langengine.engine.session.Answer
import com.langengine.engine.session.Baseline
import com.langengine.engine.session.BreakReason
import com.langengine.engine.session.Outcome
import com.langengine.engine.session.RestCoach
import com.langengine.engine.session.Rung
import com.langengine.engine.session.Session
import com.langengine.engine.session.Step
import com.langengine.study.content.StudyCourse
import com.langengine.study.exercise.AnswerCheck
import com.langengine.study.exercise.Exercise
import com.langengine.study.exercise.ExerciseFactory
import com.langengine.study.store.LearnerStore
import com.langengine.study.store.LogEntry
import com.langengine.study.store.RecentAnswer
import com.langengine.study.store.toLearner
import com.langengine.study.store.toSaved
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val MS_PER_MINUTE = 60_000L

/**
 * Runs the app around the engine: plans the day, walks the session queue, turns steps into exercises,
 * saves every graded answer at once, and follows the rest coach. Platform code supplies the clock,
 * the time zone and the storage directory.
 */
class StudyController(
    private val content: StudyCourse,
    private val store: LearnerStore,
    private val nowMs: () -> Long,
    private val utcOffsetMinutes: () -> Int,
    private val engineConfig: EngineConfig = EngineConfig(),
    private val config: StudyConfig = StudyConfig(),
) {
    private val engine = Engine(content.course, engineConfig)
    private val exercises = ExerciseFactory(content)

    private val saved = store.load()
    private var learner = saved.toLearner()
    private var recent = saved.recent

    private var today = 0
    private var session: Session? = null
    private var coach: RestCoach? = null
    private var exercise: Exercise? = null
    private var shownAtMs = 0L
    private var pendingResponseMs = 0L
    private var pendingAdvice: Advice = Advice.Continue
    private var questionCounter = 0
    private val firstAnswers = mutableListOf<Boolean>()
    private var endedEarly = false

    private val state = MutableStateFlow<Screen>(home())
    val screen: StateFlow<Screen> = state.asStateFlow()

    fun openHome() {
        session = null
        coach = null
        exercise = null
        state.value = home()
    }

    fun startSession() {
        today = currentDay()
        session = engine.startSession(engine.planDay(learner, today))
        coach = engine.restCoach(baseline(), nowMs())
        firstAnswers.clear()
        pendingAdvice = Advice.Continue
        endedEarly = false
        showCurrent(banner = null)
    }

    /** The learner has read the new item. */
    fun presented() {
        session().presented()
        showCurrent(banner = null)
    }

    fun choose(index: Int) {
        val choice = exercise as Exercise.Choice
        answer(index == choice.correctIndex, elapsedMs())
    }

    fun submitText(text: String) {
        val responseMs = elapsedMs()
        when (val current = exercise) {
            is Exercise.Typed -> when {
                AnswerCheck.matches(text, current.accepted) -> answer(true, responseMs)
                current.openEnded -> selfCheck(text, responseMs)
                else -> answer(false, responseMs)
            }
            is Exercise.Free -> selfCheck(text, responseMs)
            else -> error("No typed answer expected for $current")
        }
    }

    /** The learner has said the sentence aloud and wants to compare. */
    fun showSpokenAnswer() = selfCheck(response = null, responseMs = elapsedMs())

    fun judgeSelf(correct: Boolean) = answer(correct, pendingResponseMs)

    fun continueAfterReveal() = advance(banner = null)

    fun finishRest() {
        val rest = state.value as Screen.Rest
        if (rest.kind == RestKind.Quiet) {
            showDone()
        } else {
            coach().onBreakFinished(nowMs())
            showCurrent(banner = null)
        }
    }

    /** Leave in the middle: everything answered so far is already saved. */
    fun stopSession() = openHome()

    private fun answer(correct: Boolean, responseMs: Long) {
        val current = exercise ?: error("No exercise on screen")
        val step = session().current as Step.Recall
        val outcome = session().answer(Answer(correct, responseMs), medianMs(current.rung))
        outcome.firstGrade?.let { grade ->
            learner = engine.recordGrade(learner, step.itemId, grade, today)
            firstAnswers += correct
            recent = (recent + RecentAnswer(current.rung.name, correct, responseMs)).takeLast(config.recentAnswersKept)
            store.save(learner.toSaved(recent))
        }
        store.log(
            LogEntry(nowMs(), today, step.itemId, current.rung.name, correct, responseMs, outcome.firstGrade?.name, step.withHint),
        )
        pendingAdvice = stronger(pendingAdvice, coach().onAnswer(Answer(correct, responseMs), nowMs()))

        when (outcome) {
            is Outcome.Done -> advance(Banner.Correct)
            is Outcome.Hint -> {
                shownAtMs = nowMs()
                state.value = Screen.Question(++questionCounter, current, hint = current.hint, banner = null, remaining = remaining())
            }
            Outcome.RetryLater -> advance(Banner.CorrectWithHint)
            Outcome.Reveal -> state.value = Screen.Reveal(current, note = itemNote(current.itemId), remaining = remaining())
        }
    }

    private fun selfCheck(response: String?, responseMs: Long) {
        pendingResponseMs = responseMs
        state.value = Screen.SelfCheck(exercise!!, response, remaining())
    }

    /** Next step, unless the rest coach asked for a break or the end (М14, М16). */
    private fun advance(banner: Banner?) {
        val advice = pendingAdvice
        pendingAdvice = Advice.Continue
        when (advice) {
            Advice.End -> {
                endedEarly = true
                finishSession()
            }
            is Advice.Break -> {
                val kind = if (advice.reason == BreakReason.Fatigue) RestKind.Fatigue else RestKind.Scheduled
                state.value = Screen.Rest(kind, advice.minutes, nowMs() + advice.minutes * MS_PER_MINUTE)
            }
            else -> showCurrent(banner)
        }
    }

    private fun showCurrent(banner: Banner?) {
        when (val step = session().current) {
            null -> finishSession()
            is Step.Present -> {
                val topic = content.topicOf(step.itemId)
                val firstOfTopic = content.course.itemsOf(topic.id).first().id == step.itemId
                state.value = Screen.Present(
                    item = content.items.getValue(step.itemId),
                    topicTitle = topic.title,
                    topicIntro = topic.intro.takeIf { firstOfTopic && it.isNotBlank() },
                    banner = banner,
                    remaining = remaining(),
                )
            }
            is Step.Recall -> {
                val rung = engine.ladder.rungFor(learner.progress[step.itemId]?.memory, learner.ladderShift)
                val built = exercises.build(step.itemId, rung, seed = step.itemId.hashCode() * 31 + today + questionCounter)
                exercise = built
                shownAtMs = nowMs()
                state.value = Screen.Question(++questionCounter, built, hint = null, banner = banner, remaining = remaining())
            }
        }
    }

    private fun finishSession() {
        // М6: one step harder or easier by this session's first answers.
        if (firstAnswers.size >= config.minAnswersForLadderShift) {
            val accuracy = firstAnswers.count { it }.toDouble() / firstAnswers.size
            learner = learner.copy(ladderShift = engine.ladder.adjustShift(learner.ladderShift, accuracy))
            store.save(learner.toSaved(recent))
        }
        val after = coach().afterSession(session())
        if (after is Advice.QuietRest) {
            state.value = Screen.Rest(RestKind.Quiet, after.minutes, nowMs() + after.minutes * MS_PER_MINUTE)
        } else {
            showDone()
        }
    }

    private fun showDone() {
        state.value = Screen.Done(firstAnswers.size, firstAnswers.count { it }, endedEarly)
    }

    private fun home(): Screen.Home {
        today = currentDay()
        val plan = engine.planDay(learner, today)
        val topics = content.course.topics.map { topic ->
            val items = content.course.itemsOf(topic.id)
            val state = when {
                !engine.gate.isOpen(topic, learner, today) -> TopicState.Locked
                engine.gate.isMastered(topic.id, learner, today) -> TopicState.Mastered
                else -> TopicState.Open
            }
            TopicStatus(topic.title, state, held = items.count { engine.gate.holds(learner.progress[it.id], today) }, total = items.size)
        }
        return Screen.Home(content.title, plan.reviews.size, plan.newItems.size, plan.postponedReviews, topics)
    }

    /** The learner's usual accuracy and speed once there is enough history (М16). */
    private fun baseline(): Baseline {
        if (recent.size < config.minAnswersForBaseline) return Baseline.Default
        return Baseline(
            accuracy = recent.count { it.correct }.toDouble() / recent.size,
            medianResponseMs = median(recent.map { it.responseMs }),
        )
    }

    /** The learner's usual speed on this rung, for telling fast from slow answers (М2, М13). */
    private fun medianMs(rung: Rung): Long {
        val times = recent.filter { it.rung == rung.name }.map { it.responseMs }
        return if (times.size >= config.minAnswersForRungMedian) median(times) else config.defaultMedianMs.getValue(rung)
    }

    private fun itemNote(itemId: String): String {
        val item = content.items.getValue(itemId)
        return item.note.ifBlank { item.ru }
    }

    private fun stronger(a: Advice, b: Advice): Advice = when {
        a == Advice.End || b == Advice.End -> Advice.End
        a is Advice.Break -> a
        else -> b
    }

    private fun currentDay() = learningDay(nowMs(), utcOffsetMinutes(), engineConfig.dayStartHour)

    private fun elapsedMs() = nowMs() - shownAtMs

    private fun remaining() = session().remainingSteps

    private fun session() = session ?: error("No session running")

    private fun coach() = coach ?: error("No session running")

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
