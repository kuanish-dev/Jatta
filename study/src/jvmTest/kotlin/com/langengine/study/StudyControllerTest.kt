package com.langengine.study

import com.langengine.study.exercise.Exercise
import com.langengine.study.store.LearnerStore
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class StudyControllerTest {
    private val content = RealContent.load()
    private val dir: File = createTempDirectory("lang-engine-test").toFile()
    private val day = 86_400_000L
    private var now = 1_800_000_000_000L

    private fun controller() = StudyController(content, LearnerStore(dir.absolutePath), nowMs = { now }, utcOffsetMinutes = { 300 })

    @Test
    fun firstDayIntroducesTheFirstWordsAndSavesThem() {
        val app = controller()
        val home = app.screen.value as Screen.Home
        assertEquals(0, home.reviews)
        assertEquals(12, home.newItems)

        app.startSession()
        val first = app.screen.value as Screen.Present
        assertEquals("w-colleague", first.item.id)
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
        assertEquals(5, home.newItems)
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
        assertEquals("w-task", (app.screen.value as Screen.Present).item.id)
    }

    private fun runSessionAnsweringRight(app: StudyController): Screen.Done {
        repeat(500) {
            now += 3_000
            when (val screen = app.screen.value) {
                is Screen.Done -> return screen
                is Screen.Present -> app.presented()
                is Screen.Question -> answerRight(app, screen.exercise)
                is Screen.SelfCheck -> app.judgeSelf(true)
                is Screen.Reveal -> app.continueAfterReveal()
                is Screen.Rest -> app.finishRest()
                is Screen.Home -> error("Unexpected home screen in a session")
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
