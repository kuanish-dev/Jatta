package com.jatta.engine

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_DAY = 86_400_000L

/**
 * Learning day of a moment: days since 1970-01-01 in the learner's time zone, with the day boundary
 * moved to [dayStartHour]. The engine schedules in whole learning days, like the FSRS reference.
 */
fun learningDay(epochMs: Long, utcOffsetMinutes: Int, dayStartHour: Int): Int {
    val shifted = epochMs + utcOffsetMinutes * MS_PER_MINUTE - dayStartHour * 60 * MS_PER_MINUTE
    return shifted.floorDiv(MS_PER_DAY).toInt()
}
