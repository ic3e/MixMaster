package com.conwic.mixmaster.ui.breaktime

import android.annotation.SuppressLint
import android.content.Context
import android.speech.tts.TextToSpeech
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.conwic.mixmaster.ui.LocalAppActivity
import java.util.Locale

/**
 * Pour Day: a first-person concrete shift, for the hours the slab takes to harden.
 *
 * The game is a web page shipped inside the app (assets/pourday) — the 3D engine and the font
 * with it — so it plays on a site with no signal at all. Nothing leaves the phone; the only thing
 * it keeps is its best score and its switches, in the page's own storage.
 *
 * The people in it talk out loud through the phone's own text-to-speech, handed over from here:
 * the WebView has no speech of its own.
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
    val appContext = LocalContext.current.applicationContext
    val voice = remember { GameVoice(appContext) }
    DisposableEffect(voice) { onDispose { voice.shutdown() } }

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
    DisposableEffect(activity, web, voice) {
        val view = web
        if (view == null || activity == null) return@DisposableEffect onDispose { }
        val lifecycle = activity.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // The page's sound runs on its own clock, which pausing the view does not stop.
                    view.evaluateJavascript("window.pdSleep && window.pdSleep(true)", null)
                    voice.hush()
                    view.onPause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    view.onResume()
                    view.evaluateJavascript("window.pdSleep && window.pdSleep(false)", null)
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
                // The best score and the sound switch are kept in localStorage.
                settings.domStorageEnabled = true
                // The sound starts on the first tap anyway; this only stops the view holding it back.
                settings.mediaPlaybackRequiresUserGesture = false
                setBackgroundColor(android.graphics.Color.rgb(16, 20, 26))
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                // Waiting on concrete is the whole point; the screen should not go dark on it.
                keepScreenOn = true
                // Called from the page's own thread, so handed over to the main one.
                addJavascriptInterface(GameBridge(onQuit = { post { exit() } }, voice = voice), "MixMaster")
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

/**
 * What the game may ask of the app: to be closed, and to say a line out loud. It only ever loads
 * its own page.
 */
private class GameBridge(private val onQuit: () -> Unit, private val voice: GameVoice) {
    @JavascriptInterface
    fun quit() = onQuit()

    @JavascriptInterface
    fun speak(text: String, pitch: Float, rate: Float) = voice.say(text.take(600), pitch, rate)

    @JavascriptInterface
    fun hush() = voice.hush()
}

/**
 * The phone's text-to-speech, for the game's lines. They are written in English, so it asks for a
 * British voice, then any English one; each character gets its own pitch and pace. Until the engine
 * is up, or if the phone has none, it simply says nothing.
 */
private class GameVoice(context: Context) : TextToSpeech.OnInitListener {
    @Volatile private var ready = false
    private val tts = TextToSpeech(context, this)

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val uk = tts.setLanguage(Locale.UK)
        if (uk == TextToSpeech.LANG_MISSING_DATA || uk == TextToSpeech.LANG_NOT_SUPPORTED) tts.setLanguage(Locale.US)
        ready = true
    }

    fun say(text: String, pitch: Float, rate: Float) {
        if (!ready) return
        tts.setPitch(pitch.coerceIn(0.5f, 2f))
        tts.setSpeechRate(rate.coerceIn(0.5f, 2f))
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pourday")
    }

    fun hush() {
        if (ready) tts.stop()
    }

    fun shutdown() {
        ready = false
        tts.stop()
        tts.shutdown()
    }
}
