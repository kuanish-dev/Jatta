package com.langengine.engine.session

import com.langengine.engine.EngineConfig
import com.langengine.engine.fsrs.Grade

/** A step the learner sees. */
sealed interface Step {
    val itemId: String

    /** First meeting with a new item: shown with its meaning and an example, no question yet. */
    data class Present(override val itemId: String) : Step

    /** Recall the item from memory (М3); [withHint] marks the second try after a mistake (М12). */
    data class Recall(override val itemId: String, val withHint: Boolean = false) : Step
}

/** What follows an answer. [firstGrade] is set only for the item's first answer in the session (М2). */
sealed interface Outcome {
    val firstGrade: Grade?

    /** Right without help: the item leaves the session until its next review (М4). */
    data class Done(override val firstGrade: Grade?) : Outcome

    /** Wrong: show a hint, not the answer, and ask again right away (М12). */
    data class Hint(override val firstGrade: Grade?) : Outcome

    /** Right only with the hint: the item returns at the end of the queue for an unaided answer (М4). */
    data object RetryLater : Outcome {
        override val firstGrade: Grade? = null
    }

    /** Wrong again: show the answer and a short explanation; the item returns at the end of the queue (М4, М12). */
    data object Reveal : Outcome {
        override val firstGrade: Grade? = null
    }
}

/**
 * One study session: a queue in which every item has to end with one correct answer given without
 * help, and is never drilled further that day (М4). Reviews come first, then new items, each new item
 * recalled a few steps after it was presented.
 */
class Session(reviews: List<String>, newItems: List<String>, config: EngineConfig) {
    private val grading = Grading(config)
    private val queue = ArrayDeque<Step>()
    private val answered = mutableSetOf<String>()

    /** True when the session brings new material, which earns quiet rest afterwards (М15). */
    val introducesNew: Boolean = newItems.isNotEmpty()

    init {
        reviews.forEach { queue.addLast(Step.Recall(it)) }
        val gap = config.newItemRecallGap
        newItems.forEachIndexed { index, item ->
            queue.addLast(Step.Present(item))
            if (index >= gap) queue.addLast(Step.Recall(newItems[index - gap]))
        }
        for (index in maxOf(0, newItems.size - gap) until newItems.size) queue.addLast(Step.Recall(newItems[index]))
    }

    /** The step to show now, or null when the session is complete. */
    val current: Step? get() = queue.firstOrNull()

    val remainingSteps: Int get() = queue.size

    /** The learner has looked at the presented new item. */
    fun presented() {
        check(current is Step.Present) { "The current step is not a presentation" }
        queue.removeFirst()
    }

    /** The learner answered the current question; [medianResponseMs] is their usual speed for this exercise form. */
    fun answer(answer: Answer, medianResponseMs: Long): Outcome {
        val step = current as? Step.Recall ?: error("The current step is not a question")
        queue.removeFirst()
        val firstGrade = if (answered.add(step.itemId)) grading.grade(answer, medianResponseMs) else null
        return when {
            answer.correct && !step.withHint -> Outcome.Done(firstGrade)
            !step.withHint -> {
                queue.addFirst(step.copy(withHint = true))
                Outcome.Hint(firstGrade)
            }
            answer.correct -> {
                queue.addLast(step.copy(withHint = false))
                Outcome.RetryLater
            }
            else -> {
                queue.addLast(step.copy(withHint = false))
                Outcome.Reveal
            }
        }
    }
}
