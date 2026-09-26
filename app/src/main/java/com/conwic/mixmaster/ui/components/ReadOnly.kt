package com.conwic.mixmaster.ui.components

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether what is on screen may be changed by whoever is looking at it.
 *
 * Provided true around a form the person may read but not change — a worker without the right
 * to change recipes, looking at one. The fields read it themselves: the text stays readable, but
 * takes no typing, a picker no longer opens, a stepper loses its buttons and a chip no longer
 * switches. A field that took typing it could never save was a form that looked broken, and
 * whatever was typed was put back by the company's server on the next sync.
 *
 * Buttons that add, remove or save are left to the screen to leave out, because only the screen
 * knows which of its buttons are those.
 */
val LocalReadOnly = compositionLocalOf { false }
