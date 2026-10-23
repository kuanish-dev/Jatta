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

/** Local clock time of a moment, in minutes after midnight. */
fun localMinuteOfDay(epochMs: Long, utcOffsetMinutes: Int): Int =
    (epochMs + utcOffsetMinutes * MS_PER_MINUTE).mod(MS_PER_DAY).div(MS_PER_MINUTE).toInt()

/**
 * The moment of learning day [day] when the local clock shows [minuteOfDay]. Times before
 * [dayStartHour] belong to the end of the learning day, so they fall on the next calendar date.
 */
fun epochMsAt(day: Int, minuteOfDay: Int, utcOffsetMinutes: Int, dayStartHour: Int): Long {
    val calendarDay = if (minuteOfDay >= dayStartHour * 60) day.toLong() else day + 1L
    return calendarDay * MS_PER_DAY + minuteOfDay * MS_PER_MINUTE - utcOffsetMinutes * MS_PER_MINUTE
}
