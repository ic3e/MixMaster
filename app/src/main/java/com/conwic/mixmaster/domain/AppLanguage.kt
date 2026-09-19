package com.conwic.mixmaster.domain

import java.util.Locale

/**
 * The languages MixMaster is written in.
 *
 * [label] is deliberately the language's own name rather than a translation of it: someone who
 * has the app in a language they can't read needs to find their way out, and "Eesti" is
 * recognisable from any of the three.
 */
enum class AppLanguage(val tag: String, val label: String) {
    ENGLISH("en", "English"),
    ESTONIAN("et", "Eesti"),
    FINNISH("fi", "Suomi"),
    ;

    val locale: Locale get() = Locale.forLanguageTag(tag)

    companion object {
        fun fromTag(tag: String?): AppLanguage? = entries.firstOrNull { it.tag == tag }

        /**
         * What the phone is set to, or English when that's none of the three.
         *
         * Only the starting point — once a language is chosen in Settings it is stored and
         * this stops being consulted, so changing the phone's language doesn't override
         * somebody's choice.
         */
        fun fromDevice(): AppLanguage = fromTag(Locale.getDefault().language) ?: ENGLISH
    }
}
