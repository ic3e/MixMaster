package com.conwic.mixmaster.ui.home

import androidx.annotation.ArrayRes
import androidx.annotation.StringRes
import com.conwic.mixmaster.R
import java.time.LocalDate
import java.time.LocalTime
import kotlin.random.Random

enum class DayPart { MORNING, AFTERNOON, EVENING, NIGHT }

fun dayPartFor(time: LocalTime): DayPart = when (time.hour) {
    in 5..11 -> DayPart.MORNING
    in 12..17 -> DayPart.AFTERNOON
    in 18..21 -> DayPart.EVENING
    else -> DayPart.NIGHT
}

@StringRes
fun greetingRes(part: DayPart): Int = when (part) {
    DayPart.MORNING -> R.string.greeting_morning
    DayPart.AFTERNOON -> R.string.greeting_afternoon
    DayPart.EVENING -> R.string.greeting_evening
    DayPart.NIGHT -> R.string.greeting_night
}

/**
 * The lines are resources rather than Kotlin, so each language gets its own written for it.
 * Translating a joke word for word leaves it flat, and the three lists don't have to be the
 * same length — [quipIndex] works off whatever the chosen language actually has.
 */
@ArrayRes
fun quipArrayRes(part: DayPart): Int = when (part) {
    DayPart.MORNING -> R.array.quips_morning
    DayPart.AFTERNOON -> R.array.quips_afternoon
    DayPart.EVENING -> R.array.quips_evening
    DayPart.NIGHT -> R.array.quips_night
}

/**
 * Picks a line from [date] and [part] rather than at random, so it holds still while the
 * screen is open and turns over as the day moves on. Seeding a PRNG rather than taking the
 * seed modulo [poolSize] keeps every line reachable whatever the pool size happens to be.
 */
fun quipIndex(date: LocalDate, part: DayPart, poolSize: Int): Int {
    if (poolSize <= 0) return 0
    val seed = date.toEpochDay() * DayPart.entries.size + part.ordinal
    return Random(seed).nextInt(poolSize)
}
