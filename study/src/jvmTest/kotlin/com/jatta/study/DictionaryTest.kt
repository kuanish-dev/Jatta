package com.jatta.study

import com.jatta.engine.Engine
import com.jatta.engine.learner.Learner
import com.jatta.study.dictionary.Dictionary
import com.jatta.study.dictionary.ItemState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DictionaryTest {
    private val content = RealContent.load()
    private val engine = Engine(content.course)
    private val today = 100

    @Test
    fun everyUnitOfTheCourseIsListedInCourseOrderAndFoundByEnglishOrRussian() {
        val entries = Dictionary.entries(content, engine, Learner(), today)
        assertEquals(content.course.items.size, entries.size)
        assertEquals(content.course.topics.flatMap { topic -> content.course.itemsOf(topic.id).map { it.id } }, entries.map { it.id })
        assertTrue(entries.all { it.status.state == ItemState.New && it.status.dueInDays == null })

        val byEnglish = Dictionary.search(entries, "Order")
        assertEquals("w-order", byEnglish.first().id, "a word that starts with the query comes first")
        assertTrue(byEnglish.all { entry -> (entry.en + entry.also.joinToString() + entry.ru).lowercase().contains("order") })
        val byRussian = Dictionary.search(entries, "заказ")
        assertTrue(byRussian.any { it.id == "w-order" })
        assertEquals(entries, Dictionary.search(entries, "  "), "a blank query lists everything")
        assertEquals(emptyList(), Dictionary.search(entries, "zzzzzz"))
    }

    @Test
    fun aUnitPlacedAsKnownIsHeldWithARecallAndADueDay() {
        val learner = engine.placeKnown(Learner(), "w-order", today)
        val entry = Dictionary.entry(content, engine, learner, today, "w-order")
        assertEquals(ItemState.Held, entry.status.state)
        assertTrue(entry.status.recallTomorrow!! >= engine.config.heldRecall)
        assertEquals(learner.progress.getValue("w-order").dueDay - today, entry.status.dueInDays)
        assertEquals(content.topicOf("w-order").title, entry.topicTitle)
    }
}
