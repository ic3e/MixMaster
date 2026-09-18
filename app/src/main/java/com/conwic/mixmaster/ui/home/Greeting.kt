package com.conwic.mixmaster.ui.home

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

fun greetingFor(part: DayPart): String = when (part) {
    DayPart.MORNING -> "Good morning"
    DayPart.AFTERNOON -> "Good afternoon"
    DayPart.EVENING -> "Good evening"
    DayPart.NIGHT -> "Still up"
}

private val quips: Map<DayPart, List<String>> = mapOf(
    DayPart.MORNING to listOf(
        "Let's get those hands dusty.",
        "The floor isn't going to pour itself.",
        "Coffee first. Then chemistry.",
        "Somewhere out there, a slab is waiting.",
        "Measure twice, mix once.",
        "Today's forecast: dust, with a chance of overtime.",
        "Concrete doesn't care that you're tired.",
        "New day, same ratios.",
        "Boots on, brain on.",
        "Let's make something flat and permanent.",
        "The bags won't lift themselves — but the maths is done.",
        "Straight lines, strong coffee.",
    ),
    DayPart.AFTERNOON to listOf(
        "Half the day gone, half the floor to go.",
        "Keep moving — pot life is shorter than your patience.",
        "Second wind, second coat.",
        "The client will change their mind at half four. Plan accordingly.",
        "Nothing cures faster just because you're in a hurry.",
        "Afternoon: where the words \"quick job\" go to die.",
        "Closer to done than you were this morning.",
        "Mix it right and you only do it once.",
        "Dust now, satisfaction later.",
        "That slab won't level itself.",
        "Still standing? Good. Keep going.",
        "Push on — the kettle isn't going anywhere.",
    ),
    DayPart.EVENING to listOf(
        "Tools down soon. Numbers first.",
        "Log it now, or forget it forever.",
        "Long day. Straight lines anyway.",
        "Nearly there — resist the urge to eyeball it.",
        "Tomorrow's you would love it if today's you wrote it down.",
        "The floor stays. The ache fades.",
        "Wrapping up? Get the usage logged.",
        "Last mix of the day — make it count.",
        "Good work. Now go clean the mixer.",
        "Evening maths beats morning guesswork.",
        "One more coat, or call it a day?",
        "Finish strong, finish flat.",
    ),
    DayPart.NIGHT to listOf(
        "Still up? So is the concrete.",
        "Night shift maths, same as day shift maths.",
        "Nobody pours at this hour by choice.",
        "Quiet site, clean mix.",
        "Overtime: sets faster than the sealer.",
        "Go home. The slab will still be there tomorrow.",
        "Late one. Get the ratios right anyway.",
        "The only thing curing right now is you, slowly.",
        "Sleep is a consumable. Budget accordingly.",
        "Mixing at this hour? Double-check that pot life.",
        "Tomorrow starts in a worryingly small number of hours.",
        "Dark outside, level inside.",
    ),
)

/**
 * Picks a line from [date] and [part] rather than at random, so it holds still while the
 * screen is open and turns over as the day moves on. Seeding a PRNG rather than taking the
 * seed modulo the pool size keeps every line reachable whatever the pool size happens to be.
 */
fun quipFor(date: LocalDate, part: DayPart): String {
    val pool = quips.getValue(part)
    val seed = date.toEpochDay() * DayPart.entries.size + part.ordinal
    return pool[Random(seed).nextInt(pool.size)]
}
