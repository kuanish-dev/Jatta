package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.EngineConfig
import com.jatta.engine.epochMsAt
import com.jatta.engine.learningDay
import com.jatta.engine.localMinuteOfDay
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.session.Advice
import com.jatta.engine.session.Answer
import com.jatta.engine.session.Baseline
import com.jatta.engine.session.BreakReason
import com.jatta.engine.session.Outcome
import com.jatta.engine.session.RestCoach
import com.jatta.engine.session.Rung
import com.jatta.engine.session.Session
import com.jatta.engine.session.Step
import com.jatta.study.calibration.Calibration
import com.jatta.study.calibration.FitResult
import com.jatta.study.calibration.LogReplay
import com.jatta.study.calibration.PersonalFit
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingText
import com.jatta.study.content.StudyCourse
import com.jatta.study.exercise.AnswerCheck
import com.jatta.study.exercise.Exercise
import com.jatta.study.exercise.ExerciseFactory
import com.jatta.study.judge.EvalCase
import com.jatta.study.judge.FreeAnswerJudge
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.JudgeRequest
import com.jatta.study.judge.ModelState
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.judge.WordDiff
import com.jatta.study.placement.Placement
import com.jatta.study.placement.PlacementFile
import com.jatta.study.reading.ReadingPicker
import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
import com.jatta.study.store.AiCheck
import com.jatta.study.store.LearnerStore
import com.jatta.study.store.LogEntry
import com.jatta.study.store.RecentAnswer
import com.jatta.study.store.SavedPlacement
import com.jatta.study.store.SessionStart
import com.jatta.study.store.toLearner
import com.jatta.study.store.withLearner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val MS_PER_MINUTE = 60_000L

/** Who decided whether an answer was right; kept in the answer log. */
private enum class CheckedBy(val logName: String) {
    Code("code"), Self("self"), Ai("ai"), AiDisputed("ai-disputed"), Placement(Calibration.PLACEMENT),
}

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
    private val texts: List<ReadingText> = emptyList(),
    private val reminders: ReminderScheduler? = null,
    placementFile: PlacementFile? = null,
    private val engineConfig: EngineConfig = EngineConfig(),
    private val config: StudyConfig = StudyConfig(),
    /** Where heavy work such as fitting the memory model runs, off the UI thread. */
    private val background: CoroutineDispatcher = Dispatchers.Default,
) {
    private var saved = store.load()
    private var learner = saved.toLearner()
    private var engine = engineWith(saved.fsrsParameters)
    private var readingPicker = ReadingPicker(content, engine)
    private val personalFit = PersonalFit(engineConfig, config)
    private var fitting = false
    private val exercises = ExerciseFactory(content)
    private val judge = model?.let { FreeAnswerJudge(it, config) }
    private val placement = placementFile?.let { Placement(content, it, config) }
    /** Topics made of rules only: the grammar of the course. */
    private val grammarTopics = content.topics.values.filter { topic -> topic.items.all { it.kind == KindFile.Rule } }.map { it.id }

    private val themeState = MutableStateFlow(saved.theme)

    /** The learner's choice of look; the UI decides what System means on its platform. */
    val theme: StateFlow<ThemeChoice> = themeState.asStateFlow()

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
    private var stepsDone = 0
    private val placementWords = mutableMapOf<String, Boolean>()
    private val placementGrammar = mutableListOf<Boolean>()
    private var checkToken = 0
    private val firstAnswers = mutableListOf<Boolean>()
    private var endedEarly = false

    private val state = MutableStateFlow(if (needsPlacement()) placementIntro() else home())
    val screen: StateFlow<Screen> = state.asStateFlow()

    init {
        updateReminder()
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
        updateReminder()
    }

    fun startSession() {
        today = currentDay()
        session = engine.startSession(engine.planDay(learner, today))
        coach = engine.restCoach(baseline(), nowMs())
        val start = SessionStart(today, localMinuteOfDay(nowMs(), utcOffsetMinutes()))
        saved = saved.copy(sessionStarts = (saved.sessionStarts + start).takeLast(config.sessionStartsKept))
        save()
        firstAnswers.clear()
        stepsDone = 0
        pendingAdvice = Advice.Continue
        endedEarly = false
        showCurrent(banner = null)
    }

    /** The learner has read the new item. */
    fun presented() {
        session().presented()
        stepsDone++
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

    /** М23: start the placement test. */
    fun startPlacement() {
        placementWords.clear()
        placementGrammar.clear()
        showPlacementStep()
    }

    /** Start from the beginning of the course instead. */
    fun skipPlacement() {
        saved = saved.copy(placement = SavedPlacement(skipped = true))
        save()
        openHome()
    }

    fun answerPlacementWord(known: Boolean) {
        val screen = state.value as? Screen.PlacementWord ?: return
        placementWords[screen.word] = known
        showPlacementStep()
    }

    fun answerPlacementGrammar(index: Int) {
        val screen = state.value as? Screen.PlacementGrammar ?: return
        placementGrammar += index == screen.exercise.correctIndex
        showPlacementStep()
    }

    private fun showPlacementStep() {
        val test = placement ?: return
        val done = placementWords.size + placementGrammar.size
        val steps = Steps(done, test.words.size + test.grammar.size - done)
        state.value = when {
            placementWords.size < test.words.size -> Screen.PlacementWord(test.words[placementWords.size].word, steps)
            placementGrammar.size < test.grammar.size ->
                Screen.PlacementGrammar(done, test.grammar[placementGrammar.size].exercise, steps)
            else -> finishPlacement(test)
        }
    }

    /** Known items start with a fresh memory, so the scheduler checks them within days. */
    private fun finishPlacement(test: Placement): Screen {
        val result = test.result(placementWords, placementGrammar)
        val today = currentDay()
        result.knownItems.forEach { item ->
            learner = engine.recordGrade(learner, item, Grade.Good, today)
            // Logged so a replay of the log rebuilds the same memory; calibration doesn't score these.
            store.log(LogEntry(nowMs(), today, item, "Placement", true, 0, Grade.Good.name, false, CheckedBy.Placement.logName))
        }
        saved = saved.copy(placement = SavedPlacement(result.estimatedWords, result.totalWords))
        save()
        updateReminder()
        return Screen.PlacementDone(result)
    }

    private fun needsPlacement() = placement != null && saved.placement == null && learner.progress.isEmpty()

    private fun placementIntro(): Screen {
        val test = placement ?: error("No placement test")
        return Screen.PlacementIntro(test.words.size, test.grammar.size)
    }

    /** М2: compare what the model predicted with what the learner recalled, from the answer log. */
    fun openCalibration() {
        val log = store.readLog()
        val fit = FitStatus(
            personal = saved.fsrsParameters != null,
            reviews = personalFit.reviews(log),
            nextFitAt = personalFit.nextFitAt(saved.fsrsFit),
            last = saved.fsrsFit,
        )
        state.value = Screen.Calibration(Calibration.of(log, engine.memory, config), fit)
    }

    /** М2: fit the memory model to the learner's log once enough new reviews have come in. */
    private fun fitMemoryIfDue() {
        val log = store.readLog()
        if (fitting || !personalFit.isDue(log, saved.fsrsFit)) return
        fitting = true
        val current = engine.config.fsrsParameters
        val day = currentDay()
        scope.launch {
            val result = withContext(background) { personalFit.run(log, current, day) }
            fitting = false
            if (result != null) adoptFit(result)
        }
    }

    /** Adopted parameters rebuild every logged item's memory, so schedules follow the better model. */
    private fun adoptFit(result: FitResult) {
        saved = saved.copy(fsrsFit = result.check)
        result.parameters?.let { parameters ->
            saved = saved.copy(fsrsParameters = parameters)
            engine = engineWith(parameters)
            readingPicker = ReadingPicker(content, engine)
            learner = learner.copy(progress = learner.progress + LogReplay.progress(store.readLog(), engine.memory))
        }
        save()
        updateReminder()
        refreshHome()
    }

    private fun engineWith(parameters: List<Double>?) =
        Engine(content.course, parameters?.let { engineConfig.copy(fsrsParameters = it) } ?: engineConfig)

    /** System → Light → Dark → System. */
    fun cycleTheme() {
        val next = ThemeChoice.entries[(saved.theme.ordinal + 1) % ThemeChoice.entries.size]
        saved = saved.copy(theme = next)
        save()
        themeState.value = next
    }

    /** М11: open the real text offered on the home screen. */
    fun openReading() {
        val reading = (state.value as? Screen.Home)?.reading ?: return
        state.value = Screen.Read(reading)
    }

    fun finishReading() {
        val read = state.value as? Screen.Read ?: return
        saved = saved.copy(readTexts = saved.readTexts + read.reading.text.id)
        save()
        openHome()
    }

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
            val check = AiCheck(
                agreement = result.agreement,
                total = result.total,
                wrongAccepted = result.wrongAccepted,
                rightRejected = result.rightRejected,
                unusable = result.unusable,
                enabled = result.agreement >= config.minJudgeAgreement,
            )
            testedDone = null
            saved = saved.copy(aiCheck = check)
            save()
            refreshHome()
        }
    }

    private fun checkFreeAnswer(text: String, responseMs: Long, task: String?) {
        pendingResponseMs = responseMs
        val judge = judge?.takeIf { saved.aiCheck?.enabled == true && modelState == ModelState.Ready }
        if (judge == null || task == null) {
            showSelfCheck(text)
            return
        }
        val current = exercise ?: error("No exercise on screen")
        val token = ++checkToken
        state.value = Screen.Checking(steps())
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
                    steps = steps(),
                )
            }
        }
    }

    private fun answer(correct: Boolean, responseMs: Long, checkedBy: CheckedBy) {
        val current = exercise ?: error("No exercise on screen")
        val step = session().current as Step.Recall
        val outcome = session().answer(Answer(correct, responseMs), medianMs(current.rung))
        stepsDone++
        outcome.firstGrade?.let { grade ->
            learner = engine.recordGrade(learner, step.itemId, grade, today)
            firstAnswers += correct
            val recent = (saved.recent + RecentAnswer(current.rung.name, correct, responseMs)).takeLast(config.recentAnswersKept)
            saved = saved.copy(recent = recent)
            save()
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
                val confident = engine.grading.isConfidentError(Answer(correct, responseMs), medianMs(current.rung))
                state.value = Screen.Question(
                    ++questionCounter, current, hint = current.hint, banner = null, steps = steps(), confidentError = confident,
                )
            }
            Outcome.RetryLater -> advance(Banner.CorrectWithHint)
            Outcome.Reveal -> state.value = Screen.Reveal(current, note = itemNote(current.itemId), steps = steps())
        }
    }

    private fun showSelfCheck(response: String?) {
        state.value = Screen.SelfCheck(exercise ?: error("No exercise on screen"), response, steps())
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
                    steps = steps(),
                )
            }
            is Step.Recall -> {
                val rung = engine.ladder.rungFor(learner.progress[step.itemId]?.memory, learner.ladderShift)
                val built = exercises.build(step.itemId, rung, seed = step.itemId.hashCode() * 31 + today + questionCounter)
                exercise = built
                shownAtMs = nowMs()
                state.value = Screen.Question(++questionCounter, built, hint = null, banner = banner, steps = steps())
            }
        }
    }

    private fun finishSession() {
        // М6: one step harder or easier by this session's first answers.
        if (firstAnswers.size >= config.minAnswersForLadderShift) {
            val accuracy = firstAnswers.count { it }.toDouble() / firstAnswers.size
            learner = learner.copy(ladderShift = engine.ladder.adjustShift(learner.ladderShift, accuracy))
            save()
        }
        reminders?.askPermission()
        updateReminder()
        fitMemoryIfDue()
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

    private fun updateReminder() {
        reminders?.schedule(nextReminder())
    }

    /**
     * М19, М20: one reminder, at the learner's usual study time, on the next day with something to do.
     * Reviews fall due when the memory model expects forgetting, so the reminder follows the spacing
     * schedule; the same time every day is the cue that turns study into a habit.
     */
    private fun nextReminder(): Reminder? {
        val now = nowMs()
        val offset = utcOffsetMinutes()
        val currentDay = currentDay()
        val minute = usualStudyMinute()
        val first = if (saved.sessionStarts.any { it.day == currentDay }) currentDay + 1 else currentDay
        for (day in first..first + config.reminderLookaheadDays) {
            val at = epochMsAt(day, minute, offset, engineConfig.dayStartHour)
            if (at <= now) continue
            val plan = engine.planDay(learner, day)
            if (plan.reviews.isEmpty() && plan.newItems.isEmpty()) continue
            return Reminder(at, reminderText(plan.reviews.size, plan.newItems.size))
        }
        return null
    }

    /** The median start time of recent sessions, so the reminder comes when the learner usually studies. */
    private fun usualStudyMinute(): Int {
        val minutes = saved.sessionStarts.map { it.minute.toLong() }
        return if (minutes.isEmpty()) config.defaultStudyMinute else median(minutes).toInt()
    }

    private fun reminderText(reviews: Int, newItems: Int) = when {
        reviews > 0 && newItems > 0 -> "Повторить: $reviews, новое: $newItems"
        reviews > 0 -> "Пора повторить $reviews — память как раз начинает ослабевать"
        else -> "Новое на сегодня: $newItems"
    }

    private fun save() {
        saved = saved.withLearner(learner)
        store.save(saved)
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
        val progress = CourseProgress(
            held = topics.sumOf { it.held },
            met = content.course.items.count { it.id in learner.progress },
            total = content.course.items.size,
        )
        val ai = AiStatus(modelState, downloadedBytes, testedDone, evalCases.size, saved.aiCheck)
        // М11, the course author's choice: real texts open once all the grammar of the course holds in memory.
        val grammarHolds = grammarTopics.all { engine.gate.isMastered(it, learner, today) }
        val reading = if (grammarHolds) readingPicker.pick(texts, learner, today, saved.readTexts.toSet()) else null
        val vocabulary = saved.placement?.let { p -> p.estimatedWords?.let { VocabularyEstimate(it, p.totalWords ?: 0) } }
        return Screen.Home(content.title, plan.reviews.size, plan.newItems.size, plan.postponedReviews, topics, ai, progress, vocabulary, reading)
    }

    /** The learner's usual accuracy and speed once there is enough history (М16). */
    private fun baseline(): Baseline {
        val recent = saved.recent
        if (recent.size < config.minAnswersForBaseline) return Baseline.Default
        return Baseline(
            accuracy = recent.count { it.correct }.toDouble() / recent.size,
            medianResponseMs = median(recent.map { it.responseMs }),
        )
    }

    /** The learner's usual speed on this rung, for telling fast from slow answers (М2, М13). */
    private fun medianMs(rung: Rung): Long {
        val times = saved.recent.filter { it.rung == rung.name }.map { it.responseMs }
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

    private fun steps() = Steps(stepsDone, session().remainingSteps)

    private fun session() = session ?: error("No session running")

    private fun coach() = coach ?: error("No session running")

    private fun median(values: List<Long>): Long {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2
    }
}
