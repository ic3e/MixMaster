package com.conwic.pourday.game

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

/**
 * Pour Day, ready to put on screen: the game's page in a WebView, wired to the phone — its voices,
 * Nearby for co-workers, and a way out.
 *
 * The game is a web page shipped inside the app (assets/pourday) — the 3D engine and the font with
 * it — so it plays on a site with no signal at all. Nothing leaves the phone except what co-workers
 * playing together send each other; the only thing it keeps is its best score and its switches, in
 * the page's own storage.
 *
 * Two apps show it: MixMaster, from Break time, and the Pour Day app, for people outside the
 * company who should get the game and nothing else. Each makes one of these with its own name
 * (the page says "Back to MixMaster" or just "Quit"), puts [view] on screen and passes on the
 * phone's back gesture and its own pausing, resuming and closing.
 *
 * [askPermissions] asks the person for Android permissions and answers true when every one was
 * allowed: "Nearby devices" is asked for the first time somebody hosts or joins a day.
 *
 * [updates] is for an app that fetches new versions of itself from inside the game: the Pour Day
 * app does, on its title screen. MixMaster has its own updater in Settings and passes none.
 */
@SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
class GameScreen(
    context: Context,
    appName: String,
    onQuit: () -> Unit,
    askPermissions: (permissions: Array<String>, then: (Boolean) -> Unit) -> Unit,
    private val updates: GameUpdates? = null,
) {
    private val voice = GameVoice(context)
    private val net = GameNet(context)

    val view: WebView = WebView(context).apply {
        settings.javaScriptEnabled = true
        // The best score and the sound switch are kept in localStorage.
        settings.domStorageEnabled = true
        // The sound starts on the first tap anyway; this only stops the view holding it back.
        settings.mediaPlaybackRequiresUserGesture = false
        setBackgroundColor(Color.rgb(16, 20, 26))
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        // Waiting on concrete is the whole point; the screen should not go dark on it.
        keepScreenOn = true
    }

    init {
        // where an update has got to goes to the page as it changes; the page asks too when it loads
        updates?.let { u ->
            u.onChange = {
                view.post { view.evaluateJavascript("window.pdUpdate && window.pdUpdate(${JSONObject.quote(u.state)})", null) }
            }
        }
        // what Nearby hears goes to the page as an event
        net.emit = { json -> view.evaluateJavascript("window.pdNet && window.pdNet.onEvent(${JSONObject.quote(json)})", null) }
        // Called from the page's own thread, so handed over to the main one.
        view.addJavascriptInterface(
            GameBridge(
                appName = appName,
                onQuit = { view.post { onQuit() } },
                voice = voice,
                net = net,
                onMain = { f -> view.post { f() } },
                withPermissions = { then -> askPermissions(GameNet.permissions(), then) },
                updates = updates,
            ),
            "PourDayApp",
        )
        view.loadUrl("file:///android_asset/pourday/index.html")
    }

    /**
     * The phone's back gesture. Mid-shift the page pauses rather than leaves, because a thumb
     * looking round near the edge of the glass is exactly the swipe that means "back"; on its title
     * and end screens it answers that leaving is fine, and [leave] is called.
     */
    fun back(leave: () -> Unit) {
        view.evaluateJavascript("window.pdBack ? window.pdBack() : 'quit'") { answer ->
            if (answer == null || answer.contains("quit")) leave()
        }
    }

    /**
     * The app going into the background: a 3D scene left drawing there eats the battery. The page's
     * sound runs on its own clock, which pausing the view does not stop, so the page is told too.
     */
    fun pause() {
        view.evaluateJavascript("window.pdSleep && window.pdSleep(true)", null)
        voice.hush()
        view.onPause()
    }

    fun resume() {
        view.onResume()
        view.evaluateJavascript("window.pdSleep && window.pdSleep(false)", null)
    }

    /** Gone for good: the voices and any co-workers let go of, and the page with them. */
    fun close() {
        updates?.onChange = null
        voice.shutdown()
        net.stop()
        view.stopLoading()
        view.destroy()
    }

    companion object {
        /**
         * Which game this is: a hash of everything that makes up Pour Day. Phones play together only
         * on the same one, and the Pour Day app offers an update only when it has changed.
         */
        val stamp: String get() = BuildConfig.GAME_STAMP
    }
}

/**
 * An app that fetches new versions of itself from inside the game. The page shows [state] on its
 * title screen and calls [next] when its button is tapped.
 */
interface GameUpdates {
    /**
     * Where it has got to, as JSON for the page: `s` is "ready", "downloading" (with `p`, the
     * percent), "permission", "install" or "failed", and `v` the version; `{}` for nothing to say.
     */
    val state: String

    /** Take the next step: download, ask for the install permission, install, or try again. */
    fun next()

    /** Called, from any thread, whenever [state] changes. */
    var onChange: (() -> Unit)?
}

/**
 * What the game may ask of the app: to be closed, to say a line out loud in one of the phone's
 * voices, and to talk to co-workers' phones. It only ever loads its own page.
 */
private class GameBridge(
    private val appName: String,
    private val onQuit: () -> Unit,
    private val voice: GameVoice,
    private val net: GameNet,
    private val onMain: (() -> Unit) -> Unit,
    private val withPermissions: ((Boolean) -> Unit) -> Unit,
    private val updates: GameUpdates?,
) {
    @JavascriptInterface
    fun quit() = onQuit()

    /** Which app the game is in, for its way out: "Back to MixMaster", or just "Quit". */
    @JavascriptInterface
    fun appName(): String = appName

    /** The game's own version: phones playing together have to carry the same game. */
    @JavascriptInterface
    fun version(): String = GameScreen.stamp

    @JavascriptInterface
    fun netHost(name: String) = onMain { withPermissions { ok -> if (ok) net.host(name.take(24)) else net.denied() } }

    @JavascriptInterface
    fun netJoin(name: String) = onMain { withPermissions { ok -> if (ok) net.join(name.take(24)) else net.denied() } }

    @JavascriptInterface
    fun netConnect(endpointId: String) = onMain { net.connect(endpointId) }

    @JavascriptInterface
    fun netSend(data: String) = onMain { net.send(data) }

    @JavascriptInterface
    fun netSendTo(endpointId: String, data: String) = onMain { net.sendTo(endpointId, data) }

    @JavascriptInterface
    fun netLeave() = onMain { net.stop() }

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

    @JavascriptInterface
    fun updateState(): String = updates?.state ?: "{}"

    @JavascriptInterface
    fun updateNext() = onMain { updates?.next() }
}
