package com.jatta.study.content

import com.jatta.engine.content.Course
import com.jatta.engine.content.Item
import com.jatta.engine.content.Topic
import com.jatta.study.exercise.AnswerCheck
import kotlinx.serialization.json.Json

/** A course ready to study: the engine's view of it plus the texts exercises are built from. */
class StudyCourse(
    val title: String,
    val course: Course,
    val topics: Map<String, TopicFile>,
    val items: Map<String, ItemFile>,
) {
    fun topicOf(itemId: String): TopicFile = topics.getValue(course.item(itemId).topicId)
}

class ContentError(val problems: List<String>) :
    IllegalArgumentException("Content has errors:\n" + problems.joinToString("\n"))

/** Reads content/<language>/course.json and topics/<id>.json, and refuses broken content. */
object ContentLoader {
    private val json = Json { ignoreUnknownKeys = false }

    fun readCourseFile(text: String): CourseFile = json.decodeFromString(text)

    /** [topicTexts] maps every topic id listed in [courseFile] to the text of its file. */
    fun load(courseFile: CourseFile, topicTexts: Map<String, String>): StudyCourse {
        val topics = courseFile.topics.map { id ->
            val text = topicTexts[id] ?: throw ContentError(listOf("No file for topic $id"))
            json.decodeFromString<TopicFile>(text)
        }
        val problems = problems(courseFile, topics)
        if (problems.isNotEmpty()) throw ContentError(problems)

        val course = Course(
            topics = topics.map { Topic(it.id, it.title, it.requires.toSet(), it.confusableWith.toSet()) },
            items = topics.flatMap { topic -> topic.items.map { Item(it.id, topic.id, it.kind.engineKind, it.rank) } },
        )
        return StudyCourse(
            title = courseFile.title,
            course = course,
            topics = topics.associateBy { it.id },
            items = topics.flatMap { it.items }.associateBy { it.id },
        )
    }

    private fun problems(courseFile: CourseFile, topics: List<TopicFile>): List<String> = buildList {
        topics.zip(courseFile.topics).forEach { (topic, listedId) ->
            if (topic.id != listedId) add("File for topic $listedId has id ${topic.id}")
        }
        topics.forEach { topic ->
            val words = topic.items.count { it.kind != KindFile.Rule }
            if (words in 1..3) add("${topic.id}: choice exercises need at least 4 words or phrases in a topic, found $words")
            topic.items.forEach { item -> addAll(itemProblems("${topic.id}/${item.id}", item)) }
        }
    }

    private fun itemProblems(where: String, item: ItemFile): List<String> = buildList {
        if (item.examples.isEmpty()) add("$where: no examples")
        item.examples.forEach { example ->
            val gap = example.gap ?: return@forEach
            if (Gap.find(example.en, gap) == null) add("$where: gap \"$gap\" is not a whole word in \"${example.en}\"")
            if (example.wrong.any { AnswerCheck.normalize(it) == AnswerCheck.normalize(gap) }) {
                add("$where: a wrong option equals the answer \"$gap\"")
            }
        }
        if (item.kind == KindFile.Rule) {
            if (item.examples.none { it.gap != null }) add("$where: a rule needs examples with a gap")
            if (item.examples.none { it.gap != null && it.wrong.size >= 2 }) {
                add("$where: a rule needs an example with at least 2 wrong options")
            }
        }
    }
}

/** Finds and blanks out the gap of an example as whole words. */
internal object Gap {
    const val BLANK = "___"

    fun find(sentence: String, gap: String): MatchResult? = Regex("\\b${Regex.escape(gap)}\\b").find(sentence)

    fun blank(sentence: String, gap: String): String {
        val match = find(sentence, gap) ?: error("Gap \"$gap\" not found in \"$sentence\"")
        return sentence.replaceRange(match.range, BLANK)
    }
}
