package com.conwic.mixmaster.ui.components

import android.view.ViewTreeObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Whether the keyboard is up.
 *
 * Asked of the window rather than of Compose's insets: the app is not drawn edge to edge, so the
 * window is resized for the keyboard and Compose is never told its height. Checked on every
 * layout, which is when a resize for the keyboard lands.
 */
@Composable
fun rememberKeyboardOpen(): Boolean {
    val view = LocalView.current
    var open by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val listener = ViewTreeObserver.OnGlobalLayoutListener {
            open = ViewCompat.getRootWindowInsets(view)?.isVisible(WindowInsetsCompat.Type.ime()) == true
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
    return open
}
