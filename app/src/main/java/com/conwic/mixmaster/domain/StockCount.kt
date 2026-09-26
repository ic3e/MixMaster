package com.conwic.mixmaster.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** How often the shelf wants counting. A month is a calendar month: the 12th to the 12th. */
enum class CountInterval(val key: String) {
    OFF("off"),
    WEEKLY("week"),
    FORTNIGHTLY("2weeks"),
    MONTHLY("month"),
    ;

    /** The day the next count falls due, counted on from [date]. Null when reminders are off. */
    fun after(date: LocalDate): LocalDate? = when (this) {
        OFF -> null
        WEEKLY -> date.plusWeeks(1)
        FORTNIGHTLY -> date.plusWeeks(2)
        MONTHLY -> date.plusMonths(1)
    }

    companion object {
        val Default = FORTNIGHTLY

        fun of(key: String?): CountInterval = values().firstOrNull { it.key == key } ?: Default
    }
}

/**
 * When in the day the reminder comes, as minutes after midnight. Eight o'clock unless changed:
 * first thing on a working morning, before the van is loaded rather than in the middle of a pour.
 */
const val DefaultCountReminderMinute = 8 * 60

/** How long a reminder that was seen and not acted on is left before it asks again. */
const val CountRepeatDays = 3L

private fun timeOn(day: LocalDate, minuteOfDay: Int, zone: ZoneId): Long {
    val minute = minuteOfDay.coerceIn(0, 24 * 60 - 1)
    return day.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
}

private fun dayOf(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

/** No weekday picked: the count falls [CountInterval] after the last one, whatever day that is. */
const val SameDayAsLastCount = 0

/**
 * The picked weekday nearest to [day] — up to three days either side.
 *
 * Nearest rather than next: a weekly Monday count done on the Tuesday still makes the following
 * Monday the next one, not the Monday after, and one done a day early on the Sunday does not
 * make it due again the very next morning.
 */
fun nearestWeekday(day: LocalDate, weekday: DayOfWeek): LocalDate =
    day.minusDays(3).with(TemporalAdjusters.nextOrSame(weekday))

/**
 * When the next count falls due, as epoch millis — the reminder time on the day [interval] after
 * the last count, or after [anchorAt] when there has not been one yet; moved to the nearest
 * [weekday] (ISO, Monday = 1) when one is picked. Null when reminders are off.
 */
fun countDueAt(
    interval: CountInterval,
    lastCountAt: Long,
    anchorAt: Long,
    zone: ZoneId,
    minuteOfDay: Int = DefaultCountReminderMinute,
    weekday: Int = SameDayAsLastCount,
): Long? {
    val from = if (lastCountAt > 0L) lastCountAt else anchorAt
    val natural = interval.after(dayOf(from, zone)) ?: return null
    val due = if (weekday in 1..7) nearestWeekday(natural, DayOfWeek.of(weekday)) else natural
    return timeOn(due, minuteOfDay, zone)
}

/**
 * When the phone should next say so.
 *
 * At the reminder time on the day it falls due; and if that one came and went without a count,
 * again every [CountRepeatDays] days until there is one. Never in the past: an alarm booked for a
 * time already gone goes off the moment it is booked, which after a restart would be at a random
 * minute of the day rather than the one that was picked.
 */
fun nextCountReminderAt(
    dueAt: Long,
    notifiedAt: Long,
    now: Long,
    zone: ZoneId,
    minuteOfDay: Int = DefaultCountReminderMinute,
): Long {
    var day = if (notifiedAt >= dueAt) dayOf(notifiedAt, zone).plusDays(CountRepeatDays) else dayOf(dueAt, zone)
    while (timeOn(day, minuteOfDay, zone) <= now) day = day.plusDays(1)
    return timeOn(day, minuteOfDay, zone)
}
