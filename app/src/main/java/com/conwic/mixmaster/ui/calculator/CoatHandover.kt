package com.conwic.mixmaster.ui.calculator

import android.net.Uri
import com.conwic.mixmaster.domain.toNumberOrNull

/**
 * What the calculator was handed when it was opened from a room's build-up.
 *
 * The room's area, the coat's rate and how many passes it takes: everything the calculator
 * would otherwise ask for again. Each is nullable because a figure that was never given is not
 * the same as a zero — a zero would wipe the field.
 */
data class CoatHandover(
    val areaM2: Double? = null,
    val doseGramsPerM2: Double? = null,
    val quantity: Double? = null,
    /** The job this was opened for, as the screen names it back. Blank when opened on its own. */
    val jobLabel: String = "",
) {
    val isEmpty: Boolean
        get() = areaM2 == null && doseGramsPerM2 == null && quantity == null && jobLabel.isBlank()

    companion object {
        val None = CoatHandover()

        /** Reads what the route carried. Anything missing or unreadable simply isn't handed over. */
        fun fromRoute(area: String?, dose: String?, coats: String?, job: String?) = CoatHandover(
            areaM2 = area?.toNumberOrNull()?.takeIf { it > 0.0 },
            doseGramsPerM2 = dose?.toNumberOrNull()?.takeIf { it > 0.0 },
            quantity = coats?.toNumberOrNull()?.takeIf { it > 0.0 },
            jobLabel = readLabel(job),
        )

        private val Escape = Regex("%[0-9A-Fa-f]{2}")

        /**
         * The label goes into the route encoded — a room's name has spaces in it, and a project's
         * can have anything. Navigation hands it back decoded, but not in every version, so
         * anything still carrying an escape is decoded here rather than shown as one.
         */
        private fun readLabel(raw: String?): String {
            val text = raw.orEmpty()
            return if (Escape.containsMatchIn(text)) Uri.decode(text) else text
        }
    }
}
