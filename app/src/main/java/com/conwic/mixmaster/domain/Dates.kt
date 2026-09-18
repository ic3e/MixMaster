package com.conwic.mixmaster.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/** ISO-8601 week number — the "week 38" that gets said out loud on site. */
fun isoWeek(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

fun formatWeek(date: LocalDate): String = "W${isoWeek(date)}"

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val dayWithYearFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM yyyy")
private val longDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

/** "Thursday, 18 September · W38" */
fun formatDayWithWeek(date: LocalDate): String = "${date.format(longDayFormatter)} · ${formatWeek(date)}"

/**
 * "Fri 18 Sep · W38", or "Fri 18 Sep 2027 · W3" once the year stops being obvious.
 */
fun formatDueDate(date: LocalDate, today: LocalDate = LocalDate.now()): String {
    val day = if (date.year == today.year) date.format(dayFormatter) else date.format(dayWithYearFormatter)
    return "$day · ${formatWeek(date)}"
}
