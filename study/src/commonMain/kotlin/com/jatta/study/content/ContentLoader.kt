package com.jatta.study.content

import com.jatta.engine.content.Course
import com.jatta.engine.content.Item
import com.jatta.engine.content.Topic
import com.jatta.study.exercise.AnswerCheck
import kotlinx.serialization.json.Json

class StudyCourse(
    val title: String,
    val course: Course,
    val topics: Map<String, TopicFile>,
    val items: Map<String, ItemFile>,
    val customTopics: Set<String> = emptySet(),
) {
    fun topicOf(itemId: String): TopicFile = topics.getValue(course.item(itemId).topicId)
}

class ContentError(val problems: List<String>) :
    IllegalArgumentException("Content has errors:\n" + problems.joinToString("\n"))

object ContentLoader {
    private val json = Json { ignoreUnknownKeys = false }

    fun readCourseFile(text: String): CourseFile = json.decodeFromString(text)

    fun load(courseFile: CourseFile, topicTexts: Map<String, String>, customTexts: List<String> = emptyList()): StudyCourse {
        val builtIn = courseFile.topics.map { id ->
            val text = topicTexts[id] ?: throw ContentError(listOf("No file for topic $id"))
            json.decodeFromString<TopicFile>(text)
        }
        val custom = customTexts.map { readTopic(it) ?: throw ContentError(listOf("A custom topic is not valid JSON")) }
        val topics = builtIn + custom
        val problems = problems(courseFile, builtIn) + custom.flatMap { customProblems(it, topics) }
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
            customTopics = custom.mapTo(HashSet()) { it.id },
        )
    }

    fun readTopic(text: String): TopicFile? = runCatching { json.decodeFromString<TopicFile>(text) }.getOrNull()

    fun checkCustom(content: StudyCourse, text: String): List<String> {
        val topic = readTopic(text) ?: return listOf("Файл не похож на тему Jatta: см. docs/course-format.md")
        return customProblems(topic, content.topics.values + topic)
    }

    private fun customProblems(topic: TopicFile, all: List<TopicFile>): List<String> = buildList {
        if (topic.id.isBlank() || !topic.id.all { it.isLetterOrDigit() || it == '-' }) add("${topic.id}: id из латиницы, цифр и дефиса")
        if (topic.title.isBlank()) add("${topic.id}: нет названия")
        if (topic.items.isEmpty()) add("${topic.id}: нет единиц")
        val others = all.filter { it !== topic }
        if (others.any { it.id == topic.id }) add("${topic.id}: тема с таким id уже есть")
        val taken = others.flatMap { it.items }.mapTo(HashSet()) { it.id }
        topic.items.filter { it.id in taken }.forEach { add("${topic.id}/${it.id}: единица с таким id уже есть в курсе") }
        topic.items.groupBy { it.id }.filterValues { it.size > 1 }.keys.forEach { add("${topic.id}/$it: id повторяется") }
        val known = all.mapTo(HashSet()) { it.id }
        (topic.requires + topic.confusableWith).filterNot { it in known }.forEach { add("${topic.id}: неизвестная тема $it") }
        topic.items.filter { it.audio != null }.forEach { add("${topic.id}/${it.id}: у своей темы не может быть записей") }
        val words = topic.items.count { it.kind != KindFile.Rule }
        if (words in 1..3) add("${topic.id}: для выбора из вариантов нужно хотя бы 4 слова, найдено $words")
        topic.items.forEach { addAll(itemProblems("${topic.id}/${it.id}", it)) }
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
        item.audio?.let { audio ->
            if (!audio.file.endsWith(".m4a")) add("$where: recording ${audio.file} is not an .m4a file")
            if (item.examples.none { it.gap != null }) add("$where: a recorded item needs an example with a gap to catch by ear")
        }
        if (item.kind == KindFile.Rule) {
            if (item.examples.none { it.gap != null }) add("$where: a rule needs examples with a gap")
            if (item.examples.none { it.gap != null && it.wrong.size >= 2 }) {
                add("$where: a rule needs an example with at least 2 wrong options")
            }
        }
    }
}

internal object Gap {
    const val BLANK = "___"

    fun find(sentence: String, gap: String): MatchResult? = Regex("\\b${Regex.escape(gap)}\\b").find(sentence)

    fun blank(sentence: String, gap: String): String {
        val match = find(sentence, gap) ?: error("Gap \"$gap\" not found in \"$sentence\"")
        return sentence.replaceRange(match.range, BLANK)
    }
}
