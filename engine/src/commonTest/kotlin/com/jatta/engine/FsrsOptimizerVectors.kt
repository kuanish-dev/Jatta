package com.jatta.engine

import com.jatta.engine.fsrs.Grade
import com.jatta.engine.fsrs.Review

object FsrsOptimizerVectors {
    val histories = listOf(
        listOf(Review(0, Grade.Good), Review(2, Grade.Good), Review(7, Grade.Again), Review(8, Grade.Good), Review(12, Grade.Hard), Review(30, Grade.Easy)),
        listOf(Review(0, Grade.Again), Review(1, Grade.Again), Review(2, Grade.Good), Review(5, Grade.Good), Review(11, Grade.Good)),
        listOf(Review(3, Grade.Easy), Review(20, Grade.Good), Review(80, Grade.Again), Review(81, Grade.Good), Review(90, Grade.Good)),
        listOf(Review(4, Grade.Good), Review(4, Grade.Good), Review(9, Grade.Good), Review(9, Grade.Again), Review(10, Grade.Hard)),
        listOf(Review(10, Grade.Good)),
        listOf(Review(1, Grade.Hard), Review(3, Grade.Hard), Review(6, Grade.Again), Review(7, Grade.Easy), Review(15, Grade.Good), Review(40, Grade.Good), Review(100, Grade.Again)),
    )
    val other = listOf(0.5, 1.0, 3.0, 10.0, 5.0, 1.0, 2.0, 0.1, 1.5, 0.2, 1.0, 2.0, 0.1, 0.4, 2.0, 0.5, 2.5, 0.3, 0.2, 0.1, 0.4)
    const val DEFAULT_LOSS = 0.6427005416614581
    const val OTHER_LOSS = 0.6576458288875162
}
