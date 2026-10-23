package com.langengine.study

import com.langengine.study.content.ContentLoader
import com.langengine.study.content.StudyCourse
import java.io.File

/** The course from content/en in the repository; the path comes from study/build.gradle.kts. */
object RealContent {
    private val dir = File(System.getProperty("contentDir"), "en")

    fun load(): StudyCourse {
        val courseFile = ContentLoader.readCourseFile(File(dir, "course.json").readText())
        val topics = courseFile.topics.associateWith { File(dir, "topics/$it.json").readText() }
        return ContentLoader.load(courseFile, topics)
    }
}
