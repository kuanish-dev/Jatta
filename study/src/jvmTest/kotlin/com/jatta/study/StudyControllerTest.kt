package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.EngineConfig
import com.jatta.engine.learner.Learner
import com.jatta.engine.learningDay
import com.jatta.engine.localMinuteOfDay
import com.jatta.study.calibration.LogReplay
import com.jatta.study.chat.Change
import com.jatta.study.chat.ChatTopic
import com.jatta.study.chat.ChatTopicsFile
import com.jatta.study.chat.Review
import com.jatta.study.content.BasicWords
import com.jatta.study.content.ContentLoader
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingText
import com.jatta.study.content.TextSource
import com.jatta.study.placement.PlacementFile
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.EvalCase
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.judge.TranslationCase
import com.jatta.study.dictionary.Dictionary
import com.jatta.study.dictionary.ItemState
import com.jatta.study.store.CustomTopicStore
import com.jatta.study.store.LearnerStore
import com.jatta.study.store.SavedProgress
import com.jatta.study.store.SavedState
import com.jatta.study.store.withLearner
import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
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
        translationCases: List<TranslationCase> = emptyList(),
        texts: List<ReadingText> = emptyList(),
        sentences: SentencesFile? = null,
        placement: PlacementFile? = null,
        chatTopics: List<ChatTopic> = emptyList(),
        basicWords: BasicWords? = null,
        topicStore: CustomTopicStore? = null,
        engineConfig: EngineConfig = EngineConfig(),
        config: StudyConfig = StudyConfig(),
    ) = StudyController(
        content, LearnerStore(dir.absolutePath), nowMs = { now }, utcOffsetMinutes = { almaty },
        scope = CoroutineScope(Dispatchers.Unconfined), model = model, evalCases = evalCases, translationCases = translationCases,
        texts = texts, sentences = sentences, reminders = reminders, placementFile = placement, chatTopics = chatTopics,
        basicWords = basicWords, topicStore = topicStore, engineConfig = engineConfig, config = config, background = Dispatchers.Unconfined,
    )

    private val placementFile = PlacementFile.read(File(System.getProperty("contentDir"), "en/placement.json").readText())

    @Test
    fun aNewLearnerStartsOnHomeWhereThePlacementTestIsOfferedUntilTakenOrDeclined() {
        val app = controller(placement = placementFile)
        val home = app.screen.value as Screen.Home
        assertTrue(home.placementOffered)
        assertEquals(12, home.newItems, "the first session needs no test")
        assertFalse(home.chatReady, "no talk before the first session")
        app.openPlacement()
        assertTrue(app.screen.value is Screen.PlacementIntro)
        app.goBack()
        assertTrue((app.screen.value as Screen.Home).placementOffered)
        app.skipPlacement()
        assertFalse((app.screen.value as Screen.Home).placementOffered)
        assertFalse((controller(placement = placementFile).screen.value as Screen.Home).placementOffered, "the choice is kept")
        app.openPlacement()
        assertTrue(app.screen.value is Screen.Home, "a declined test does not open")
    }

    @Test
    fun thePlacementTestAfterAFirstSessionLeavesStudiedItemsAlone() {
        val app = controller(placement = placementFile)
        app.startSession()
        runSessionAnsweringRight(app)
        app.openHome()
        val studied = LearnerStore(dir.absolutePath).load().progress.associateBy { it.item }
        app.openPlacement()
        app.startPlacement()
        while (true) {
            when (val screen = app.screen.value) {
                is Screen.PlacementWord -> app.answerPlacementWord(true)
                is Screen.PlacementGrammar -> app.answerPlacementGrammar(screen.exercise.correctIndex)
                else -> break
            }
        }
        assertTrue(app.screen.value is Screen.PlacementDone)
        val after = LearnerStore(dir.absolutePath).load().progress.associateBy { it.item }
        studied.forEach { (id, before) -> assertEquals(before, after.getValue(id), "$id was studied today and keeps its memory") }
        assertTrue(after.size > studied.size, "other known items are placed")
    }

    @Test
    fun backLeadsToTheStartOfWhatIsOpenAndHomeHasNoBack() {
        val app = controller(placement = placementFile)
        assertFalse(app.screen.value.canGoBack)
        app.openPlacement()
        app.startPlacement()
        app.answerPlacementWord(true)
        app.goBack()
        assertTrue(app.screen.value is Screen.PlacementIntro, "leaving the test returns to its start")
        app.skipPlacement()
        app.openProgress()
        app.goBack()
        assertTrue(app.screen.value is Screen.Home)
        app.startSession()
        app.presented()
        app.goBack()
        assertTrue(app.screen.value is Screen.Home, "back ends a session like the stop button")
        assertFalse(app.screen.value.canGoBack, "on home the system closes the app")
    }

    @Test
    fun whatThePlacementTestFindsKnownStartsInMemory() {
        val app = controller(placement = placementFile)
        app.openPlacement()
        app.startPlacement()
        while (true) {
            when (val screen = app.screen.value) {
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
        assertTrue(home.newItems > 0)

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

    @Test
    fun progressCanBeSavedToAFileAndLoadedOnAnotherPhone() {
        val app = controller()
        app.startSession()
        runSessionAnsweringRight(app)
        app.cycleLook()
        val bundle = app.exportProgress()
        val before = LearnerStore(dir.absolutePath)
        val savedBefore = before.load()
        val logBefore = before.readLog()

        val other = createTempDirectory("jatta-other").toFile()
        val fresh = StudyController(
            content, LearnerStore(other.absolutePath), nowMs = { now }, utcOffsetMinutes = { almaty },
            scope = CoroutineScope(Dispatchers.Unconfined), reminders = reminders, background = Dispatchers.Unconfined,
        )
        assertEquals(12, (fresh.screen.value as Screen.Home).newItems)
        fresh.importProgress("not a bundle")
        assertTrue((fresh.screen.value as Screen.Home).notice!!.startsWith("Это не выгрузка"))
        fresh.importProgress(bundle)
        val home = fresh.screen.value as Screen.Home
        assertEquals(0, home.newItems, "today's new items were started on the first phone")
        assertEquals(savedBefore.progress, LearnerStore(other.absolutePath).load().progress)
        assertEquals(logBefore, LearnerStore(other.absolutePath).readLog())
        assertEquals(Look.Calm, fresh.look.value)
        assertTrue(home.notice!!.startsWith("Прогресс загружен"))
        fresh.openHome()
        assertNull((fresh.screen.value as Screen.Home).notice, "the notice is shown once")
    }

    private val customTopic = """
        {"id": "my-kitchen", "title": "Моя кухня", "intro": "Своя тема.", "items": [
          {"id": "w-kettle", "kind": "word", "en": "kettle", "ru": "чайник", "examples": [{"en": "The kettle is boiling.", "ru": "Чайник кипит.", "gap": "kettle"}]},
          {"id": "w-spoon", "kind": "word", "en": "spoon", "ru": "ложка", "examples": [{"en": "Pass me a spoon, please.", "ru": "Передай ложку.", "gap": "spoon"}]},
          {"id": "w-fork", "kind": "word", "en": "fork", "ru": "вилка", "examples": [{"en": "I need a clean fork.", "ru": "Мне нужна чистая вилка.", "gap": "fork"}]},
          {"id": "w-bowl", "kind": "word", "en": "bowl", "ru": "миска", "examples": [{"en": "Put the salad in a bowl.", "ru": "Положи салат в миску.", "gap": "bowl"}]}
        ]}
    """.trimIndent()

    @Test
    fun aTopicFromAFileIsCheckedAndThenJoinsTheCourse() {
        val topics = CustomTopicStore(dir.absolutePath)
        val app = controller(topicStore = topics)
        assertTrue(app.addTopic(customTopic.replace("w-kettle", "w-account"))!!.contains("w-account"), "an item id taken by the course is refused")
        assertTrue(app.addTopic("{\"id\": \"x\"}")!!.isNotEmpty())
        assertNull(app.addTopic(customTopic))
        assertEquals(1, topics.list().size)

        val withTopic = ContentLoader.load(RealContent.courseFile(), RealContent.topicTexts(), topics.list())
        assertTrue("my-kitchen" in withTopic.customTopics)
        assertEquals(content.items.size + 4, withTopic.items.size)
        val reloaded = StudyController(
            withTopic, LearnerStore(dir.absolutePath), nowMs = { now }, utcOffsetMinutes = { almaty },
            scope = CoroutineScope(Dispatchers.Unconfined), reminders = reminders, topicStore = topics, background = Dispatchers.Unconfined,
        )
        val status = (reloaded.screen.value as Screen.Home).topics.last()
        assertEquals("my-kitchen", status.id)
        assertTrue(status.custom)
        reloaded.openTopic("my-kitchen")
        assertEquals(4, (reloaded.screen.value as Screen.Topic).newToday)
        reloaded.startTopic()
        runSessionAnsweringRight(reloaded)

        val without = controller()
        assertTrue((without.screen.value as Screen.Home).newItems > 0, "progress of a topic that is not loaded is ignored, not fatal")
        without.startSession()
        runSessionAnsweringRight(without)
    }

    @Test
    fun anOwnTextIsAnalysedAndItsCourseWordsCanBeStudied() {
        val basic = BasicWords.read(File(System.getProperty("contentDir"), "en/basic-words.json").readText())
        val app = controller(basicWords = basic)
        app.startSession()
        runSessionAnsweringRight(app)
        app.openHome()
        app.openOwnText()
        assertTrue(app.screen.value is Screen.OwnText)
        app.analyzeText("My colleague Anna missed the deadline, so her salary review was postponed. The budget is a mess.")
        val study = app.screen.value as Screen.TextStudy
        val report = study.report
        assertTrue("colleague" in report.courseWords && "deadline" in report.courseWords && "salary" in report.courseWords)
        assertTrue("w-budget" in report.newItems, "budget is a course word not studied yet")
        assertTrue("w-colleague" in report.metItems, "studied today: can be reviewed early")
        assertFalse("anna" in report.otherWords, "a name is not an unknown word")
        assertFalse("my" in report.otherWords, "basic words are known")
        assertTrue("postponed" in report.otherWords)
        assertTrue(report.metItems.size + report.newItems.size < 12, "recorded phrases do not count as vocabulary: ${report.newItems}")
        assertTrue(report.knownShare > 0.7)
        assertEquals(0, study.newToday, "today's new items are used up")
        app.studyText()
        assertTrue(app.screen.value !is Screen.TextStudy, "weak words of the text are reviewed")
        val done = runSessionAnsweringRight(app)
        assertEquals(report.metItems.size, done.answered)
    }

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

    @Test
    fun aPhraseHeardRightIsShownAsTextToRepeat() {
        val app = controller()
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
    fun aSpokenAnswerHeardAsTheSampleCountsAndAnyOtherIsComparedByTheLearner() {
        val app = controller(engineConfig = EngineConfig(rungStabilityDays = listOf(0.0, 0.0, 0.0, 0.0)))
        app.startSession()
        runSessionAnsweringRight(app)
        now += 5 * day
        app.openHome()
        app.startSession()

        val first = nextSpeak(app)
        app.submitSpoken(listOf("something else", first.answer.uppercase().trimEnd('.', '?', '!')))
        assertFalse(app.screen.value is Screen.SelfCheck)
        assertEquals("speech", LearnerStore(dir.absolutePath).readLog().last().checkedBy)

        val second = nextSpeak(app)
        app.submitSpoken(listOf("I have no idea"))
        val check = app.screen.value as Screen.SelfCheck
        assertEquals("I have no idea", check.response)
        assertEquals(second.answer, check.heard!!.filter { it.kind != DiffKind.Removed }.joinToString(" ") { it.text })
        app.judgeSelf(false)
        assertEquals("self", LearnerStore(dir.absolutePath).readLog().last().checkedBy)
    }

    private fun nextSpeak(app: StudyController): Exercise.Speak {
        val question = answerRightUntil(app) { it is Screen.Question && it.exercise is Exercise.Speak } as Screen.Question
        return question.exercise as Exercise.Speak
    }

    @Test
    fun remindersFollowTheScheduleAtTheUsualStudyTime() {
        val app = controller()
        val first = reminders.last!!
        assertEquals(learningDay(now, almaty, 4), learningDay(first.atMs, almaty, 4))
        assertEquals(19 * 60, localMinuteOfDay(first.atMs, almaty))

        val startMinute = localMinuteOfDay(now, almaty)
        app.startSession()
        runSessionAnsweringRight(app)
        assertTrue(reminders.askedPermission)
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
        assertEquals(Look.Neon, app.look.value)
        app.cycleLook()
        assertEquals(Look.Calm, controller().look.value, "the look survives a restart")
    }

    @Test
    fun aFastWrongAnswerIsMarkedAsAConfidentError() {
        val app = controller()
        app.startSession()
        while (app.screen.value is Screen.Present) app.presented()
        val choice = (app.screen.value as Screen.Question).exercise as Exercise.Choice
        val wrong = (choice.options.indices - choice.correctIndex).first()
        app.choose(wrong)
        assertTrue((app.screen.value as Screen.Question).confidentError)

        app.choose(wrong)
        app.continueAfterReveal()
        while (app.screen.value is Screen.Present) app.presented()
        val next = (app.screen.value as Screen.Question).exercise as Exercise.Choice
        now += 9_000
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
        val model = ReplyingModel { prompt ->
            val case = cases.firstOrNull { "Learner's answer: ${it.response}" in prompt }
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

    @Test
    fun aTalkOpensAfterTheModelPassesItsTestAndEachMessageIsReviewedAtOnce() {
        val cases = JudgeEval.readCases(File(System.getProperty("contentDir"), "eval/free-answers.json").readText())
        val model = ReplyingModel { prompt ->
            val case = cases.firstOrNull { "Learner's answer: ${it.response}" in prompt }
            when {
                case != null -> if (case.correct) "VERDICT: CORRECT" else "VERDICT: WRONG\nFIXED: X " + case.response.substringAfter(' ')
                "Learner's message: I go to the office yesterday" in prompt -> "VERDICT: WRONG\nFIXED: I went to the office yesterday"
                "Learner's message:" in prompt -> "VERDICT: CORRECT"
                else -> "You: Hi! How was your day at work? 😊\nFriend: fine"
            }
        }
        val topics = ChatTopicsFile.read(File(System.getProperty("contentDir"), "en/chat.json").readText())
        val app = controller(model, cases, chatTopics = topics)
        assertFalse((app.screen.value as Screen.Home).chatReady, "no talk before the first session")
        app.startSession()
        runSessionAnsweringRight(app)
        app.openHome()
        val untested = app.screen.value as Screen.Home
        assertTrue(untested.chatReady, "a ready model can talk before its test")
        assertFalse(untested.chatReviewTrusted)
        app.openChat()
        assertFalse((app.screen.value as Screen.ChatTopics).reviewTrusted, "the review is marked approximate until the test")
        app.openHome()
        app.testModel()
        assertTrue((app.screen.value as Screen.Home).chatReviewTrusted)

        app.openChat()
        val chatTopics = app.screen.value as Screen.ChatTopics
        assertEquals(topics, chatTopics.topics)
        assertTrue(chatTopics.reviewTrusted)
        app.startChat("work")
        assertEquals(listOf("Hi! How was your day at work?"), (app.screen.value as Screen.Chat).messages.map { it.text })

        app.sendChat("I go to the office yesterday")
        val chat = app.screen.value as Screen.Chat
        val fixed = chat.messages[1].review as Review.Fixed
        assertEquals(listOf(Change("go", "went")), fixed.changes)
        assertEquals("I went to the office yesterday", fixed.corrected)
        assertEquals(3, chat.messages.size, "the partner answers after the review")
        app.sendChat("It was fine")
        assertEquals(Review.Fine, (app.screen.value as Screen.Chat).messages[3].review)

        app.goBack()
        assertTrue(app.screen.value is Screen.Home)
    }

    @Test
    fun aSessionEndsWithSentencesToTranslate() {
        val app = controller(sentences = RealContent.sentences())
        val first = firstTranslation(app)
        val answers = LearnerStore(dir.absolutePath).readLog().size
        app.submitTranslation(first.task.references.first())
        val close = app.screen.value as Screen.Translated
        assertEquals(TranslationOutcome.Close, close.outcome)
        assertEquals(1.0, close.score)
        assertTrue(close.diff.all { it.kind == DiffKind.Same })
        app.nextTranslation()

        app.screen.value as Screen.Translate
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

    @Test
    fun theDictionaryFindsAnyUnitAndTheTopicListsItsContents() {
        val app = controller()
        app.openDictionary()
        val all = app.screen.value as Screen.Dictionary
        assertEquals(content.course.items.size, all.entries.size)
        assertTrue(all.entries.all { it.status.state == ItemState.New })
        app.searchDictionary("заказ")
        val found = Dictionary.search((app.screen.value as Screen.Dictionary).entries, "заказ")
        assertEquals("w-order", found.first().id)

        app.openItem("w-order")
        val card = app.screen.value as Screen.ItemCard
        assertEquals("order", card.item.en)
        assertEquals(ItemState.New, card.status.state)
        app.goBack()
        assertEquals("заказ", (app.screen.value as Screen.Dictionary).query, "back returns to the same search")

        app.openHome()
        app.startSession()
        runSessionAnsweringRight(app)
        app.openHome()
        val studied = (app.screen.value as Screen.Home).topics.first { it.held > 0 }
        app.openTopic(studied.id)
        val topic = app.screen.value as Screen.Topic
        assertEquals(content.course.itemsOf(studied.id).map { it.id }, topic.entries.map { it.id })
        val held = topic.entries.first { it.status.state == ItemState.Held }
        app.openItem(held.id)
        val heldCard = app.screen.value as Screen.ItemCard
        assertEquals(ItemState.Held, heldCard.status.state)
        assertTrue(heldCard.status.recallTomorrow!! >= 0.85)
        assertTrue(heldCard.status.dueInDays!! >= 1)
        assertEquals(studied.id, heldCard.topicId)
        app.closeItem()
        assertEquals(studied.id, (app.screen.value as Screen.Topic).topic.id)
    }

    @Test
    fun translationsCanBePractisedFromHomeAnyTimeWithoutALimit() {
        val app = controller(sentences = RealContent.sentences())
        val home = app.screen.value as Screen.Home
        assertTrue(home.translationPractice)
        assertEquals(0, home.progress.met, "a learner who has not studied yet can translate")
        app.startTranslation()
        val config = StudyConfig()
        repeat(config.translationsPerSession + 2) { index ->
            val screen = app.screen.value as Screen.Translate
            assertTrue(screen.practice)
            assertEquals(index, screen.steps.done)
            app.submitTranslation(screen.task.references.first())
            assertEquals(TranslationOutcome.Close, (app.screen.value as Screen.Translated).outcome)
            app.nextTranslation()
        }
        app.screen.value as Screen.Translate
        app.submitTranslation("Something else entirely.")
        app.judgeTranslation(right = false)
        app.screen.value as Screen.Translate
        app.goBack()
        val back = app.screen.value as Screen.Home
        assertEquals("Перевод: фраз ${config.translationsPerSession + 3}, верно ${config.translationsPerSession + 2}.", back.notice)
        val store = LearnerStore(dir.absolutePath)
        assertEquals(config.translationsPerSession + 3, store.readTranslations().size)
        assertEquals(0, store.readLog().size, "practice stays out of the answer log")
        assertEquals(0, (app.screen.value as Screen.Home).progress.met, "practice does not touch memory")

        app.startTranslation()
        val again = app.screen.value as Screen.Translate
        assertTrue(store.readTranslations().none { it.passed && it.sentence == again.task.sentenceId }, "a sentence done right is not offered again")
        app.stopSession()
        assertEquals(null, (app.screen.value as Screen.Home).notice, "nothing translated, nothing to report")
    }

    @Test
    fun aModelChecksTranslationsLenientlyOnlyAfterItsOwnTest() {
        val dir = File(System.getProperty("contentDir"), "eval")
        val cases = JudgeEval.readCases(File(dir, "free-answers.json").readText())
        val translationCases = JudgeEval.readTranslationCases(File(dir, "translations.json").readText())
        val model = ReplyingModel { prompt ->
            val free = cases.firstOrNull { "Learner's answer: ${it.response}" in prompt }
            val translation = translationCases.firstOrNull { "Learner's English: ${it.response}\n" in prompt }
            when {
                free != null -> if (free.correct) "VERDICT: CORRECT" else "VERDICT: WRONG\nFIXED: X " + free.response.substringAfter(' ')
                translation?.correct == false -> "MEANING: DIFFERENT"
                else -> "MEANING: SAME"
            }
        }
        val app = controller(model, cases, translationCases = translationCases, sentences = RealContent.sentences())
        app.testModel()
        val check = (app.screen.value as Screen.Home).ai.lastTest!!
        assertEquals(1.0, check.translationAgreement)
        assertTrue(check.translationsEnabled)

        firstTranslation(app)
        while (true) {
            app.screen.value as? Screen.Translate ?: break
            app.submitTranslation("In my own words.")
            assertEquals(TranslationOutcome.RightByAi, (app.screen.value as Screen.Translated).outcome)
            app.nextTranslation()
        }
        val log = LearnerStore(this.dir.absolutePath).readTranslations()
        assertTrue(log.isNotEmpty() && log.all { it.checkedBy == "ai" && it.passed })
    }

    @Test
    fun anyTopicCanBeGoneThroughOutsideThePlan() {
        val app = controller()
        app.startSession()
        runSessionAnsweringRight(app)
        now += day
        app.openHome()
        val before = LearnerStore(dir.absolutePath).load().progress.associateBy { it.item }

        app.openTopic("work-words")
        val topic = app.screen.value as Screen.Topic
        assertEquals(12, topic.reviews)
        assertEquals(12, topic.early, "learned yesterday, due in a few days")
        assertEquals(0, topic.newToday)
        app.startTopic()
        runSessionAnsweringRight(app)

        val after = LearnerStore(dir.absolutePath).load().progress.associateBy { it.item }
        val today = learningDay(now, almaty, EngineConfig().dayStartHour)
        val reviewed = content.course.itemsOf("work-words").map { it.id }
        reviewed.forEach { item ->
            assertEquals(today, after.getValue(item).lastReviewDay)
            assertTrue(after.getValue(item).dueDay > before.getValue(item).dueDay, "$item: an early review moves the next one later, not to now")
        }

        app.openHome()
        app.openTopic("present-perfect")
        val closed = app.screen.value as Screen.Topic
        assertTrue(closed.missing.isNotEmpty(), "a closed topic says what it builds on")
        assertEquals(content.course.itemsOf("present-perfect").size, closed.newToday, "its new items fit in today's limit")
    }

    @Test
    fun eachDayOpensWithYesterdaysSummaryAndTheStreak() {
        controller().apply { startSession() }.also { runSessionAnsweringRight(it) }
        now += day

        val app = controller()
        val summary = app.screen.value as Screen.DailySummary
        val yesterday = summary.yesterday!!
        assertEquals(12, yesterday.newItems)
        assertEquals(yesterday.answered, yesterday.newItems + yesterday.reviews)
        assertTrue(yesterday.minutes >= 1)
        assertEquals(1, summary.streak.days)
        assertFalse(summary.streak.studiedToday)
        app.closeSummary()
        assertEquals(1, (app.screen.value as Screen.Home).streak.days)
        app.openProgress()
        val progress = (app.screen.value as Screen.Progress).report
        assertEquals(1, progress.longestStreak)
        assertEquals(listOf("Слова и сочетания", "Грамматика", "Фразы на слух"), progress.parts.map { it.title })
        assertEquals(content.items.size, progress.parts.sumOf { it.total })
        app.openHome()
        assertTrue(controller().screen.value is Screen.Home, "once a day")

        app.startSession()
        val done = runSessionAnsweringRight(app)
        assertTrue(done.streakGrew)
        assertEquals(2, done.streak)

        now += 3 * day
        val later = controller().screen.value as Screen.DailySummary
        assertNull(later.yesterday)
        assertEquals(0, later.streak.days, "two days with something to study were missed")
    }

    @Test
    fun firstDayIntroducesTheFirstWordsAndSavesThem() {
        val app = controller()
        val home = app.screen.value as Screen.Home
        assertEquals(0, home.reviews)
        assertEquals(12, home.newItems)

        app.startSession()
        val first = app.screen.value as Screen.Present
        assertEquals("w-experience", first.item.id)
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
        again.closeSummary()
        val home = again.screen.value as Screen.Home
        assertEquals(12, home.reviews)
        assertEquals(12, home.newItems)
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
                is Screen.Read, is Screen.OwnText, is Screen.TextStudy,
                is Screen.PlacementIntro, is Screen.PlacementWord, is Screen.PlacementGrammar, is Screen.PlacementDone,
                is Screen.Calibration, is Screen.Done, is Screen.Translate, is Screen.Translated, is Screen.Topic,
                is Screen.Dictionary, is Screen.ItemCard,
                is Screen.ChatTopics, is Screen.Chat,
                is Screen.DailySummary, is Screen.Progress ->
                    error("Unexpected screen in a session: $screen")
            }
        }
        error("The session did not finish")
    }

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
