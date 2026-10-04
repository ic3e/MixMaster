package com.conwic.carmech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private const val GOOGLE_TTS = "com.google.android.tts"

/**
 * The phone's text-to-speech, for the customers' lines. They are written in English, so it gathers
 * the English voices on the phone for the game to cast its customers from — every customer is meant
 * to sound like nobody else. When Google's engine is on the phone beside the phone's own (Samsung's,
 * say), both are started and their voices pooled: each line goes to the engine that has the voice
 * asked for. Until an engine is up, or if the phone has none, it simply says nothing.
 *
 * The same arrangement as Pour Day's voices, which have been through the phones already.
 */
internal class CarMechVoice(context: Context) {
    private val app = context.applicationContext

    /**
     * An engine that came up, with the English voices it has, the voice it starts with, and whether
     * it took English at all — one that lists no English voice by name can still read in its own.
     */
    private class Engine(val tts: TextToSpeech, val voices: Map<String, Voice>, val base: Voice?, val english: Boolean)

    @Volatile private var engines: List<Engine> = emptyList()
    @Volatile private var closed = false

    /** Told when the line being said is finished or cut off, so the page can say the next one then. */
    @Volatile var onDone: (() -> Unit)? = null
    @Volatile private var current = ""
    private var count = 0

    private val progress = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) = Unit
        override fun onDone(utteranceId: String?) = finished(utteranceId)
        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) = finished(utteranceId)
        override fun onError(utteranceId: String?, errorCode: Int) = finished(utteranceId)
        override fun onStop(utteranceId: String?, interrupted: Boolean) = finished(utteranceId)
    }

    /** Only the latest line counts: the one a new line flushed reports being stopped, and that's not news. */
    private fun finished(id: String?) {
        if (id != null && id == current) onDone?.invoke()
    }

    init {
        open(null)
    }

    /** An engine starting up: the phone's own when [engine] is null. */
    private inner class Starter(private val engine: String?) : TextToSpeech.OnInitListener {
        var made: TextToSpeech? = null

        override fun onInit(status: Int) {
            // a failure can be reported before the constructor has returned: nothing to keep then
            val t = made ?: return
            if (status == TextToSpeech.SUCCESS) adopt(t, engine) else t.shutdown()
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
            // the phone's own is kept whatever it has: with no English voices it still reads in its default one
            engines = engines + ready(t, found)
            val google = runCatching { t.defaultEngine != GOOGLE_TTS && t.engines.any { it.name == GOOGLE_TTS } }.getOrDefault(false)
            if (google) open(GOOGLE_TTS)
        } else if (found.keys.any { name -> engines.none { name in it.voices } }) {
            engines = engines + ready(t, found)
        } else {
            t.shutdown()
        }
    }

    private fun ready(t: TextToSpeech, found: Map<String, Voice>): Engine {
        val us = t.setLanguage(Locale.US)
        val took = if (us == TextToSpeech.LANG_MISSING_DATA || us == TextToSpeech.LANG_NOT_SUPPORTED) t.setLanguage(Locale.UK) else us
        val english = took != TextToSpeech.LANG_MISSING_DATA && took != TextToSpeech.LANG_NOT_SUPPORTED
        t.setOnUtteranceProgressListener(progress)
        return Engine(t, found, runCatching { t.voice }.getOrNull(), english)
    }

    /** English voices that are on the phone and work without a network. */
    private fun english(t: TextToSpeech): Map<String, Voice> = runCatching {
        val all = t.voices.orEmpty().filter { v ->
            v.locale.language == "en" && !v.isNetworkConnectionRequired &&
                !v.features.orEmpty().contains(TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED)
        }
        // the very-low and low quality ones are the robot voices: left out while anything better is there
        val good = all.filter { it.quality >= Voice.QUALITY_NORMAL }
        good.ifEmpty { all }.sortedBy { it.name }.associateBy { it.name }
    }.getOrDefault(emptyMap())

    fun list(): String {
        val out = JSONArray()
        val seen = HashSet<String>()
        engines.forEach { e ->
            e.voices.values.forEach { v ->
                if (seen.add(v.name)) out.put(JSONObject().put("n", v.name).put("l", v.locale.toLanguageTag()).put("g", genderOf(v.name)))
            }
        }
        return out.toString()
    }

    fun say(text: String, pitch: Float, rate: Float, name: String) {
        val all = engines
        val e = all.firstOrNull { name in it.voices } ?: all.firstOrNull { it.english } ?: all.firstOrNull() ?: return
        // the new line is the one that counts before anything is stopped: the line cut off to make
        // room for it says it stopped, and that must not pass for this one being finished
        val id = "carmech${++count}"
        current = id
        // one voice at a time, whichever engine it lives in
        all.forEach { if (it !== e) it.tts.stop() }
        val v = e.voices[name] ?: e.base
        if (v != null) runCatching { e.tts.setVoice(v) }
        e.tts.setPitch(pitch.coerceIn(0.5f, 2f))
        e.tts.setSpeechRate(rate.coerceIn(0.5f, 2f))
        e.tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    }

    /** Whether a line in English would be heard: an engine is up that has an English voice, named or its own. */
    fun speaks(): Boolean = engines.any { it.voices.isNotEmpty() || it.english }

    fun hush() {
        // hushed on purpose: nothing to report
        current = ""
        engines.forEach { it.tts.stop() }
    }

    @Synchronized
    fun shutdown() {
        closed = true
        val all = engines
        engines = emptyList()
        all.forEach {
            it.tts.stop()
            it.tts.shutdown()
        }
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
