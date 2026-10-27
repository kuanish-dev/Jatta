package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.store.TranslationEntry
import com.jatta.study.translate.Direction
import com.jatta.study.translate.TranslationPicker
import com.jatta.study.translate.TranslationTask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TranslationPickerTest {
    private val content = RealContent.load()
    private val file = RealContent.sentences()
    private val engine = Engine(content.course)
    private val config = StudyConfig()
    private val picker = TranslationPicker(file, engine, config)
    private val today = 100

    /** The first [count] course words learned today, so they hold tomorrow. */
    private fun holding(count: Int): Learner {
        val words = file.sentences.flatMap { it.items }.distinct().take(count)
        return words.fold(Learner()) { learner, id -> engine.placeKnown(learner, id, today) }
    }

    private fun entry(task: TranslationTask, passed: Boolean, day: Int = today) =
        TranslationEntry(0, day, task.sentenceId, task.direction.logName, "", 0.0, passed, 0, "code")

    @Test
    fun onlySentencesMadeOfHeldWordsInAlternatingDirections() {
        val learner = holding(200)
        val tasks = picker.pick(learner, today, emptyList())
        assertEquals(config.translationsPerSession, tasks.size)
        assertEquals(listOf(Direction.EnRu, Direction.RuEn, Direction.EnRu, Direction.RuEn), tasks.map { it.direction })
        val sentences = file.sentences.associateBy { it.id }
        val items = tasks.flatMap { sentences.getValue(it.sentenceId).items }
        assertTrue(items.all { it in learner.progress }, "every word of a sentence is held")
        assertEquals(items.size, items.toSet().size, "no word twice in one session")
        tasks.forEach { task ->
            val sentence = sentences.getValue(task.sentenceId)
            val (source, references) = if (task.direction == Direction.EnRu) sentence.en.first() to sentence.ru else sentence.ru.first() to sentence.en
            assertEquals(source, task.source)
            assertEquals(references, task.references)
            assertEquals("https://tatoeba.org/sentences/show/${sentence.id}", task.sourceUrl)
        }
        assertTrue(picker.pick(Learner(), today, emptyList()).isEmpty(), "nothing held, nothing to translate")
    }

    @Test
    fun aSentenceDoneRightIsNotRepeatedAndOneDoneWrongReturnsAfterAFewDays() {
        val sentence = file.sentences.first()
        val one = TranslationPicker(file.copy(sentences = listOf(sentence)), engine, config)
        fun pickOn(day: Int, history: List<TranslationEntry>): List<Pair<Int, Direction>> {
            val learner = sentence.items.fold(Learner()) { l, id -> engine.placeKnown(l, id, day) }
            return one.pick(learner, day, history).map { it.sentenceId to it.direction }
        }
        val first = one.pick(sentence.items.fold(Learner()) { l, id -> engine.placeKnown(l, id, today) }, today, emptyList()).single()
        assertEquals(Direction.EnRu, first.direction)

        val failed = listOf(entry(first, passed = false))
        assertEquals(listOf(sentence.id to Direction.RuEn), pickOn(today + 1, failed), "the other direction meanwhile")
        assertEquals(listOf(sentence.id to Direction.EnRu), pickOn(today + config.translationRetryDays, failed))

        val passed = listOf(entry(first, passed = true))
        assertEquals(listOf(sentence.id to Direction.RuEn), pickOn(today + 30, passed), "never again in the direction done right")
    }
}
