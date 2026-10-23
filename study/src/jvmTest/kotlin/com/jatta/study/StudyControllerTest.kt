package com.jatta.study

import com.jatta.engine.EngineConfig
import com.jatta.engine.learningDay
import com.jatta.engine.localMinuteOfDay
import com.jatta.study.content.KindFile
import com.jatta.study.content.ReadingText
import com.jatta.study.content.TextSource
import com.jatta.study.exercise.Exercise
import com.jatta.study.judge.DiffKind
import com.jatta.study.judge.EvalCase
import com.jatta.study.judge.JudgeEval
import com.jatta.study.judge.OnDeviceModel
import com.jatta.study.store.LearnerStore
import com.jatta.study.store.SavedProgress
import com.jatta.study.store.SavedState
import com.jatta.study.remind.Reminder
import com.jatta.study.remind.ReminderScheduler
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
    ) = StudyController(
        content, LearnerStore(dir.absolutePath), nowMs = { now }, utcOffsetMinutes = { almaty },
        scope = CoroutineScope(Dispatchers.Unconfined), model = model, evalCases = evalCases,
        texts = texts, reminders = reminders,
    )

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
        assertEquals(TopicState.Locked, home.topics.last().state)
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

    private fun runSessionAnsweringRight(app: StudyController): Screen.Done {
        repeat(500) {
            now += 3_000
            when (val screen = app.screen.value) {
                is Screen.Done -> return screen
                is Screen.Present -> app.presented()
                is Screen.Question -> answerRight(app, screen.exercise)
                is Screen.SelfCheck -> app.judgeSelf(true)
                is Screen.Judged -> app.acceptCorrection()
                is Screen.Checking -> error("No model in this test")
                is Screen.Reveal -> app.continueAfterReveal()
                is Screen.Rest -> app.finishRest()
                is Screen.Home -> error("Unexpected home screen in a session")
                is Screen.Read -> error("Unexpected reading screen in a session")
            }
        }
        error("The session did not finish")
    }

    private fun answerRight(app: StudyController, exercise: Exercise) = when (exercise) {
        is Exercise.Choice -> app.choose(exercise.correctIndex)
        is Exercise.Typed -> app.submitText(exercise.accepted.first())
        is Exercise.Free -> app.submitText("My own sentence.")
        is Exercise.Speak -> app.showSpokenAnswer()
    }
}
