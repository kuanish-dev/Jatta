package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.store.TranslationEntry
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
        TranslationEntry(0, day, task.sentenceId, "", 0.0, passed, 0, "code")

    @Test
    fun onlySentencesMadeOfHeldWordsFromRussianIntoEnglish() {
        val learner = holding(200)
        val tasks = picker.pick(learner, today, emptyList())
        assertEquals(config.translationsPerSession, tasks.size)
        val sentences = file.sentences.associateBy { it.id }
        val items = tasks.flatMap { sentences.getValue(it.sentenceId).items }
        assertTrue(items.all { it in learner.progress }, "every word of a sentence is held")
        assertEquals(items.size, items.toSet().size, "no word twice in one session")
        tasks.forEach { task ->
            val sentence = sentences.getValue(task.sentenceId)
            assertEquals(sentence.ru.first(), task.source)
            assertEquals(sentence.en, task.references)
            assertEquals("https://tatoeba.org/sentences/show/${sentence.id}", task.sourceUrl)
        }
        assertTrue(picker.pick(Learner(), today, emptyList()).isEmpty(), "nothing held, nothing to translate")
    }

    @Test
    fun aSentenceDoneRightIsNotRepeatedAndOneDoneWrongReturnsAfterAFewDays() {
        val sentence = file.sentences.first()
        val one = TranslationPicker(file.copy(sentences = listOf(sentence)), engine, config)
        fun pickOn(day: Int, history: List<TranslationEntry>): List<Int> {
            val learner = sentence.items.fold(Learner()) { l, id -> engine.placeKnown(l, id, day) }
            return one.pick(learner, day, history).map { it.sentenceId }
        }
        val first = one.pick(sentence.items.fold(Learner()) { l, id -> engine.placeKnown(l, id, today) }, today, emptyList()).single()

        val failed = listOf(entry(first, passed = false))
        assertEquals(emptyList(), pickOn(today + 1, failed))
        assertEquals(listOf(sentence.id), pickOn(today + config.translationRetryDays, failed))
        assertEquals(emptyList(), pickOn(today + 30, listOf(entry(first, passed = true))), "never again once right")
    }
}
