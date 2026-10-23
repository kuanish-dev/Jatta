package com.langengine.engine

import com.langengine.engine.content.Course
import com.langengine.engine.content.Item
import com.langengine.engine.content.ItemKind
import com.langengine.engine.content.Topic
import com.langengine.engine.fsrs.Memory
import com.langengine.engine.learner.ItemProgress

/** A small course: basic words, two confusable present tenses built on them, and the past built on the present. */
object Fixture {
    val course = Course(
        topics = listOf(
            Topic("basics", "Basic words"),
            Topic("present-simple", "Present Simple", requires = setOf("basics"), confusableWith = setOf("present-continuous")),
            Topic("present-continuous", "Present Continuous", requires = setOf("basics")),
            Topic("past-simple", "Past Simple", requires = setOf("present-simple")),
        ),
        items = (1..10).map { Item("w$it", "basics", ItemKind.Word, frequencyRank = 11 - it) } +
            (1..4).map { Item("ps$it", "present-simple", ItemKind.Rule) } +
            (1..4).map { Item("pc$it", "present-continuous", ItemKind.Rule) } +
            (1..3).map { Item("past$it", "past-simple", ItemKind.Rule) },
    )

    /** Basic words, most frequent first: w10, w9, …, w1. */
    val basics = course.itemsOf("basics").map { it.id }

    /** Reviewed today with strong memory: holds for weeks. */
    fun held(itemId: String, today: Int) =
        ItemProgress(itemId, Memory(stability = 30.0, difficulty = 5.0), lastReviewDay = today, dueDay = today + 30)

    /** Due today, last reviewed [daysAgo] days ago. */
    fun due(itemId: String, today: Int, daysAgo: Int, stability: Double = 5.0) =
        ItemProgress(itemId, Memory(stability, difficulty = 5.0), lastReviewDay = today - daysAgo, dueDay = today)
}
