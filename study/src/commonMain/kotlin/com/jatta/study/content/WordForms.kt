package com.jatta.study.content

import com.jatta.engine.content.TextCoverage

object WordForms {
    fun ofCourse(content: StudyCourse, coverage: TextCoverage): Map<String, Set<String>> = content.items.values
        .filter { it.kind != KindFile.Rule }
        .associate { item ->
            val texts = listOf(item.en) + item.also + item.examples.mapNotNull { it.gap }
            item.id to texts.flatMapTo(HashSet()) { coverage.words(it) }
        }
}
