package com.conwic.mixmaster.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields

/** ISO-8601 week number — the "week 38" that gets said out loud on site. */
fun isoWeek(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

fun formatWeek(date: LocalDate): String = "W${isoWeek(date)}"

private val dayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE d MMM")
private val longDayFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE, d MMMM")

fun formatDay(date: LocalDate): String = date.format(dayFormatter)

/** "Thursday, 18 September · W38" */
fun formatDayWithWeek(date: LocalDate): String = "${date.format(longDayFormatter)} · ${formatWeek(date)}"

/**
 * Reads the date formats people actually type, not just ISO. Strict ISO parsing meant a due
 * date entered as "18.09.2026" was silently dropped and the task ended up with no date at all.
 *
 * Accepts 2026-09-18, 18.09.2026, 18/9/2026, 18.09.26 and 18.09 (this year).
 */
fun parseDueDate(text: String, today: LocalDate = LocalDate.now()): LocalDate? {
    val raw = text.trim()
    if (raw.isEmpty()) return null

    runCatching { LocalDate.parse(raw) }.getOrNull()?.let { return it }

    val parts = raw.split('.', '/', '-', ' ').filter { it.isNotBlank() }
    return runCatching {
        when (parts.size) {
            2 -> LocalDate.of(today.year, parts[1].toInt(), parts[0].toInt())
            3 -> {
                val year = parts[2].toInt().let { if (it < 100) 2000 + it else it }
                LocalDate.of(year, parts[1].toInt(), parts[0].toInt())
            }
            else -> null
        }
    }.getOrNull()
}
