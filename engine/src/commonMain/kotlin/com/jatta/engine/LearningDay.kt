package com.jatta.engine

private const val MS_PER_MINUTE = 60_000L
private const val MS_PER_DAY = 86_400_000L

fun learningDay(epochMs: Long, utcOffsetMinutes: Int, dayStartHour: Int): Int {
    val shifted = epochMs + utcOffsetMinutes * MS_PER_MINUTE - dayStartHour * 60 * MS_PER_MINUTE
    return shifted.floorDiv(MS_PER_DAY).toInt()
}

fun localMinuteOfDay(epochMs: Long, utcOffsetMinutes: Int): Int =
    (epochMs + utcOffsetMinutes * MS_PER_MINUTE).mod(MS_PER_DAY).div(MS_PER_MINUTE).toInt()

fun epochMsAt(day: Int, minuteOfDay: Int, utcOffsetMinutes: Int, dayStartHour: Int): Long {
    val calendarDay = if (minuteOfDay >= dayStartHour * 60) day.toLong() else day + 1L
    return calendarDay * MS_PER_DAY + minuteOfDay * MS_PER_MINUTE - utcOffsetMinutes * MS_PER_MINUTE
}

data class CalendarDate(val year: Int, val month: Int, val dayOfMonth: Int)

fun calendarDate(day: Int): CalendarDate {
    val z = day + 719_468
    val era = z.floorDiv(146_097)
    val dayOfEra = z - era * 146_097
    val yearOfEra = (dayOfEra - dayOfEra / 1_460 + dayOfEra / 36_524 - dayOfEra / 146_096) / 365
    val dayOfYear = dayOfEra - (365 * yearOfEra + yearOfEra / 4 - yearOfEra / 100)
    val shifted = (5 * dayOfYear + 2) / 153
    val month = if (shifted < 10) shifted + 3 else shifted - 9
    val year = yearOfEra + era * 400 + if (month <= 2) 1 else 0
    return CalendarDate(year, month, dayOfYear - (153 * shifted + 2) / 5 + 1)
}
