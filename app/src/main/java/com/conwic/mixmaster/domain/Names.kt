package com.conwic.mixmaster.domain

import java.util.Locale

/**
 * What two spellings of one name have in common: capitals, spaces and punctuation left out, so
 * "Ideal Work", "ideal work", "Ideal-Work" and "IDEALWORK" all come to "idealwork".
 */
fun nameKey(name: String): String = name.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

/**
 * The spelling already in use when [typed] is the same name written differently; otherwise
 * [typed], trimmed and with its runs of spaces closed up.
 *
 * Checked on save as well as offered while typing: a suggestion can be ignored, and "ardex"
 * saved next to "Ardex" is a second brand in every filter from then on.
 */
fun canonicalName(typed: String, existing: List<String>): String {
    val tidy = typed.trim().replace(Regex("\\s+"), " ")
    val key = nameKey(tidy)
    if (key.isEmpty()) return tidy
    return existing.firstOrNull { it.isNotBlank() && nameKey(it) == key } ?: tidy
}

/**
 * The names in [pool] that [typed] could be heading for, closest first: the same name spelt
 * differently, then those it starts, then those it is part of. The one it already spells
 * exactly is left out — there is nothing to offer for it.
 */
fun nameMatches(typed: String, pool: List<String>): List<String> {
    val key = nameKey(typed)
    if (key.isEmpty()) return emptyList()
    val exact = typed.trim()
    return pool
        .filter { it.isNotBlank() && it.trim() != exact }
        .distinct()
        .mapNotNull { name ->
            val other = nameKey(name)
            when {
                other == key -> 0 to name
                other.startsWith(key) -> 1 to name
                other.contains(key) -> 2 to name
                else -> null
            }
        }
        .sortedWith(compareBy({ it.first }, { it.second.lowercase(Locale.ROOT) }))
        .map { it.second }
}

/** The name in [others] that [typed] already is, however it is spelt there, or null. */
fun sameNameIn(typed: String, others: List<String>): String? {
    val key = nameKey(typed)
    if (key.isEmpty()) return null
    return others.firstOrNull { nameKey(it) == key }
}

/** Every different name in [names], blanks left out, in the order a list shows them. */
fun distinctNames(names: List<String>): List<String> =
    names.map { it.trim() }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase(Locale.ROOT) }
