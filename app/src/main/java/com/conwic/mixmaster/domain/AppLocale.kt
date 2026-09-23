package com.conwic.mixmaster.domain

import java.util.Locale

/**
 * The locale the app is currently running in.
 *
 * Dates are formatted by java.time, which knows nothing about Android resources and would
 * otherwise have to go through Locale.getDefault(). That default is not ours to keep: the
 * framework resets it to the system's locale whenever it pushes a configuration change, which
 * would quietly put an Estonian weekday back under an English heading mid-session.
 *
 * Set once wherever the language is applied, and read by [formatLongDay] and friends.
 */
object AppLocale {
    @Volatile
    var current: Locale = Locale.getDefault()
}
