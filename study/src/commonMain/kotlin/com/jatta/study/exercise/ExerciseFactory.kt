package com.jatta.study.exercise

import com.jatta.engine.session.Rung
import com.jatta.study.content.ExampleFile
import com.jatta.study.content.Gap
import com.jatta.study.content.ItemFile
import com.jatta.study.content.KindFile
import com.jatta.study.content.StudyCourse
import kotlin.random.Random

/**
 * Builds the exercise for an item on a rung (М5). When the item has no material for that rung
 * it takes the nearest lower rung, then the nearest higher one.
 */
class ExerciseFactory(private val content: StudyCourse) {

    fun build(itemId: String, rung: Rung, seed: Int): Exercise {
        val item = content.items.getValue(itemId)
        val random = Random(seed)
        val lower = Rung.entries.filter { it <= rung }.reversed()
        val higher = Rung.entries.filter { it > rung }
        return (lower + higher).firstNotNullOfOrNull { build(item, it, random) }
            ?: error("Item $itemId has no material for any exercise")
    }

    private fun build(item: ItemFile, rung: Rung, random: Random): Exercise? =
        if (item.kind == KindFile.Rule) ruleExercise(item, rung, random) else wordExercise(item, rung, random)

    private fun wordExercise(item: ItemFile, rung: Rung, random: Random): Exercise? = when (rung) {
        Rung.Recognize -> {
            val options = (listOf(item.ru) + otherWords(item).map { it.ru }.distinct().shuffled(random).take(3)).shuffled(random)
            Exercise.Choice(
                item.id, rung, "Что значит это слово?",
                question = item.en, translation = null,
                options = options, correctIndex = options.indexOf(item.ru),
                hint = item.examples.first().en, answer = item.ru, listen = item.en,
            )
        }
        Rung.FillGap -> gapped(item, random)?.let { example ->
            val gap = example.gap!!
            Exercise.Typed(
                item.id, rung, "Вставьте слово «${item.ru}»",
                question = Gap.blank(example.en, gap), translation = example.ru, cue = null,
                accepted = listOf(gap), openEnded = false,
                hint = firstLetters(gap), answer = example.en, listen = example.en,
            )
        }
        Rung.Translate -> Exercise.Typed(
            item.id, rung, "Как это по-английски?",
            question = item.ru, translation = null, cue = null,
            accepted = listOf(item.en) + item.also, openEnded = false,
            hint = firstLetters(item.en), answer = item.en, listen = item.en,
        )
        Rung.Compose -> Exercise.Free(
            item.id, rung, "Напишите своё предложение",
            task = "Со словом «${item.en}» (${item.ru})",
            references = item.examples.map { it.en },
            hint = item.note.ifBlank { item.examples.first().en }, answer = item.examples.first().en,
            listen = item.examples.first().en,
            judgeTask = "Write an English sentence using the word \"${item.en}\" (${item.ru})",
        )
        Rung.Speak -> speak(item, random)
    }

    private fun ruleExercise(item: ItemFile, rung: Rung, random: Random): Exercise? = when (rung) {
        Rung.Recognize -> item.examples.filter { it.gap != null && it.wrong.size >= 2 }.randomOrNull(random)?.let { example ->
            val gap = example.gap!!
            val options = (listOf(gap) + example.wrong.shuffled(random).take(3)).shuffled(random)
            Exercise.Choice(
                item.id, rung, "Выберите, что подходит",
                question = Gap.blank(example.en, gap), translation = example.ru,
                options = options, correctIndex = options.indexOf(gap),
                hint = item.ru, answer = example.en, listen = example.en,
            )
        }
        Rung.FillGap -> gapped(item, random)?.let { example ->
            Exercise.Typed(
                item.id, rung, "Вставьте пропущенное",
                question = Gap.blank(example.en, example.gap!!), translation = example.ru, cue = example.cue,
                accepted = listOf(example.gap), openEnded = false,
                hint = item.ru, answer = example.en, listen = example.en,
            )
        }
        Rung.Translate -> item.examples.random(random).let { example ->
            Exercise.Typed(
                item.id, rung, "Переведите на английский",
                question = example.ru, translation = null, cue = null,
                accepted = listOf(example.en) + example.also, openEnded = true,
                hint = item.ru, answer = example.en, listen = example.en,
                judgeTask = "Translate into English: «${example.ru}»",
            )
        }
        Rung.Compose -> Exercise.Free(
            item.id, rung, "Напишите своё предложение",
            task = item.ru,
            references = item.examples.map { it.en },
            hint = item.note.ifBlank { item.ru }, answer = item.examples.first().en,
            listen = item.examples.first().en,
            judgeTask = "Write an English sentence using the ${content.topicOf(item.id).title} pattern \"${item.en}\" (${item.ru})",
        )
        Rung.Speak -> speak(item, random)
    }

    private fun speak(item: ItemFile, random: Random): Exercise {
        val example = item.examples.random(random)
        return Exercise.Speak(
            item.id, Rung.Speak, "Скажите вслух по-английски",
            prompt = example.ru, hint = if (item.kind == KindFile.Rule) item.ru else item.en, answer = example.en,
            listen = example.en,
        )
    }

    private fun gapped(item: ItemFile, random: Random): ExampleFile? =
        item.examples.filter { it.gap != null }.randomOrNull(random)

    /** Words and phrases of the same topic to use as wrong options, or of the whole course if the topic is small. */
    private fun otherWords(item: ItemFile): List<ItemFile> {
        val topic = content.topicOf(item.id)
        val sameTopic = topic.items.filter { it.kind != KindFile.Rule && it.id != item.id && it.ru != item.ru }
        if (sameTopic.size >= 3) return sameTopic
        return content.items.values.filter { it.kind != KindFile.Rule && it.id != item.id && it.ru != item.ru }
    }

    private fun firstLetters(answer: String): String =
        answer.split(' ').joinToString(" ") { word -> word.take(1) + "…" } + " (букв: ${answer.count { it.isLetter() }})"
}
