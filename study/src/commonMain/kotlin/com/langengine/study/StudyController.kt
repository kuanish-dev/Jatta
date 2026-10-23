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
import com.langengine.study.judge.EvalCase
import com.langengine.study.judge.FreeAnswerJudge
import com.langengine.study.judge.JudgeEval
import com.langengine.study.judge.JudgeRequest
import com.langengine.study.judge.ModelState
import com.langengine.study.judge.OnDeviceModel
import com.langengine.study.judge.WordDiff
import com.langengine.study.store.AiCheck
import com.langengine.study.store.LearnerStore
import com.langengine.study.store.LogEntry
import com.langengine.study.store.RecentAnswer
import com.langengine.study.store.toLearner
import com.langengine.study.store.toSaved
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

private const val MS_PER_MINUTE = 60_000L

/** Who decided whether an answer was right; kept in the answer log. */
private enum class CheckedBy(val logName: String) { Code("code"), Self("self"), Ai("ai"), AiDisputed("ai-disputed") }

/**
 * Runs the app around the engine: plans the day, walks the session queue, turns steps into exercises,
 * saves every graded answer at once, and follows the rest coach. Free answers go to the on-device model
 * only after it has passed the accuracy test (М21); otherwise the learner compares with the model answer.
 * Platform code supplies the clock, the time zone, the storage directory and the model.
 */
class StudyController(
    private val content: StudyCourse,
    private val store: LearnerStore,
    private val nowMs: () -> Long,
    private val utcOffsetMinutes: () -> Int,
    private val scope: CoroutineScope,
    private val model: OnDeviceModel? = null,
    private val evalCases: List<EvalCase> = emptyList(),
    private val engineConfig: EngineConfig = EngineConfig(),
    private val config: StudyConfig = StudyConfig(),
) {
    private val engine = Engine(content.course, engineConfig)
    private val exercises = ExerciseFactory(content)
    private val judge = model?.let { FreeAnswerJudge(it, config) }

    private val saved = store.load()
    private var learner = saved.toLearner()
    private var recent = saved.recent
    private var aiCheck = saved.aiCheck

    private var modelState = ModelState.Unavailable
    private var downloadedBytes: Long? = null
    private var testedDone: Int? = null

    private var today = 0
    private var session: Session? = null
    private var coach: RestCoach? = null
    private var exercise: Exercise? = null
    private var shownAtMs = 0L
    private var pendingResponseMs = 0L
    private var pendingAdvice: Advice = Advice.Continue
    private var questionCounter = 0
    private var checkToken = 0
    private val firstAnswers = mutableListOf<Boolean>()
    private var endedEarly = false

    private val state = MutableStateFlow<Screen>(home())
    val screen: StateFlow<Screen> = state.asStateFlow()

    init {
        if (model != null) {
            scope.launch {
                modelState = model.state()
                refreshHome()
            }
        }
    }

    fun openHome() {
        checkToken++
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
        answer(index == choice.correctIndex, elapsedMs(), CheckedBy.Code)
    }

    fun submitText(text: String) {
        val responseMs = elapsedMs()
        when (val current = exercise) {
            is Exercise.Typed -> when {
                AnswerCheck.matches(text, current.accepted) -> answer(true, responseMs, CheckedBy.Code)
                current.openEnded -> checkFreeAnswer(text, responseMs, current.judgeTask)
                else -> answer(false, responseMs, CheckedBy.Code)
            }
            is Exercise.Free -> checkFreeAnswer(text, responseMs, current.judgeTask)
            else -> error("No typed answer expected for $current")
        }
    }

    /** The learner has said the sentence aloud and wants to compare. */
    fun showSpokenAnswer() {
        pendingResponseMs = elapsedMs()
        showSelfCheck(response = null)
    }

    fun judgeSelf(correct: Boolean) = answer(correct, pendingResponseMs, CheckedBy.Self)

    /** The learner accepts the model's correction. */
    fun acceptCorrection() = answer(false, pendingResponseMs, CheckedBy.Ai)

    /** The learner is sure the model was wrong; logged separately to watch the model's accuracy. */
    fun disputeCorrection() = answer(true, pendingResponseMs, CheckedBy.AiDisputed)

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

    fun downloadModel() {
        val model = model ?: return
        modelState = ModelState.Downloading
        downloadedBytes = 0
        refreshHome()
        scope.launch {
            val ready = model.download { bytes ->
                downloadedBytes = bytes
                refreshHome()
            }
            downloadedBytes = null
            modelState = if (ready) ModelState.Ready else model.state()
            refreshHome()
        }
    }

    /** М21, "test first, then trust": the model checks lessons only if it matches enough known verdicts. */
    fun testModel() {
        val judge = judge ?: return
        if (testedDone != null || evalCases.isEmpty()) return
        testedDone = 0
        refreshHome()
        scope.launch {
            val result = JudgeEval.run(judge, evalCases) { done ->
                testedDone = done
                refreshHome()
            }
            aiCheck = AiCheck(
                agreement = result.agreement,
                total = result.total,
                wrongAccepted = result.wrongAccepted,
                rightRejected = result.rightRejected,
                unusable = result.unusable,
                enabled = result.agreement >= config.minJudgeAgreement,
            )
            testedDone = null
            store.save(learner.toSaved(recent, aiCheck))
            refreshHome()
        }
    }

    private fun checkFreeAnswer(text: String, responseMs: Long, task: String?) {
        pendingResponseMs = responseMs
        val judge = judge?.takeIf { aiCheck?.enabled == true && modelState == ModelState.Ready }
        if (judge == null || task == null) {
            showSelfCheck(text)
            return
        }
        val current = exercise ?: error("No exercise on screen")
        val token = ++checkToken
        state.value = Screen.Checking(remaining())
        scope.launch {
            val judgment = withTimeoutOrNull(config.judgeTimeoutMs) { judge.judge(JudgeRequest(task, current.answer, text)) }
            if (token != checkToken || state.value !is Screen.Checking) return@launch // the learner left meanwhile
            when {
                judgment == null -> showSelfCheck(text)
                judgment.correct -> answer(true, responseMs, CheckedBy.Ai)
                else -> state.value = Screen.Judged(
                    exercise = current,
                    diff = WordDiff.parts(text, judgment.corrected!!),
                    note = itemNote(current.itemId),
                    remaining = remaining(),
                )
            }
        }
    }

    private fun answer(correct: Boolean, responseMs: Long, checkedBy: CheckedBy) {
        val current = exercise ?: error("No exercise on screen")
        val step = session().current as Step.Recall
        val outcome = session().answer(Answer(correct, responseMs), medianMs(current.rung))
        outcome.firstGrade?.let { grade ->
            learner = engine.recordGrade(learner, step.itemId, grade, today)
            firstAnswers += correct
            recent = (recent + RecentAnswer(current.rung.name, correct, responseMs)).takeLast(config.recentAnswersKept)
            store.save(learner.toSaved(recent, aiCheck))
        }
        store.log(
            LogEntry(
                nowMs(), today, step.itemId, current.rung.name, correct, responseMs,
                outcome.firstGrade?.name, step.withHint, checkedBy.logName,
            ),
        )
        pendingAdvice = stronger(pendingAdvice, coach().onAnswer(Answer(correct, responseMs), nowMs()))

        when (outcome) {
            is Outcome.Done -> advance(if (checkedBy == CheckedBy.Ai) Banner.CheckedByAi else Banner.Correct)
            is Outcome.Hint -> {
                shownAtMs = nowMs()
                state.value = Screen.Question(++questionCounter, current, hint = current.hint, banner = null, remaining = remaining())
            }
            Outcome.RetryLater -> advance(Banner.CorrectWithHint)
            Outcome.Reveal -> state.value = Screen.Reveal(current, note = itemNote(current.itemId), remaining = remaining())
        }
    }

    private fun showSelfCheck(response: String?) {
        state.value = Screen.SelfCheck(exercise ?: error("No exercise on screen"), response, remaining())
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
            store.save(learner.toSaved(recent, aiCheck))
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

    private fun refreshHome() {
        if (state.value is Screen.Home) state.value = home()
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
        val ai = AiStatus(modelState, downloadedBytes, testedDone, evalCases.size, aiCheck)
        return Screen.Home(content.title, plan.reviews.size, plan.newItems.size, plan.postponedReviews, topics, ai)
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
