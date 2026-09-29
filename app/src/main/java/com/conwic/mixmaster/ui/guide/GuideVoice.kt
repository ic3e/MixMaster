package com.conwic.mixmaster.ui.guide

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

private const val GOOGLE_TTS = "com.google.android.tts"

/**
 * The guide's narrator: the phone's text-to-speech, reading each caption in the app's language —
 * with the most natural voice the phone has, not the one it happens to start with.
 *
 * A Samsung starts its own engine, and its voices read like a machine. Google's engine is on most
 * phones beside it, and its voices — the ones fetched over the network above all — are recorded
 * from people and sound like them. So Google's is asked first; out of its voices for the language
 * the best one is picked, a network voice when there is a connection, the best one on the phone
 * when there isn't. Where Google's engine is missing or has no voice for the language, the
 * phone's own is used.
 *
 * Whether there is a voice at all is the phone's business — an Estonian voice is not on every
 * phone. Without one, [available] stays false and the guide runs on its captions alone, which is
 * how a video with the sound off works anyway.
 */
internal class GuideVoice(context: Context, private val locale: Locale) {
    private val app = context.applicationContext

    /** A voice for the app's language came up. */
    var available by mutableStateOf(false)
        private set

    /** Still reading the last caption: the guide waits for it before moving on. */
    var speaking by mutableStateOf(false)
        private set

    @Volatile private var tts: TextToSpeech? = null
    @Volatile private var closed = false
    @Volatile private var current = ""
    private var count = 0

    init {
        open(GOOGLE_TTS)
    }

    /** An engine starting up: Google's by name, or the phone's own when [engine] is null. */
    private fun open(engine: String?) {
        val starter = Starter(engine)
        starter.made = TextToSpeech(app, starter, engine)
    }

    private inner class Starter(private val engine: String?) : TextToSpeech.OnInitListener {
        var made: TextToSpeech? = null

        override fun onInit(status: Int) {
            // a failure can be reported before the constructor has returned: nothing to keep then
            val engineUp = made ?: return
            started(engineUp, status, engine)
        }
    }

    @Synchronized
    private fun started(engine: TextToSpeech, status: Int, asked: String?) {
        if (closed) {
            engine.shutdown()
            return
        }
        val speaks = status == TextToSpeech.SUCCESS && runCatching { engine.setLanguage(locale) }
            .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            .let { it != TextToSpeech.LANG_MISSING_DATA && it != TextToSpeech.LANG_NOT_SUPPORTED }
        if (!speaks) {
            engine.shutdown()
            // Google's had nothing for this language: the phone's own may have
            if (asked != null) open(null)
            return
        }
        best(engine)?.let { voice -> runCatching { engine.setVoice(voice) } }
        // a touch under the engine's pace: a narrator, not a sat nav
        runCatching { engine.setSpeechRate(0.95f) }
        engine.setOnUtteranceProgressListener(
            object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) = finished(utteranceId)
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) = finished(utteranceId)
                override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
                override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
            },
        )
        tts = engine
        available = true
    }

    /**
     * The most natural voice the engine has for the language: the highest quality, the country's
     * own accent before another's, and a network voice when there is a connection to fetch it
     * over. One not yet downloaded is left out — it would read nothing.
     */
    private fun best(engine: TextToSpeech): Voice? {
        val online = online()
        val voices = runCatching { engine.voices }.getOrNull().orEmpty().filter { voice ->
            voice.locale.language == locale.language &&
                TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in voice.features.orEmpty() &&
                (online || !voice.isNetworkConnectionRequired)
        }
        return voices.sortedWith(
            compareByDescending<Voice> { it.quality }
                .thenByDescending { it.locale.country.equals(locale.country, ignoreCase = true) }
                .thenByDescending { it.isNetworkConnectionRequired }
                .thenBy { it.latency }
                .thenBy { it.name },
        ).firstOrNull()
    }

    private fun online(): Boolean = runCatching {
        val manager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }.getOrDefault(false)

    /** Only the latest line counts: one cut off by the next reports stopping, and that's not news. */
    private fun finished(id: String?) {
        if (id != null && id == current) speaking = false
    }

    fun say(text: String) {
        val engine = tts ?: return
        if (!available) return
        count += 1
        val id = "guide-$count"
        current = id
        speaking = true
        val result = runCatching { engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id) }.getOrDefault(TextToSpeech.ERROR)
        if (result != TextToSpeech.SUCCESS) speaking = false
    }

    fun stop() {
        current = ""
        speaking = false
        runCatching { tts?.stop() }
    }

    @Synchronized
    fun close() {
        closed = true
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
