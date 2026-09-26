package com.conwic.mixmaster.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale

/** ISO-8601 week number — the "week 38" that gets said out loud on site. */
fun isoWeek(date: LocalDate): Int = date.get(WeekFields.ISO.weekOfWeekBasedYear())

fun formatWeek(date: LocalDate): String = "W${isoWeek(date)}"

// Patterns only. A DateTimeFormatter captures a locale when it is built, so a formatter held in
// a top-level val is stuck with whatever the language was at class load — which is how the app
// ended up printing an Estonian weekday under an English "TODAY". The locale is applied at
// format time instead, from whatever the language setting last put in place.
private const val DAY_PATTERN = "EEE d MMM"
private const val DAY_WITH_YEAR_PATTERN = "EEE d MMM yyyy"
private const val LONG_DAY_PATTERN = "EEEE, d MMMM"

private fun LocalDate.formatIn(pattern: String, locale: Locale): String =
    format(DateTimeFormatter.ofPattern(pattern, locale))

/** "Thursday, 18 September · W38", in whichever language is set. */
fun formatDayWithWeek(date: LocalDate, locale: Locale = AppLocale.current): String =
    "${date.formatIn(LONG_DAY_PATTERN, locale)} · ${formatWeek(date)}"

/**
 * "Fri 18 Sep · W38", or "Fri 18 Sep 2027 · W3" once the year stops being obvious.
 */
fun formatDueDate(
    date: LocalDate,
    today: LocalDate = LocalDate.now(),
    locale: Locale = AppLocale.current,
): String {
    val pattern = if (date.year == today.year) DAY_PATTERN else DAY_WITH_YEAR_PATTERN
    return "${date.formatIn(pattern, locale)} · ${formatWeek(date)}"
}

/** The weekday's own short name — "Mon" / "esmasp." / "ma" — rather than the enum constant. */
fun formatShortWeekday(date: LocalDate, locale: Locale = AppLocale.current): String =
    date.formatIn("EEE", locale).replaceFirstChar { it.uppercase(locale) }

/** "September 2026", for the month heading. */
fun formatMonthYear(date: LocalDate, locale: Locale = AppLocale.current): String =
    date.formatIn("LLLL yyyy", locale).replaceFirstChar { it.uppercase(locale) }

/**
 * Mon–Sun in the chosen language, for a calendar header.
 *
 * Taken from the locale rather than kept as a translated list: these are the same seven names
 * java.time already knows, and a hand-kept copy is one more thing to get out of step.
 */
fun shortWeekdayNames(locale: Locale = AppLocale.current): List<String> =
    java.time.DayOfWeek.entries.map { day ->
        day.getDisplayName(java.time.format.TextStyle.SHORT, locale)
            .replaceFirstChar { it.uppercase(locale) }
    }

/**
 * A moment, in the app's own language.
 *
 * Note and receipt stamps were built with a bare pattern, which falls through to the phone's
 * locale — so with the app in English on an Estonian phone the weekday and month came out
 * Estonian, against a settings screen that promises dates follow the app.
 */
fun formatStamp(at: java.time.Instant, locale: Locale = AppLocale.current): String =
    at.atZone(java.time.ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", locale))

/** The long form under the greeting: "Wednesday, 24 September". */
fun formatGreetingDay(date: LocalDate, locale: Locale = AppLocale.current): String =
    date.formatIn("EEEE, d MMMM", locale)
