package com.conwic.pourday

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.conwic.pourday.game.GameScreen
import com.conwic.pourday.game.GameUpdates
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * New versions of Pour Day, fetched from inside the game: its title screen says when one is out,
 * and one button takes it the rest of the way — download, the phone's one-time permission to
 * install, and Android's own install screen, which nothing can skip.
 *
 * CI publishes dist/pourday.json beside every build, with the stamp of the game in it. An update
 * is offered only when that stamp differs from this app's: MixMaster is rebuilt several times a
 * day, and a friend shouldn't be asked to update for a change to MixMaster's warehouse screen.
 *
 * No signal is normal on a site, and at a friend's: a check that fails says nothing at all. A
 * download that fails says so, with a way to try again.
 */
internal class PourDayUpdates(private val activity: Activity) : GameUpdates {
    @Volatile override var state: String = "{}"
        private set

    @Volatile override var onChange: (() -> Unit)? = null

    @Volatile private var found: Found? = null
    @Volatile private var file: File? = null
    @Volatile private var busy = false

    /** Sent out to the install-permission screen: on the way back, carry straight on to installing. */
    private var askedPermission = false

    private class Found(val versionName: String, val url: String, val sizeBytes: Long)

    private val folder get() = File(activity.cacheDir, "updates")

    /** The quiet look on start. */
    fun look() {
        if (busy) return
        busy = true
        thread(name = "pourday-update") {
            try {
                val json = JSONObject(fetchText(MANIFEST_URL))
                val newer = json.getInt("versionCode") > BuildConfig.VERSION_CODE && json.optString("game") != GameScreen.stamp
                if (newer) {
                    val info = Found(json.getString("versionName"), json.getString("url"), json.optLong("sizeBytes"))
                    found = info
                    // Downloaded already on an earlier run — sent out for the permission and the
                    // process died there, say — so no need to fetch it again.
                    val waiting = File(folder, "PourDay_${info.versionName}.apk")
                    if (waiting.isFile && (info.sizeBytes <= 0L || waiting.length() == info.sizeBytes)) {
                        file = waiting
                        say(if (canInstall()) "install" else "permission")
                    } else {
                        say("ready")
                    }
                }
            } catch (e: Exception) {
                // no signal, or GitHub not answering: nothing to say
            } finally {
                busy = false
            }
        }
    }

    override fun next() {
        val info = found
        when {
            info == null -> look()
            file == null -> download(info)
            !canInstall() -> {
                askedPermission = true
                runCatching {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${activity.packageName}")))
                }
            }
            else -> install()
        }
    }

    /** Back in the app, perhaps from the permission screen. */
    fun resumed() {
        if (!askedPermission || file == null) return
        askedPermission = false
        if (canInstall()) {
            say("install")
            install()
        } else {
            say("permission")
        }
    }

    private fun download(info: Found) {
        if (busy) return
        busy = true
        say("downloading", 0)
        thread(name = "pourday-download") {
            try {
                file = fetchApk(info)
                say(if (canInstall()) "install" else "permission")
            } catch (e: Exception) {
                say("failed")
            } finally {
                busy = false
            }
        }
    }

    private fun install() {
        val apk = file ?: return
        val uri = FileProvider.getUriForFile(activity, "${activity.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { activity.startActivity(intent) }.onFailure { say("failed") }
    }

    private fun canInstall(): Boolean = runCatching { activity.packageManager.canRequestPackageInstalls() }.getOrDefault(false)

    private fun say(step: String, percent: Int = 0) {
        val o = JSONObject().put("s", step)
        found?.let { o.put("v", it.versionName) }
        if (step == "downloading") o.put("p", percent)
        state = o.toString()
        onChange?.invoke()
    }

    private fun fetchText(url: String): String = open(url).run {
        try {
            check(responseCode == HttpURLConnection.HTTP_OK) { "Server answered $responseCode" }
            inputStream.bufferedReader().readText()
        } finally {
            disconnect()
        }
    }

    private fun fetchApk(info: Found): File {
        val dir = folder.apply { mkdirs() }
        // Only ever one APK waiting, so a half-finished download can't be installed later.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "PourDay_${info.versionName}.apk")
        val partial = File(dir, "${target.name}.part")
        val connection = open(info.url)
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK) { "Server answered ${connection.responseCode}" }
            val total = if (info.sizeBytes > 0) info.sizeBytes else connection.contentLengthLong
            var read = 0L
            var shown = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            // only on a whole percent: the page redraws its line for each one
                            val percent = ((read * 100) / total).toInt()
                            if (percent != shown) {
                                shown = percent
                                say("downloading", percent)
                            }
                        }
                    }
                }
            }
            // A truncated download is a broken APK that still looks like a file.
            check(total <= 0 || read == total) { "Download stopped early" }
        } finally {
            connection.disconnect()
        }
        check(partial.renameTo(target)) { "Couldn't finish saving the download" }
        return target
    }

    private fun open(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }

    companion object {
        /** CI writes this next to the Pour Day APK on every successful build. */
        private const val MANIFEST_URL = "https://raw.githubusercontent.com/ic3e/MixMaster/main/dist/pourday.json"
    }
}
