package com.conwic.mixmaster.ui.breaktime

import android.annotation.SuppressLint
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.ui.LocalAppActivity

/**
 * Pour Day: a first-person concrete shift, for the hours the slab takes to harden.
 *
 * The game is a web page shipped inside the app (assets/pourday) — the 3D engine and the font
 * with it — so it plays on a site with no signal at all. Nothing leaves the phone; the only thing
 * it keeps is its best score, in the page's own storage.
 *
 * Full screen: the status and navigation bars are hidden while it is open and come back with a
 * swipe from the edge. The way out is inside the game — its pause menu has "Back to MixMaster" —
 * and the phone's back gesture pauses rather than leaves, because a thumb looking round near the
 * edge of the glass is exactly the swipe that means "back".
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
@Composable
fun BreakTimeScreen(onExit: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    val exit by rememberUpdatedState(onExit)

    BackHandler {
        val view = web
        if (view == null) {
            exit()
        } else {
            // The page answers "quit" on its title and end screens, "paused" mid-shift.
            view.evaluateJavascript("window.pdBack ? window.pdBack() : 'quit'") { answer ->
                if (answer == null || answer.contains("quit")) exit()
            }
        }
    }

    val activity = LocalAppActivity.current
    DisposableEffect(activity) {
        val window = activity?.window ?: return@DisposableEffect onDispose { }
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
        onDispose { bars.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Paused with the app: a 3D scene left drawing in the background eats the battery the phone
    // needs for the rest of the shift. And full screen again on the way back, which Android does
    // not always keep across a trip to another app.
    DisposableEffect(activity, web) {
        val view = web
        if (view == null || activity == null) return@DisposableEffect onDispose { }
        val lifecycle = activity.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> {
                    view.onResume()
                    val window = activity.window
                    WindowCompat.getInsetsController(window, window.decorView).hide(WindowInsetsCompat.Type.systemBars())
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                // The best score is kept in localStorage.
                settings.domStorageEnabled = true
                setBackgroundColor(android.graphics.Color.rgb(16, 20, 26))
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                // Waiting on concrete is the whole point; the screen should not go dark on it.
                keepScreenOn = true
                // Called from the page's own thread, so handed over to the main one.
                addJavascriptInterface(GameBridge { post { exit() } }, "MixMaster")
                loadUrl("file:///android_asset/pourday/index.html")
                web = this
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.destroy()
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/** The one thing the game may ask of the app: to be closed. It only ever loads its own page. */
private class GameBridge(private val onQuit: () -> Unit) {
    @JavascriptInterface
    fun quit() = onQuit()
}
