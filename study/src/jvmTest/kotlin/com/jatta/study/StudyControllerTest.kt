package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.EngineConfig
import com.jatta.engine.learner.Learner
import com.jatta.engine.learningDay
import com.jatta.engine.localMinuteOfDay
import com.jatta.study.calibration.LogReplay
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingText
import com.jatta.study.content.TextSource
import com.jatta.study.placement.PlacementFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.EvalCase
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.store.LearnerStore
import com.jatta.study.store.SavedProgress
import com.jatta.study.store.SavedState
import com.jatta.study.store.withLearner
import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
import com.jatta.study.translate.Direction
import com.jatta.study.translate.SentencesFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Keeps the last scheduled reminder instead of showing it. */
class RecordingReminders : ReminderScheduler {
    var last: Reminder? = null
    var askedPermission = false
    override fun askPermission() { askedPermission = true }
    override fun schedule(reminder: Reminder?) { last = reminder }
}

class StudyControllerTest {
    private val content = RealContent.load()
    private val dir: File = createTempDirectory("jatta-test").toFile()
    private val day = 86_400_000L
    private var now = 1_800_000_000_000L

    private val almaty = 300
    private val reminders = RecordingReminders()

    private fun controller(
        model: OnDeviceModel? = null,
        evalCases: List<EvalCase> = emptyList(),
        texts: List<ReadingText> = emptyList(),
        sentences: SentencesFile? = null,
        placement: PlacementFile? = null,
        engineConfig: EngineConfig = EngineConfig(),
        config: StudyConfig = StudyConfig(),
    ) = StudyController(
        content, LearnerStore(dir.absolutePath), nowMs = { now }, utcOffsetMinutes = { almaty },
        scope = CoroutineScope(Dispatchers.Unconfined), model = model, evalCases = evalCases,
        texts = texts, sentences = sentences, reminders = reminders, placementFile = placement,
        engineConfig = engineConfig, config = config, background = Dispatchers.Unconfined,
    )

    private val placementFile = PlacementFile.read(File(System.getProperty("contentDir"), "en/placement.json").readText())

    @Test
    fun aNewLearnerIsOfferedThePlacementTestAndMaySkipIt() {
        val app = controller(placement = placementFile)
        assertTrue(app.screen.value is Screen.PlacementIntro)
        app.skipPlacement()
        assertEquals(12, (app.screen.value as Screen.Home).newItems)
        assertTrue(controller(placement = placementFile).screen.value is Screen.Home, "the test is offered once")
    }

    @Test
    fun whatThePlacementTestFindsKnownStartsInMemory() {
        val app = controller(placement = placementFile)
        app.startPlacement()
        while (true) {
            when (val screen = app.screen.value) {
                // knows every real word and no pseudoword
                is Screen.PlacementWord -> app.answerPlacementWord(screen.word !in placementFile.pseudowords)
                is Screen.PlacementGrammar -> app.answerPlacementGrammar(screen.exercise.correctIndex)
                else -> break
            }
        }
        val done = app.screen.value as Screen.PlacementDone
        assertEquals(2809, done.result.estimatedWords)
        app.openHome()
        val home = app.screen.value as Screen.Home
        assertEquals(VocabularyEstimate(2809, 2809), home.vocabulary)
        assertEquals(done.result.knownItems.size, home.progress.held)
        // words outside NGSL still have to be learned
        assertTrue(home.newItems > 0)

        // placed items are in the log, so a replay rebuilds them, but they are not scored
        app.openCalibration()
        assertEquals(0, (app.screen.value as Screen.Calibration).report.reviews)
        now += 3 * day
        app.openHome()
        app.startSession()
        runSessionAnsweringRight(app)
        app.openCalibration()
        val report = (app.screen.value as Screen.Calibration).report
        assertTrue(report.reviews > 0, "placed items came back for review")
        assertEquals(1.0, report.actual)
    }

    @Test
    fun afterASessionTheMemoryModelIsFittedToTheLogAndMemoryRebuilt() {
        // small batches so a short history is enough to fit on
        val engineConfig = EngineConfig(optimizerBatchReviews = 64)
        val studyConfig = StudyConfig(refitAfterReviews = 64)
        val today = learningDay(now, almaty, 4)
        val history = SimulatedLog.of(
            content.course.items.take(48).map { it.id }, lastDay = today - 1, seed = 1,
            truth = SimulatedLog.fastForgetter, firstDay = today - 150,
        )
        val store = LearnerStore(dir.absolutePath)
        history.forEach(store::log)
        store.save(SavedState().withLearner(Learner(LogReplay.progress(history, Engine(content.course).memory))))

        val app = controller(engineConfig = engineConfig, config = studyConfig)
        app.openCalibration()
        val before = (app.screen.value as Screen.Calibration).fit
        assertFalse(before.personal)
        assertNull(before.last)
        assertTrue(before.reviews >= before.nextFitAt, "enough reviews for the first fit")

        app.openHome()
        app.startSession()
        runSessionAnsweringRight(app)

        val saved = store.load()
        val check = assertNotNull(saved.fsrsFit)
        assertTrue(check.adopted)
        val parameters = assertNotNull(saved.fsrsParameters)
        val rebuilt = LogReplay.progress(store.readLog(), Engine(content.course, engineConfig.copy(fsrsParameters = parameters)).memory)
        saved.progress.forEach { assertEquals(rebuilt.getValue(it.item).memory.stability, it.stability, "memory of ${it.item} follows the fit") }

        app.openCalibration()
        val after = (app.screen.value as Screen.Calibration).fit
        assertTrue(after.personal)
        assertEquals(check, after.last)
        assertEquals(check.reviews + studyConfig.refitAfterReviews, after.nextFitAt)
    }

    /** М18: twelve new items a day, however many sessions it takes. */
    @Test
    fun theDailyLimitOfNewItemsHoldsAcrossSessions() {
        val app = controller()
        app.startSession()
        runSessionAnsweringRight(app)
        app.openHome()
        assertEquals(0, (app.screen.value as Screen.Home).newItems, "twelve new items were started today already")
        assertEquals(0, (controller().screen.value as Screen.Home).newItems, "and the limit survives a restart")

        now += day
        app.openHome()
        assertEquals(12, (app.screen.value as Screen.Home).newItems)
    }

    /** A state saved before items kept their first study day gets it from the answer log. */
    @Test
    fun anOlderStateLearnsFromTheLogWhatWasStartedToday() {
        controller().apply {
            startSession()
            runSessionAnsweringRight(this)
        }
        val store = LearnerStore(dir.absolutePath)
        val older = store.load().let { state -> state.copy(progress = state.progress.map { it.copy(firstDay = null) }, firstDaysRecorded = false) }
        store.save(older)

        assertEquals(0, (controller().screen.value as Screen.Home).newItems)
        assertTrue(store.load().progress.isNotEmpty())
    }

    /** М24: a phrase recognised by ear is then shown as text, to listen to again and repeat. */
    @Test
    fun aPhraseHeardRightIsShownAsTextToRepeat() {
        val app = controller()
        // the first phrases come a few days in, after the topics before them in the course
        repeat(8) {
            app.openHome()
            app.startSession()
            while (app.screen.value !is Screen.Done) {
                now += 3_000
                when (val screen = app.screen.value) {
                    is Screen.Present -> app.presented()
                    is Screen.Question -> {
                        val exercise = screen.exercise
                        answerRight(app, exercise)
                        if (exercise.byEar) {
                            val heard = app.screen.value as Screen.Heard
                            assertEquals(exercise.answer, heard.exercise.answer)
                            assertEquals(content.items.getValue(exercise.itemId).ru, heard.translation)
                            assertEquals(exercise.recording, heard.exercise.recording)
                            app.continueAfterHeard()
                            assertFalse(app.screen.value is Screen.Heard)
                            return
                        }
                    }
                    is Screen.SelfCheck -> app.judgeSelf(true)
                    is Screen.Reveal -> app.continueAfterReveal()
                    is Screen.Rest -> app.finishRest()
                    else -> error("Unexpected screen: $screen")
                }
            }
            now += day
        }
        error("No phrase by ear in the first sessions")
    }

    @Test
    fun remindersFollowTheScheduleAtTheUsualStudyTime() {
        val app = controller()
        // 13:00 in Almaty, nothing studied yet: today at the default 19:00
        val first = reminders.last!!
        assertEquals(learningDay(now, almaty, 4), learningDay(first.atMs, almaty, 4))
        assertEquals(19 * 60, localMinuteOfDay(first.atMs, almaty))

        val startMinute = localMinuteOfDay(now, almaty)
        app.startSession()
        runSessionAnsweringRight(app)
        assertTrue(reminders.askedPermission)
        // studied today: tomorrow, at the time this session started
        val next = reminders.last!!
        assertEquals(learningDay(now, almaty, 4) + 1, learningDay(next.atMs, almaty, 4))
        assertEquals(startMinute, localMinuteOfDay(next.atMs, almaty))
        assertEquals("Новое на сегодня: 12", next.text)
    }

    @Test
    fun theThemeChoiceCyclesAndIsKept() {
        val app = controller()
        assertEquals(ThemeChoice.System, app.theme.value)
        app.cycleTheme()
        assertEquals(ThemeChoice.Light, app.theme.value)
        assertEquals(ThemeChoice.Light, controller().theme.value, "the choice survives a restart")
        app.cycleTheme()
        app.cycleTheme()
        assertEquals(ThemeChoice.System, app.theme.value)
    }

    @Test
    fun aFastWrongAnswerIsMarkedAsAConfidentError() {
        val app = controller()
        app.startSession()
        while (app.screen.value is Screen.Present) app.presented()
        val choice = (app.screen.value as Screen.Question).exercise as Exercise.Choice
        val wrong = (choice.options.indices - choice.correctIndex).first()
        app.choose(wrong) // answered at once
        assertTrue((app.screen.value as Screen.Question).confidentError)

        app.choose(wrong)
        app.continueAfterReveal()
        while (app.screen.value is Screen.Present) app.presented()
        val next = (app.screen.value as Screen.Question).exercise as Exercise.Choice
        now += 9_000 // slower than the usual 5 s for this exercise form
        app.choose((next.options.indices - next.correctIndex).first())
        assertFalse((app.screen.value as Screen.Question).confidentError)
    }

    @Test
    fun aRealTextIsOfferedAfterTheGrammarAndOnlyWhenTheLearnerKnowsEnoughOfIt() {
        val source = TextSource("Test", "Test", "Test", "https://example.org", "CC0", "")
        val easy = ReadingText("easy", "Easy", "I work in a big office with many people.", rare = emptyList(), source = source)
        val withDeadline = ReadingText("deadline", "Deadline", "The deadline is near.", rare = listOf("deadline"), source = source)
        val texts = listOf(withDeadline, easy)
        assertNull((controller(texts = texts).screen.value as Screen.Home).reading, "no reading before the grammar holds")

        val today = learningDay(now, almaty, 4)
        val store = LearnerStore(dir.absolutePath)
        val grammar = content.items.values.filter { it.kind == KindFile.Rule }.map { SavedProgress(it.id, 40.0, 5.0, today, today + 40) }
        store.save(store.load().copy(progress = grammar))
        val app = controller(texts = texts)
        assertEquals("easy", (app.screen.value as Screen.Home).reading?.text?.id)
        app.openReading()
        app.finishReading()
        assertNull((app.screen.value as Screen.Home).reading, "the other text has an unknown word for a quarter of it")

        store.save(store.load().copy(progress = grammar + SavedProgress("w-deadline", 40.0, 5.0, today, today + 40)))
        val reading = (controller(texts = texts).screen.value as Screen.Home).reading
        assertEquals("deadline", reading?.text?.id)
        assertEquals(setOf("deadline"), reading?.courseWords)
    }

    @Test
    fun theModelChecksFreeAnswersOnlyAfterPassingTheAccuracyTest() {
        val cases = JudgeEval.readCases(File(System.getProperty("contentDir"), "eval/free-answers.json").readText())
        // A model that knows the eval answers and fixes one word; anything else gets "is" for "are".
        val model = ReplyingModel { prompt ->
            val case = cases.firstOrNull { "Learner's sentence: ${it.response}" in prompt }
            when {
                case == null -> "VERDICT: WRONG\nFIXED: The deadline is next Monday."
                case.correct -> "VERDICT: CORRECT"
                else -> "VERDICT: WRONG\nFIXED: X " + case.response.substringAfter(' ')
            }
        }
        val today = learningDay(now, 300, EngineConfig().dayStartHour)
        val strong = listOf("w-colleague", "w-deadline").map { SavedProgress(it, 40.0, 5.0, lastReviewDay = today - 40, dueDay = today) }
        LearnerStore(dir.absolutePath).save(SavedState(progress = strong))

        val app = controller(model, cases)
        app.startSession()
        assertTrue((app.screen.value as Screen.Question).exercise is Exercise.Free)
        app.submitText("My colleague is great.")
        assertTrue(app.screen.value is Screen.SelfCheck, "an untested model must not check answers")
        app.judgeSelf(true)
        app.stopSession()

        app.testModel()
        val check = (app.screen.value as Screen.Home).ai.lastTest!!
        assertEquals(1.0, check.agreement)
        assertTrue(check.enabled)

        app.startSession()
        app.submitText("The deadline are next Monday.")
        val judged = app.screen.value as Screen.Judged
        assertEquals(listOf("are"), judged.diff.filter { it.kind == DiffKind.Removed }.map { it.text })
        assertEquals(listOf("is"), judged.diff.filter { it.kind == DiffKind.Added }.map { it.text })
        app.acceptCorrection()
        assertNotNull((app.screen.value as Screen.Question).hint)
    }

    /** М25: close to a human translation counts at once; further off, the learner decides; all of it in its own log. */
    @Test
    fun aSessionEndsWithSentencesToTranslate() {
        val app = controller(sentences = RealContent.sentences())
        val first = firstTranslation(app)
        val answers = LearnerStore(dir.absolutePath).readLog().size
        assertEquals(Direction.EnRu, first.task.direction)
        app.submitTranslation(first.task.references.first())
        val close = app.screen.value as Screen.Translated
        assertEquals(TranslationOutcome.Close, close.outcome)
        assertEquals(1.0, close.score)
        assertTrue(close.diff.all { it.kind == DiffKind.Same })
        app.nextTranslation()

        val second = app.screen.value as Screen.Translate
        assertEquals(Direction.RuEn, second.task.direction)
        app.submitTranslation("Something else entirely.")
        val far = app.screen.value as Screen.Translated
        assertEquals(TranslationOutcome.LearnerDecides, far.outcome)
        assertTrue(far.score < 0.8)
        app.judgeTranslation(right = false)

        var translated = 2
        while (true) {
            val screen = app.screen.value as? Screen.Translate ?: break
            app.submitTranslation(screen.task.references.last())
            app.nextTranslation()
            translated++
        }
        (app.screen.value as? Screen.Rest)?.let { app.finishRest() }
        val done = app.screen.value as Screen.Done
        assertEquals(translated, done.translated)
        assertEquals(translated - 1, done.translatedRight)
        val log = LearnerStore(dir.absolutePath).readTranslations()
        assertEquals(listOf("code", "self"), log.take(2).map { it.checkedBy })
        assertEquals(listOf(true, false), log.take(2).map { it.passed })
        assertEquals("Something else entirely.", log[1].response)
        assertEquals(answers, LearnerStore(dir.absolutePath).readLog().size, "translations stay out of the answer log")
    }

    /** М25: English further off goes to a trusted model; Russian stays with the learner, the model was not tested on it. */
    @Test
    fun aTrustedModelChecksEnglishTranslationsOnly() {
        val cases = JudgeEval.readCases(File(System.getProperty("contentDir"), "eval/free-answers.json").readText())
        val model = ReplyingModel { prompt ->
            val case = cases.firstOrNull { "Learner's sentence: ${it.response}" in prompt }
            when {
                case == null -> "VERDICT: CORRECT"
                case.correct -> "VERDICT: CORRECT"
                else -> "VERDICT: WRONG\nFIXED: X " + case.response.substringAfter(' ')
            }
        }
        val app = controller(model, cases, sentences = RealContent.sentences())
        app.testModel()
        assertTrue((app.screen.value as Screen.Home).ai.lastTest!!.enabled)

        firstTranslation(app)
        while (true) {
            val screen = app.screen.value as? Screen.Translate ?: break
            app.submitTranslation("In my own words.")
            val checked = app.screen.value as Screen.Translated
            if (screen.task.direction == Direction.RuEn) {
                assertEquals(TranslationOutcome.RightByAi, checked.outcome)
                app.nextTranslation()
            } else {
                assertEquals(TranslationOutcome.LearnerDecides, checked.outcome)
                app.judgeTranslation(right = true)
            }
        }
        val log = LearnerStore(dir.absolutePath).readTranslations()
        assertEquals(log.map { if (it.direction == "ru-en") "ai" else "self" }, log.map { it.checkedBy })
        assertTrue(log.all { it.passed })
    }

    @Test
    fun firstDayIntroducesTheFirstWordsAndSavesThem() {
        val app = controller()
        val home = app.screen.value as Screen.Home
        assertEquals(0, home.reviews)
        assertEquals(12, home.newItems)

        app.startSession()
        val first = app.screen.value as Screen.Present
        assertEquals("w-experience", first.item.id) // most frequent work word in NGSL
        assertNotNull(first.topicIntro)

        val done = runSessionAnsweringRight(app)
        assertEquals(12, done.answered)
        assertEquals(12, done.rightFirstTime)
        val saved = LearnerStore(dir.absolutePath).load()
        assertEquals(12, saved.progress.size)
        assertTrue(saved.progress.all { it.item.startsWith("w-") })
    }

    @Test
    fun reviewsComeBackAndTheNextTopicOpens() {
        val app = controller()
        app.startSession()
        runSessionAnsweringRight(app)

        now += 2 * day
        val again = controller()
        val home = again.screen.value as Screen.Home
        assertEquals(12, home.reviews)
        assertEquals(12, home.newItems)
        // the passive waits until the Present Perfect holds
        assertEquals(TopicState.Locked, home.topics.single { it.title == content.topics.getValue("passive").title }.state)
    }

    @Test
    fun mistakeGivesAHintThenTheAnswerAndTheWordComesBackLater() {
        val app = controller()
        app.startSession()
        while (app.screen.value is Screen.Present) app.presented()

        val question = app.screen.value as Screen.Question
        val choice = question.exercise as Exercise.Choice
        val wrong = (choice.options.indices - choice.correctIndex).first()
        app.choose(wrong)
        val retry = app.screen.value as Screen.Question
        assertEquals(choice, retry.exercise)
        assertNotNull(retry.hint)

        app.choose(wrong)
        assertTrue(app.screen.value is Screen.Reveal)
        assertEquals(0.212, LearnerStore(dir.absolutePath).load().progress.single().stability)

        app.continueAfterReveal()
        assertEquals("w-responsible", (app.screen.value as Screen.Present).item.id)
    }

    private fun runSessionAnsweringRight(app: StudyController): Screen.Done = answerRightUntil(app) { it is Screen.Done } as Screen.Done

    private fun answerRightUntil(app: StudyController, stop: (Screen) -> Boolean): Screen {
        repeat(500) {
            now += 3_000
            val screen = app.screen.value
            if (stop(screen)) return screen
            when (screen) {
                is Screen.Present -> app.presented()
                is Screen.Question -> answerRight(app, screen.exercise)
                is Screen.SelfCheck -> app.judgeSelf(true)
                is Screen.Judged -> app.acceptCorrection()
                is Screen.Checking -> error("No model in this test")
                is Screen.Reveal -> app.continueAfterReveal()
                is Screen.Heard -> app.continueAfterHeard()
                is Screen.Rest -> app.finishRest()
                is Screen.Home -> error("Unexpected home screen in a session")
                is Screen.Read, is Screen.PlacementIntro, is Screen.PlacementWord, is Screen.PlacementGrammar, is Screen.PlacementDone,
                is Screen.Calibration, is Screen.Done, is Screen.Translate, is Screen.Translated ->
                    error("Unexpected screen in a session: $screen")
            }
        }
        error("The session did not finish")
    }

    /** Sessions day after day until one ends with sentences to translate. */
    private fun firstTranslation(app: StudyController): Screen.Translate {
        repeat(5) {
            app.openHome()
            app.startSession()
            val screen = answerRightUntil(app) { it is Screen.Translate || it is Screen.Done }
            if (screen is Screen.Translate) return screen
            now += day
        }
        error("No sentences to translate after five days")
    }

    private fun answerRight(app: StudyController, exercise: Exercise) = when (exercise) {
        is Exercise.Choice -> app.choose(exercise.correctIndex)
        is Exercise.Typed -> app.submitText(exercise.accepted.first())
        is Exercise.Free -> app.submitText("My own sentence.")
        is Exercise.Speak -> app.showSpokenAnswer()
    }
}
