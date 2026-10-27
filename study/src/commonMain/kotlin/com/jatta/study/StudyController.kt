package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.EngineConfig
import com.jatta.engine.epochMsAt
import com.jatta.engine.learningDay
import com.jatta.engine.localMinuteOfDay
import com.jatta.engine.fsrs.Grade
import com.jatta.engine.learner.Learner
import com.jatta.engine.plan.DayPlan
import com.jatta.engine.session.Advice
import com.jatta.engine.session.Answer
import com.jatta.engine.session.Baseline
import com.jatta.engine.session.BreakReason
import com.jatta.engine.session.Outcome
import com.jatta.engine.session.RestCoach
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
import com.jatta.study.judge.TranslationCase
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.judge.WordDiff
import com.jatta.study.placement.Placement
import com.jatta.study.placement.PlacementFile
import com.jatta.study.progress.DailyProgress
import com.jatta.study.progress.DayMark
import com.jatta.study.progress.ProgressReport
import com.jatta.study.progress.Streak
import com.jatta.study.reading.ReadingPicker
import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
import com.jatta.study.store.AiCheck
import com.jatta.study.store.LearnerStore
import com.jatta.study.store.LogEntry
import com.jatta.study.store.RecentAnswer
import com.jatta.study.store.SavedPlacement
import com.jatta.study.store.SessionStart
import com.jatta.study.store.TranslationEntry
import com.jatta.study.store.toLearner
import com.jatta.study.store.withFirstDays
import com.jatta.study.store.withLearner
import com.jatta.study.translate.SentencesFile
import com.jatta.study.translate.TranslationPicker
import com.jatta.study.translate.TranslationScore
import com.jatta.study.translate.TranslationTask
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

private class PendingTranslation(val response: String, val score: Double, val responseMs: Long)

private enum class CheckedBy(val logName: String) {
    Code("code"), Self("self"), Ai("ai"), AiDisputed("ai-disputed"), Placement(Calibration.PLACEMENT),
}

class StudyController(
    private val content: StudyCourse,
    private val store: LearnerStore,
    private val nowMs: () -> Long,
    private val utcOffsetMinutes: () -> Int,
    private val scope: CoroutineScope,
    private val model: OnDeviceModel? = null,
    private val evalCases: List<EvalCase> = emptyList(),
    translationCases: List<TranslationCase> = emptyList(),
    private val texts: List<ReadingText> = emptyList(),
    private val sentences: SentencesFile? = null,
    private val reminders: ReminderScheduler? = null,
    placementFile: PlacementFile? = null,
    private val engineConfig: EngineConfig = EngineConfig(),
    private val config: StudyConfig = StudyConfig(),
    private val background: CoroutineDispatcher = Dispatchers.Default,
) {
    private var saved = store.load().let { if (it.firstDaysRecorded) it else it.withFirstDays(LogReplay.firstStudyDays(store.readLog())) }
    private var learner = saved.toLearner()
    private var engine = engineWith(saved.fsrsParameters)
    private var readingPicker = ReadingPicker(content, engine)
    private var translationPicker = sentences?.let { TranslationPicker(it, engine, config) }
    private val personalFit = PersonalFit(engineConfig, config)
    private var fitting = false
    private val exercises = ExerciseFactory(content)
    private val judge = model?.let { FreeAnswerJudge(it, config) }
    private val modelTranslationCases = JudgeEval.belowThreshold(translationCases, config.translationPassScore)
    private val placement = placementFile?.let { Placement(content, it, config) }
    private val grammarTopics = content.topics.values.filter { topic -> topic.items.all { it.kind == KindFile.Rule } }.map { it.id }

    private val themeState = MutableStateFlow(saved.theme)

    val theme: StateFlow<ThemeChoice> = themeState.asStateFlow()

    private var modelState = ModelState.Unavailable
    private var downloadedBytes: Long? = null
    private var downloadFailed = false
    private var following = false
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
    private var translations = emptyList<TranslationTask>()
    private var translationIndex = 0
    private var translatedRight = 0
    private var pendingTranslation: PendingTranslation? = null

    private var streakCache: Pair<Int, Streak>? = null
    private var streakBefore: Streak? = null

    private val state = MutableStateFlow(if (needsPlacement()) placementIntro() else dailySummary() ?: home())
    val screen: StateFlow<Screen> = state.asStateFlow()

    init {
        updateReminder()
        if (model != null) {
            scope.launch {
                modelState = withContext(background) { model.state() }
                refreshHome()
                if (modelState == ModelState.Downloading) followDownload(model)
            }
        }
    }

    fun openHome() {
        checkToken++
        session = null
        coach = null
        exercise = null
        streakCache = null
        state.value = home()
        updateReminder()
    }

    fun startSession() {
        today = currentDay()
        begin(engine.planDay(learner, today))
    }

    fun openTopic(topicId: String) {
        today = currentDay()
        val status = topicStatuses().first { it.id == topicId }
        val items = content.course.itemsOf(topicId)
        val plan = engine.planTopic(learner, topicId, today)
        val missing = content.course.topics.first { it.id == topicId }.requires
            .filterNot { engine.gate.isMastered(it, learner, today) }
            .map { content.topics.getValue(it).title }
        state.value = Screen.Topic(
            topic = status,
            intro = content.topics.getValue(topicId).intro,
            reviews = plan.reviews.size,
            early = plan.reviews.count { learner.progress.getValue(it).dueDay > today },
            newToday = plan.newItems.size,
            newLater = items.count { it.id !in learner.progress } - plan.newItems.size,
            missing = missing,
        )
    }

    fun startTopic() {
        val topic = state.value as? Screen.Topic ?: return
        today = currentDay()
        begin(engine.planTopic(learner, topic.topic.id, today))
    }

    fun openProgress() {
        val day = currentDay()
        scope.launch {
            val report = withContext(background) {
                ProgressReport.of(store.readLog(), store.readTranslations(), engine, progressParts(), day)
            }
            if (state.value is Screen.Home) state.value = Screen.Progress(report)
        }
    }

    private fun progressParts(): List<Pair<String, Set<String>>> {
        val items = content.items.values
        return listOf(
            "Слова и сочетания" to items.filter { it.kind != KindFile.Rule && it.audio == null }.mapTo(HashSet()) { it.id },
            "Грамматика" to items.filter { it.kind == KindFile.Rule }.mapTo(HashSet()) { it.id },
            "Фразы на слух" to items.filter { it.audio != null }.mapTo(HashSet()) { it.id },
        )
    }

    fun closeSummary() {
        saved = saved.copy(lastSummaryDay = currentDay())
        save()
        openHome()
    }

    private fun dailySummary(): Screen.DailySummary? {
        today = currentDay()
        if (saved.lastSummaryDay == today) return null
        val log = store.readLog()
        val translations = store.readTranslations()
        if (DailyProgress.studiedDays(log, translations).none { it < today }) return null
        val yesterday = DailyProgress.summary(log, translations, today - 1)
        val streak = currentStreak()
        val restDay = yesterday == null && streak.week[streak.week.size - 2] == DayMark.Rest
        return Screen.DailySummary(yesterday, restDay, streak)
    }

    private fun currentStreak(): Streak {
        val day = currentDay()
        streakCache?.let { (cached, streak) -> if (cached == day) return streak }
        val log = store.readLog()
        val studied = DailyProgress.studiedDays(log, store.readTranslations())
        val streak = DailyProgress.streak(studied, day) { past ->
            val plan = engine.planDay(Learner(LogReplay.progress(log.filter { it.day < past }, engine.memory)), past)
            plan.reviews.isEmpty() && plan.newItems.isEmpty()
        }
        streakCache = day to streak
        return streak
    }

    private fun begin(plan: DayPlan) {
        streakBefore = currentStreak()
        session = engine.startSession(plan)
        coach = engine.restCoach(baseline(), nowMs())
        val start = SessionStart(today, localMinuteOfDay(nowMs(), utcOffsetMinutes()))
        saved = saved.copy(sessionStarts = (saved.sessionStarts + start).takeLast(config.sessionStartsKept))
        save()
        firstAnswers.clear()
        stepsDone = 0
        pendingAdvice = Advice.Continue
        endedEarly = false
        translations = emptyList()
        showCurrent(banner = null)
    }

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

    fun showSpokenAnswer() {
        pendingResponseMs = elapsedMs()
        showSelfCheck(response = null)
    }

    fun judgeSelf(correct: Boolean) = answer(correct, pendingResponseMs, CheckedBy.Self)

    fun acceptCorrection() = answer(false, pendingResponseMs, CheckedBy.Ai)

    fun disputeCorrection() = answer(true, pendingResponseMs, CheckedBy.AiDisputed)

    fun continueAfterReveal() = advance(banner = null)

    fun continueAfterHeard() {
        val heard = state.value as? Screen.Heard ?: return
        advance(heard.banner)
    }

    fun finishRest() {
        val rest = state.value as Screen.Rest
        if (rest.kind == RestKind.Quiet) {
            showDone()
        } else {
            coach().onBreakFinished(nowMs())
            showCurrent(banner = null)
        }
    }

    fun submitTranslation(text: String) {
        val task = (state.value as? Screen.Translate)?.task ?: return
        val closeness = TranslationScore.of(text, task.references, config.translationPassScore)
        pendingTranslation = PendingTranslation(text, closeness.score, elapsedMs())
        val judge = trustedJudge()?.takeIf { saved.aiCheck?.translationsEnabled == true }
        when {
            closeness.passed -> {
                recordTranslation(true, CheckedBy.Code)
                showTranslated(closeness.closest, TranslationOutcome.Close)
            }
            judge != null -> checkTranslation(judge, task, closeness.closest)
            else -> showTranslated(closeness.closest, TranslationOutcome.LearnerDecides)
        }
    }

    fun judgeTranslation(right: Boolean) {
        val outcome = (state.value as? Screen.Translated)?.outcome ?: return
        val checkedBy = when (outcome) {
            TranslationOutcome.WrongByAi -> if (right) CheckedBy.AiDisputed else CheckedBy.Ai
            TranslationOutcome.LearnerDecides -> CheckedBy.Self
            TranslationOutcome.Close, TranslationOutcome.RightByAi -> return
        }
        recordTranslation(right, checkedBy)
        nextTranslation()
    }

    fun nextTranslation() {
        translationIndex++
        if (translationIndex < translations.size) showTranslation() else concludeSession()
    }

    private fun showTranslation() {
        shownAtMs = nowMs()
        state.value = Screen.Translate(++questionCounter, translations[translationIndex], translationSteps())
    }

    private fun checkTranslation(judge: FreeAnswerJudge, task: TranslationTask, closest: String) {
        val response = pendingTranslation?.response ?: error("No translation to check")
        val token = ++checkToken
        state.value = Screen.Checking(translationSteps())
        scope.launch {
            val request = JudgeRequest(task.source, closest, response)
            val judgment = withTimeoutOrNull(config.judgeTimeoutMs) { judge.judgeTranslation(request) }
            if (token != checkToken || state.value !is Screen.Checking) return@launch
            when {
                judgment == null -> showTranslated(closest, TranslationOutcome.LearnerDecides)
                judgment.correct -> {
                    recordTranslation(true, CheckedBy.Ai)
                    showTranslated(closest, TranslationOutcome.RightByAi)
                }
                else -> showTranslated(closest, TranslationOutcome.WrongByAi)
            }
        }
    }

    private fun showTranslated(target: String, outcome: TranslationOutcome) {
        val pending = pendingTranslation ?: error("No translation on screen")
        state.value = Screen.Translated(
            task = translations[translationIndex],
            response = pending.response,
            score = pending.score,
            target = target,
            diff = WordDiff.parts(pending.response, target),
            outcome = outcome,
            steps = translationSteps(),
        )
    }

    private fun recordTranslation(right: Boolean, checkedBy: CheckedBy) {
        val task = translations[translationIndex]
        val pending = pendingTranslation ?: error("No translation on screen")
        if (right) translatedRight++
        store.logTranslation(
            TranslationEntry(
                nowMs(), today, task.sentenceId, pending.response, pending.score, right, pending.responseMs, checkedBy.logName,
            ),
        )
    }

    private fun translationSteps() = Steps(translationIndex, translations.size - translationIndex)

    fun stopSession() = openHome()

    fun goBack() {
        val current = state.value
        when {
            !current.canGoBack -> Unit
            current is Screen.DailySummary -> closeSummary()
            current is Screen.PlacementWord || current is Screen.PlacementGrammar -> state.value = placementIntro()
            else -> openHome()
        }
    }

    fun startPlacement() {
        placementWords.clear()
        placementGrammar.clear()
        showPlacementStep()
    }

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

    private fun finishPlacement(test: Placement): Screen {
        val result = test.result(placementWords, placementGrammar)
        val today = currentDay()
        result.knownItems.forEach { item ->
            learner = engine.placeKnown(learner, item, today)
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

    private fun adoptFit(result: FitResult) {
        saved = saved.copy(fsrsFit = result.check)
        result.parameters?.let { parameters ->
            saved = saved.copy(fsrsParameters = parameters)
            engine = engineWith(parameters)
            readingPicker = ReadingPicker(content, engine)
            translationPicker = sentences?.let { TranslationPicker(it, engine, config) }
            learner = learner.copy(progress = learner.progress + LogReplay.progress(store.readLog(), engine.memory))
        }
        save()
        updateReminder()
        refreshHome()
    }

    private fun engineWith(parameters: List<Double>?) =
        Engine(content.course, parameters?.let { engineConfig.copy(fsrsParameters = it) } ?: engineConfig)

    fun cycleTheme() {
        val next = ThemeChoice.entries[(saved.theme.ordinal + 1) % ThemeChoice.entries.size]
        saved = saved.copy(theme = next)
        save()
        themeState.value = next
    }

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
        if (following) return
        reminders?.askPermission()
        modelState = ModelState.Downloading
        refreshHome()
        scope.launch { followDownload(model) }
    }

    private suspend fun followDownload(model: OnDeviceModel) {
        if (following) return
        following = true
        val ready = model.download { bytes ->
            downloadedBytes = bytes
            refreshHome()
        }
        following = false
        downloadedBytes = null
        downloadFailed = !ready
        modelState = if (ready) ModelState.Ready else model.state()
        refreshHome()
    }

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
            val translations = JudgeEval.runTranslations(judge, modelTranslationCases, config.translationPassScore) { done ->
                testedDone = evalCases.size + done
                refreshHome()
            }
            val check = AiCheck(
                agreement = result.agreement,
                total = result.total,
                wrongAccepted = result.wrongAccepted,
                rightRejected = result.rightRejected,
                unusable = result.unusable,
                enabled = result.agreement >= config.minJudgeAgreement,
                translationAgreement = translations.agreement.takeIf { translations.total > 0 },
                translationsEnabled = translations.total > 0 && translations.agreement >= config.minJudgeAgreement,
            )
            testedDone = null
            saved = saved.copy(aiCheck = check)
            save()
            refreshHome()
        }
    }

    private fun trustedJudge() = judge?.takeIf { saved.aiCheck?.enabled == true && modelState == ModelState.Ready }

    private fun checkFreeAnswer(text: String, responseMs: Long, task: String?) {
        pendingResponseMs = responseMs
        val judge = trustedJudge()
        if (judge == null || task == null) {
            showSelfCheck(text)
            return
        }
        val current = exercise ?: error("No exercise on screen")
        val token = ++checkToken
        state.value = Screen.Checking(steps())
        scope.launch {
            val judgment = withTimeoutOrNull(config.judgeTimeoutMs) { judge.judge(JudgeRequest(task, current.answer, text)) }
            if (token != checkToken || state.value !is Screen.Checking) return@launch
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
        val outcome = session().answer(Answer(correct, responseMs), medianMs(current))
        stepsDone++
        outcome.firstGrade?.let { grade ->
            learner = engine.recordGrade(learner, step.itemId, grade, today)
            firstAnswers += correct
            val recent = (saved.recent + RecentAnswer(speedKey(current), correct, responseMs)).takeLast(config.recentAnswersKept)
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
            is Outcome.Done -> afterRight(current, if (checkedBy == CheckedBy.Ai) Banner.CheckedByAi else Banner.Correct)
            is Outcome.Hint -> {
                shownAtMs = nowMs()
                val confident = engine.grading.isConfidentError(Answer(correct, responseMs), medianMs(current))
                state.value = Screen.Question(
                    ++questionCounter, current, hint = current.hint, banner = null, steps = steps(), confidentError = confident,
                )
            }
            Outcome.RetryLater -> afterRight(current, Banner.CorrectWithHint)
            Outcome.Reveal -> state.value = Screen.Reveal(current, note = itemNote(current.itemId), steps = steps())
        }
    }

    private fun afterRight(exercise: Exercise, banner: Banner) {
        if (exercise.byEar) {
            state.value = Screen.Heard(exercise, content.items.getValue(exercise.itemId).ru, banner, steps())
        } else {
            advance(banner)
        }
    }

    private fun showSelfCheck(response: String?) {
        state.value = Screen.SelfCheck(exercise ?: error("No exercise on screen"), response, steps())
    }

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
        if (firstAnswers.size >= config.minAnswersForLadderShift) {
            val accuracy = firstAnswers.count { it }.toDouble() / firstAnswers.size
            learner = learner.copy(ladderShift = engine.ladder.adjustShift(learner.ladderShift, accuracy))
            save()
        }
        reminders?.askPermission()
        updateReminder()
        fitMemoryIfDue()
        translations = if (endedEarly) emptyList() else translationPicker?.pick(learner, today, store.readTranslations()).orEmpty()
        translationIndex = 0
        translatedRight = 0
        if (translations.isEmpty()) concludeSession() else showTranslation()
    }

    private fun concludeSession() {
        val after = coach().afterSession(session())
        if (after is Advice.QuietRest) {
            state.value = Screen.Rest(RestKind.Quiet, after.minutes, nowMs() + after.minutes * MS_PER_MINUTE)
        } else {
            showDone()
        }
    }

    private fun showDone() {
        streakCache = null
        val streak = currentStreak()
        state.value = Screen.Done(
            firstAnswers.size, firstAnswers.count { it }, endedEarly, translations.size, translatedRight,
            streak = streak.days,
            streakGrew = streak.studiedToday && streakBefore?.studiedToday == false,
        )
    }

    private fun updateReminder() {
        reminders?.schedule(nextReminder())
    }

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
        val topics = topicStatuses()
        val progress = CourseProgress(
            held = topics.sumOf { it.held },
            met = content.course.items.count { it.id in learner.progress },
            total = content.course.items.size,
        )
        val ai = AiStatus(
            model = modelState,
            modelName = model?.name,
            downloadTotalBytes = model?.downloadBytes,
            downloadedBytes = downloadedBytes,
            downloadFailed = downloadFailed,
            testedDone = testedDone,
            testTotal = evalCases.size + modelTranslationCases.size,
            lastTest = saved.aiCheck,
        )
        val grammarHolds = grammarTopics.all { engine.gate.isMastered(it, learner, today) }
        val reading = if (grammarHolds) readingPicker.pick(texts, learner, today, saved.readTexts.toSet()) else null
        val vocabulary = saved.placement?.let { p -> p.estimatedWords?.let { VocabularyEstimate(it, p.totalWords ?: 0) } }
        return Screen.Home(
            content.title, plan.reviews.size, plan.newItems.size, plan.postponedReviews, topics, ai, progress, vocabulary, reading,
            streak = currentStreak(),
        )
    }

    private fun topicStatuses(): List<TopicStatus> = content.course.topics.map { topic ->
        val items = content.course.itemsOf(topic.id)
        val state = when {
            !engine.gate.isOpen(topic, learner, today) -> TopicState.Locked
            engine.gate.isMastered(topic.id, learner, today) -> TopicState.Mastered
            else -> TopicState.Open
        }
        TopicStatus(topic.id, topic.title, state, held = items.count { engine.gate.holds(learner.progress[it.id], today) }, total = items.size)
    }

    private fun baseline(): Baseline {
        val recent = saved.recent
        if (recent.size < config.minAnswersForBaseline) return Baseline.Default
        return Baseline(
            accuracy = recent.count { it.correct }.toDouble() / recent.size,
            medianResponseMs = median(recent.map { it.responseMs }),
        )
    }

    private fun speedKey(exercise: Exercise) = exercise.rung.name + if (exercise.byEar) "-ear" else ""

    private fun medianMs(exercise: Exercise): Long {
        val times = saved.recent.filter { it.rung == speedKey(exercise) }.map { it.responseMs }
        if (times.size >= config.minAnswersForRungMedian) return median(times)
        val usual = config.defaultMedianMs.getValue(exercise.rung)
        return if (exercise.byEar) usual + config.listeningExtraMs else usual
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
