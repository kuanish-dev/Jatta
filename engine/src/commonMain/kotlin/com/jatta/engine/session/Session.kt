package com.jatta.engine.session

import com.jatta.engine.EngineConfig
import com.jatta.engine.fsrs.Grade

sealed interface Step {
    val itemId: String

    data class Present(override val itemId: String) : Step

    data class Recall(override val itemId: String, val withHint: Boolean = false) : Step
}

sealed interface Outcome {
    val firstGrade: Grade?

    data class Done(override val firstGrade: Grade?) : Outcome

    data class Hint(override val firstGrade: Grade?) : Outcome

    data object RetryLater : Outcome {
        override val firstGrade: Grade? = null
    }

    data object Reveal : Outcome {
        override val firstGrade: Grade? = null
    }
}

class Session(reviews: List<String>, newItems: List<String>, config: EngineConfig) {
    private val grading = Grading(config)
    private val queue = ArrayDeque<Step>()
    private val answered = mutableSetOf<String>()

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

    val current: Step? get() = queue.firstOrNull()

    val remainingSteps: Int get() = queue.size

    fun presented() {
        check(current is Step.Present) { "The current step is not a presentation" }
        queue.removeFirst()
    }

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
