package com.conwic.mixmaster.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.fragment.app.FragmentActivity

/**
 * The hosting activity, passed down explicitly.
 *
 * The app overrides [androidx.compose.ui.platform.LocalContext] with a locale-wrapped context so
 * the chosen language reaches every stringResource. That wrapper comes from
 * createConfigurationContext, which builds a fresh context with no link back to the activity —
 * so anything that needs the real one (BiometricPrompt needs a FragmentActivity) has to be
 * handed it rather than casting whatever LocalContext happens to be.
 */
val LocalAppActivity = staticCompositionLocalOf<FragmentActivity?> { null }
