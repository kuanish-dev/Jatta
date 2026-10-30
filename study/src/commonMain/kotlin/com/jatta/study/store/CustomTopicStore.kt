package com.jatta.study.store

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString

class CustomTopicStore(directory: String) {
    private val dir = Path(directory, "topics")

    fun list(): List<String> {
        if (!SystemFileSystem.exists(dir)) return emptyList()
        return SystemFileSystem.list(dir)
            .filter { it.name.endsWith(".json") }
            .sortedBy { it.name }
            .map { path -> SystemFileSystem.source(path).buffered().use { it.readString() } }
    }

    fun add(topicId: String, text: String) {
        SystemFileSystem.createDirectories(dir)
        val temp = Path(dir, "$topicId.json.tmp")
        SystemFileSystem.sink(temp).buffered().use { it.writeString(text) }
        SystemFileSystem.atomicMove(temp, Path(dir, "$topicId.json"))
    }
}
