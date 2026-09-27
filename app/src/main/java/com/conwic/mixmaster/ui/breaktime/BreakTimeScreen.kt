package com.conwic.mixmaster.ui.breaktime

import android.annotation.SuppressLint
import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
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
import org.json.JSONArray
import org.json.JSONObject

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
 * What the game may ask of the app: to be closed, and to say a line out loud, in one of the phone's
 * voices. It only ever loads its own page.
 */
private class GameBridge(private val onQuit: () -> Unit, private val voice: GameVoice) {
    @JavascriptInterface
    fun quit() = onQuit()

    @JavascriptInterface
    fun speak(text: String, pitch: Float, rate: Float) = voice.say(text.take(600), pitch, rate, "")

    /** The same, in a particular voice: a name from [voices]. */
    @JavascriptInterface
    fun speakAs(text: String, pitch: Float, rate: Float, name: String) = voice.say(text.take(600), pitch, rate, name)

    /** The English voices the phone can speak in, as JSON: name, language tag, and "f" or "m" when it's known. */
    @JavascriptInterface
    fun voices(): String = voice.list()

    @JavascriptInterface
    fun hush() = voice.hush()
}

private const val GOOGLE_TTS = "com.google.android.tts"

/**
 * The phone's text-to-speech, for the game's lines. They are written in English, so it gathers the
 * English voices that are on the phone — British, American, Australian, Indian, whatever is
 * installed — for the game to cast its characters from. If the phone's own engine has only one or
 * two and Google's engine is on the phone too, it uses Google's, which usually has a handful. Until
 * an engine is up, or if the phone has none, it simply says nothing.
 */
private class GameVoice(context: Context) {
    private val app = context.applicationContext
    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var voices: Map<String, Voice> = emptyMap()
    @Volatile private var base: Voice? = null
    @Volatile private var closed = false

    init {
        open(null)
    }

    /** An engine starting up: the phone's own when [engine] is null. */
    private inner class Starter(private val engine: String?) : TextToSpeech.OnInitListener {
        var made: TextToSpeech? = null

        override fun onInit(status: Int) {
            // a failure can be reported before the constructor has returned: nothing to keep then
            val t = made ?: return
            if (status == TextToSpeech.SUCCESS) adopt(t, engine) else if (engine != null) t.shutdown()
        }
    }

    private fun open(engine: String?) {
        val starter = Starter(engine)
        starter.made = TextToSpeech(app, starter, engine)
    }

    @Synchronized
    private fun adopt(t: TextToSpeech, engine: String?) {
        if (closed) {
            t.shutdown()
            return
        }
        val found = english(t)
        if (engine == null) {
            use(t, found)
            val google = runCatching { t.defaultEngine != GOOGLE_TTS && t.engines.any { it.name == GOOGLE_TTS } }.getOrDefault(false)
            if (found.size < 4 && google) open(GOOGLE_TTS)
        } else if (found.size > voices.size) {
            val old = tts
            use(t, found)
            old?.shutdown()
        } else {
            t.shutdown()
        }
    }

    private fun use(t: TextToSpeech, found: Map<String, Voice>) {
        val uk = t.setLanguage(Locale.UK)
        if (uk == TextToSpeech.LANG_MISSING_DATA || uk == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.US)
        base = runCatching { t.voice }.getOrNull()
        voices = found
        tts = t
    }

    /** English voices that are on the phone and work without a network. */
    private fun english(t: TextToSpeech): Map<String, Voice> = runCatching {
        t.voices.orEmpty()
            .filter { v ->
                v.locale.language == "en" && !v.isNetworkConnectionRequired &&
                    !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
            }
            .sortedBy { it.name }
            .associateBy { it.name }
    }.getOrDefault(emptyMap())

    fun list(): String {
        val out = JSONArray()
        voices.values.forEach { v ->
            out.put(JSONObject().put("n", v.name).put("l", v.locale.toLanguageTag()).put("g", genderOf(v.name)))
        }
        return out.toString()
    }

    fun say(text: String, pitch: Float, rate: Float, name: String) {
        val t = tts ?: return
        val v = voices[name] ?: base
        if (v != null) runCatching { t.setVoice(v) }
        t.setPitch(pitch.coerceIn(0.5f, 2f))
        t.setSpeechRate(rate.coerceIn(0.5f, 2f))
        t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "pourday")
    }

    fun hush() {
        tts?.stop()
    }

    @Synchronized
    fun shutdown() {
        closed = true
        val t = tts
        tts = null
        t?.stop()
        t?.shutdown()
    }
}

/** Voices don't say whether they're a man's or a woman's; the names of the common ones give it away. */
private fun genderOf(name: String): String {
    val n = name.lowercase(Locale.ROOT)
    Regex("smt([fm])").find(n)?.let { return it.groupValues[1] }
    if ("female" in n) return "f"
    if ("male" in n) return "m"
    val code = Regex("-x-([a-z]{3})").find(n)?.groupValues?.get(1) ?: return ""
    return GOOGLE_VOICE_GENDER[code] ?: ""
}

/** Google's English voices, by the three letters in their names. */
private val GOOGLE_VOICE_GENDER = mapOf(
    "sfg" to "f", "iob" to "f", "iog" to "f", "tpc" to "f", "tpf" to "f", "iol" to "m", "iom" to "m", "tpd" to "m",
    "gba" to "f", "gbc" to "f", "gbg" to "f", "gbb" to "m", "gbd" to "m", "rjs" to "m",
    "aua" to "f", "auc" to "f", "aub" to "m", "aud" to "m",
    "ena" to "f", "enc" to "f", "end" to "m", "ene" to "m",
)
