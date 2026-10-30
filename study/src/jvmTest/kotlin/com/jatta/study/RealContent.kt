package com.jatta.study

import com.jatta.study.content.ContentLoader
import com.jatta.study.content.CourseFile
import com.jatta.study.content.StudyCourse
import com.jatta.study.translate.SentencesFile
import java.io.File

object RealContent {
    private val dir = File(System.getProperty("contentDir"), "en")

    fun load(): StudyCourse = ContentLoader.load(courseFile(), topicTexts())

    fun courseFile(): CourseFile = ContentLoader.readCourseFile(File(dir, "course.json").readText())

    fun topicTexts(): Map<String, String> = courseFile().topics.associateWith { File(dir, "topics/$it.json").readText() }

    fun sentences(): SentencesFile = SentencesFile.read(File(dir, "sentences.json").readText())
}
