package com.conwic.mixmaster.ui.components

import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Whether the phone has been told to keep still — animations turned off in accessibility
 * settings.
 *
 * Asked once per screen and answered the same way everywhere, so a phone that is keeping still
 * keeps still in all of the app rather than in the parts that remembered to check.
 */
fun motionOff(context: Context): Boolean = runCatching {
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
}.getOrDefault(false)

/** The same question from inside a composable. */
@Composable
fun rememberMotionOff(): Boolean {
    val context = LocalContext.current
    return remember(context) { motionOff(context) }
}
