package com.conwic.mixmaster.domain

/**
 * Looks like a host: letters/digits/dashes in at least two dot-separated parts, an optional
 * port, and anything after the first slash.
 */
private val HOST = Regex("""^[\w-]+(\.[\w-]+)+(:\d+)?([/?#].*)?$""")

/**
 * The datasheet link as something the system can actually open, or null.
 *
 * The field is free text — someone typed "test datasheet" into it — and handing that to the
 * browser throws ActivityNotFoundException, which took the app down. A link that can't be
 * opened isn't offered as one.
 *
 * A bare "conwic.fi/x.pdf" is a link people mean, so it gets https:// rather than a refusal.
 */
fun openableUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
    val withoutScheme = trimmed
        .removePrefix("https://")
        .removePrefix("http://")
    if (!HOST.matches(withoutScheme)) return null
    return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
}
