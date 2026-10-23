package com.langengine.study

import com.langengine.engine.session.Rung
import com.langengine.study.content.ContentError
import com.langengine.study.content.ContentLoader
import com.langengine.study.exercise.Exercise
import com.langengine.study.exercise.ExerciseFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ContentTest {
    private val content = RealContent.load()

    @Test
    fun realCourseLoads() {
        assertEquals(listOf("work-words", "past-simple", "present-perfect"), content.course.topics.map { it.id })
        assertEquals(22, content.items.size)
    }

    @Test
    fun everyItemHasAWorkingExerciseOnEveryRung() {
        val factory = ExerciseFactory(content)
        for (itemId in content.items.keys) for (rung in Rung.entries) for (seed in 0 until 8) {
            val exercise = factory.build(itemId, rung, seed)
            val where = "$itemId on $rung (seed $seed)"
            when (exercise) {
                is Exercise.Choice -> {
                    assertEquals(exercise.options.size, exercise.options.distinct().size, "$where: repeated options")
                    assertTrue(exercise.options.size >= 3, "$where: too few options")
                    val answer = exercise.options[exercise.correctIndex].trim().lowercase()
                    val right = exercise.options.count { it.trim().lowercase() == answer }
                    assertEquals(1, right, "$where: the right option appears more than once")
                }
                is Exercise.Typed -> assertTrue(exercise.accepted.isNotEmpty(), "$where: nothing accepted")
                is Exercise.Free -> assertTrue(exercise.references.isNotEmpty(), "$where: no model sentences")
                is Exercise.Speak -> assertTrue(exercise.answer.isNotBlank(), "$where: no answer")
            }
        }
    }

    @Test
    fun brokenContentIsRefusedWithAReadableReason() {
        val course = ContentLoader.readCourseFile("""{"language":"en","title":"T","topics":["t"]}""")
        val topic = """
            {"id":"t","title":"T","items":[
              {"id":"r","kind":"rule","en":"x","ru":"x","examples":[
                {"en":"I worked late.","ru":"…","gap":"work","wrong":["works","working"]}
              ]}
            ]}
        """.trimIndent()
        val error = assertFailsWith<ContentError> { ContentLoader.load(course, mapOf("t" to topic)) }
        assertTrue(error.problems.any { "not a whole word" in it }, error.message)
    }
}
