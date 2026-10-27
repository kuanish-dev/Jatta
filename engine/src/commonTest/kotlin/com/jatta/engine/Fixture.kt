package com.jatta.engine

import com.jatta.engine.content.Course
import com.jatta.engine.content.Item
import com.jatta.engine.content.ItemKind
import com.jatta.engine.content.Topic
import com.jatta.engine.fsrs.Memory
import com.jatta.engine.learner.ItemProgress

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

    val basics = course.itemsOf("basics").map { it.id }

    fun held(itemId: String, today: Int) =
        ItemProgress(itemId, Memory(stability = 30.0, difficulty = 5.0), lastReviewDay = today, dueDay = today + 30)

    fun due(itemId: String, today: Int, daysAgo: Int, stability: Double = 5.0) =
        ItemProgress(itemId, Memory(stability, difficulty = 5.0), lastReviewDay = today - daysAgo, dueDay = today)
}
