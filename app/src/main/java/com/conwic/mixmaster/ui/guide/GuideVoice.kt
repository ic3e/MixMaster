package com.conwic.mixmaster.ui.guide

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale

/**
 * The guide's narrator: the phone's own text-to-speech, reading each caption in the app's language.
 *
 * Whether there is a voice at all is the phone's business — an Estonian voice is not on every
 * phone. Without one, [available] stays false and the guide runs on its captions alone, which is
 * how a video with the sound off works anyway.
 */
internal class GuideVoice(context: Context, private val locale: Locale) {

    /** A voice for the app's language came up. */
    var available by mutableStateOf(false)
        private set

    /** Still reading the last caption: the guide waits for it before moving on. */
    var speaking by mutableStateOf(false)
        private set

    private var tts: TextToSpeech? = null
    @Volatile private var current = ""
    private var count = 0

    init {
        tts = TextToSpeech(context.applicationContext) { status -> started(status) }
    }

    private fun started(status: Int) {
        val engine = tts ?: return
        if (status != TextToSpeech.SUCCESS) return
        val result = runCatching { engine.setLanguage(locale) }.getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) return
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
        available = true
    }

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

    fun close() {
        stop()
        runCatching { tts?.shutdown() }
        tts = null
    }
}
