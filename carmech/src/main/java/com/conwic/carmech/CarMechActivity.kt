package com.conwic.carmech

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Car Mech: the game's page, full screen, and nothing else. The page is shipped in the app's assets
 * with its 3D engine and fonts, so it plays anywhere; the only thing it keeps is the career and its
 * switches, in the page's own storage. Nothing leaves the phone.
 */
class CarMechActivity : ComponentActivity() {
    private var web: WebView? = null
    private var voice: CarMechVoice? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val speech = CarMechVoice(this)
        voice = speech
        val view = WebView(this).apply {
            settings.javaScriptEnabled = true
            // the career, the best week and the sound switches are kept in localStorage
            settings.domStorageEnabled = true
            // the sound starts on the first tap anyway; this only stops the view holding it back
            settings.mediaPlaybackRequiresUserGesture = false
            setBackgroundColor(Color.rgb(5, 8, 15))
            isVerticalScrollBarEnabled = false
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            // a shift is long and the screen should not go dark in the middle of a bolt
            keepScreenOn = true
        }
        web = view
        // a customer finished a line: the page may say the next one
        speech.onDone = { view.post { view.evaluateJavascript("window.cmVoiceDone && window.cmVoiceDone()", null) } }
        view.addJavascriptInterface(Bridge(view, speech) { view.post { finish() } }, "CarMechApp")
        view.loadUrl("file:///android_asset/carmech/index.html")

        // From Android 15 an app is drawn edge to edge, under the camera's cut-out too, and the
        // HUD's corners would sit under it on a phone held sideways. The page keeps clear of it.
        val frame = FrameLayout(this)
        frame.setBackgroundColor(Color.rgb(5, 8, 15))
        frame.addView(view, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        ViewCompat.setOnApplyWindowInsetsListener(frame) { v, insets ->
            val cutOut = insets.getInsets(WindowInsetsCompat.Type.displayCutout())
            v.setPadding(cutOut.left, cutOut.top, cutOut.right, cutOut.bottom)
            insets
        }
        setContentView(frame)
        fullScreen()

        // Mid-shift the back gesture pauses the game, since a thumb on a bolt near the edge of the
        // glass is exactly the swipe that means "back"; on the title screen it leaves.
        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    view.evaluateJavascript("window.cmBack ? window.cmBack() : 'quit'") { answer ->
                        if (answer == null || answer.contains("quit")) finish()
                    }
                }
            },
        )
    }

    override fun onPause() {
        // the page's sound runs on its own clock, which pausing the view does not stop
        web?.evaluateJavascript("window.cmSleep && window.cmSleep(true)", null)
        voice?.hush()
        web?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web?.onResume()
        web?.evaluateJavascript("window.cmSleep && window.cmSleep(false)", null)
        // Android does not always keep the bars hidden across a trip to another app
        fullScreen()
    }

    override fun onDestroy() {
        voice?.let {
            it.onDone = null
            it.shutdown()
        }
        voice = null
        web?.let {
            it.stopLoading()
            it.destroy()
        }
        web = null
        super.onDestroy()
    }

    /** The status and navigation bars hidden; a swipe from the edge brings them back for a moment. */
    private fun fullScreen() {
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        bars.hide(WindowInsetsCompat.Type.systemBars())
    }
}

/**
 * What the page may ask of the app: to be closed, to have a customer's line said out loud in one of
 * the phone's voices, and a buzz in the hand. Called on the page's own thread.
 */
private class Bridge(
    private val view: WebView,
    private val voice: CarMechVoice,
    private val onQuit: () -> Unit,
) {
    @JavascriptInterface
    fun quit() = onQuit()

    @JavascriptInterface
    fun speak(text: String, pitch: Float, rate: Float, name: String) = voice.say(text.take(600), pitch, rate, name)

    /** The English voices the phone can speak in, as JSON: name, language tag, and "f" or "m" when it's known. */
    @JavascriptInterface
    fun voices(): String = voice.list()

    /** Whether anything said would be heard yet: false until an engine with English in it is up. */
    @JavascriptInterface
    fun canSpeak(): Boolean = voice.speaks()

    @JavascriptInterface
    fun hush() = voice.hush()

    /**
     * The phone's own short buzzes, the ones its keyboard uses: crisp, and no permission needed.
     * "tick" for a ratchet click, "tap" for a button, "heavy" for a bolt coming off or a zap,
     * "ok" and "bad" for a job done well or badly.
     */
    @JavascriptInterface
    fun haptic(kind: String) {
        val feel = when (kind) {
            "tick" -> HapticFeedbackConstants.CLOCK_TICK
            "heavy" -> HapticFeedbackConstants.LONG_PRESS
            "ok" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
            "bad" -> if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS
            else -> HapticFeedbackConstants.VIRTUAL_KEY
        }
        view.post { view.performHapticFeedback(feel) }
    }
}
