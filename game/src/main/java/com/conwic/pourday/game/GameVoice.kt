package com.conwic.pourday.game

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

private const val GOOGLE_TTS = "com.google.android.tts"

/**
 * The phone's text-to-speech, for the game's lines. They are written in English, so it gathers the
 * English voices that are on the phone — British, American, Australian, Indian, whatever is
 * installed — for the game to cast its characters from. If the phone's own engine has only one or
 * two and Google's engine is on the phone too, it uses Google's, which usually has a handful. Until
 * an engine is up, or if the phone has none, it simply says nothing.
 */
internal class GameVoice(context: Context) {
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
