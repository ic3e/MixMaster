package com.conwic.mixmaster.ui.breaktime

import android.annotation.SuppressLint
import android.view.View
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.ui.LocalAppActivity

/**
 * Pour Day: a first-person concrete shift, for the hours the slab takes to harden.
 *
 * The game is a web page shipped inside the app (assets/pourday) — the 3D engine and the font
 * with it — so it plays on a site with no signal at all. Nothing leaves the phone; the only thing
 * it keeps is its best score, in the page's own storage.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BreakTimeScreen(onExit: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler(onBack = onExit)

    // Paused with the app: a 3D scene left drawing in the background eats the battery the
    // phone needs for the rest of the shift. The activity is the owner, as in UpdateSection.
    val activity = LocalAppActivity.current
    DisposableEffect(activity, web) {
        val view = web
        val lifecycle = activity?.lifecycle
        if (view == null || lifecycle == null) return@DisposableEffect onDispose { }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view.onPause()
                Lifecycle.Event.ON_RESUME -> view.onResume()
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
