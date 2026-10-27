package com.jatta.study.exercise

import com.jatta.study.translate.Chrf

object SpokenAnswer {
    private val NUMBER = Regex("\\b\\d{1,3}\\b")
    private val SMALL = listOf(
        "zero", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine", "ten",
        "eleven", "twelve", "thirteen", "fourteen", "fifteen", "sixteen", "seventeen", "eighteen", "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun matches(heard: List<String>, answer: String): Boolean = heard.any { spoken(it) == spoken(answer) }

    fun closest(heard: List<String>, answer: String): String = heard.maxBy { Chrf.score(spoken(it), spoken(answer)) }

    fun spoken(text: String): String = AnswerCheck.normalize(NUMBER.replace(text) { words(it.value.toInt()) })

    private fun words(n: Int): String = when {
        n < 20 -> SMALL[n]
        n < 100 -> TENS[n / 10] + if (n % 10 == 0) "" else " " + SMALL[n % 10]
        else -> SMALL[n / 100] + " hundred" + if (n % 100 == 0) "" else " " + words(n % 100)
    }
}
