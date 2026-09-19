package com.conwic.mixmaster.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import com.conwic.mixmaster.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** What CI published, as read from dist/latest.json. */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val url: String,
    val notes: String,
    val sizeBytes: Long,
)

sealed interface UpdateState {
    /** Nothing asked for yet. */
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class ReadyToInstall(val info: UpdateInfo, val file: File) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/**
 * Checks whether CI has published a newer build than the one running, fetches it, and hands it
 * to Android's installer.
 *
 * Deliberately a process-wide singleton rather than a ViewModel: a download shouldn't die
 * because the crew swiped to another tab or turned the phone, and there's only ever one of
 * these in flight.
 *
 * What this cannot do: install silently. Android always shows its own confirmation screen for
 * an app installing another app, and the phone has to allow this app to install at all
 * ([canInstall]). What it removes is hunting for the file in Downloads and opening it by hand.
 */
object AppUpdates {

    /** CI writes this next to the APK on every successful build. */
    private const val MANIFEST_URL =
        "https://raw.githubusercontent.com/ic3e/MixMaster/main/dist/latest.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var inFlight: Job? = null

    /** Set once a process has looked, so opening Settings repeatedly doesn't re-check. */
    private var checkedThisRun = false

    /** The quiet check on app start. Failures stay silent — no signal on site is normal. */
    fun checkOnStart(context: Context) {
        if (checkedThisRun) return
        checkedThisRun = true
        val appContext = context.applicationContext
        scope.launch {
            val found = runCatching { fetchManifest() }.getOrNull() ?: return@launch
            if (found.versionCode > BuildConfig.VERSION_CODE) {
                _state.value = alreadyFetched(appContext, found) ?: UpdateState.Available(found)
            }
        }
    }

    /** The explicit "check for updates" tap, which does report what happened. */
    fun check(context: Context) {
        if (inFlight?.isActive == true) return
        checkedThisRun = true
        val appContext = context.applicationContext
        inFlight = scope.launch {
            _state.value = UpdateState.Checking
            _state.value = runCatching { fetchManifest() }.fold(
                onSuccess = { found ->
                    when {
                        found.versionCode <= BuildConfig.VERSION_CODE -> UpdateState.UpToDate
                        else -> alreadyFetched(appContext, found) ?: UpdateState.Available(found)
                    }
                },
                onFailure = { UpdateState.Failed(readableReason(it)) },
            )
        }
    }

    fun download(context: Context, info: UpdateInfo) {
        if (inFlight?.isActive == true) return
        val appContext = context.applicationContext
        inFlight = scope.launch {
            _state.value = UpdateState.Downloading(info, 0)
            _state.value = runCatching { fetchApk(appContext, info) }.fold(
                onSuccess = { UpdateState.ReadyToInstall(info, it) },
                onFailure = { UpdateState.Failed(readableReason(it)) },
            )
        }
    }

    fun cancel() {
        inFlight?.cancel()
        _state.value = UpdateState.Idle
    }

    /** Whether the phone lets this app install another. One-time toggle in system settings. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * The one-time "allow this app to install apps" screen.
     *
     * Handed back rather than started here so the caller can launch it for a result and pick
     * up where it left off: the screen reports nothing back, so whoever launches it has to ask
     * [canInstall] again on return.
     */
    fun installPermissionIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.parse("package:${context.packageName}"),
    )

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }.onFailure {
            _state.value = UpdateState.Failed("Couldn't open the installer.")
        }
    }

    /**
     * The APK for [info] if a previous run already downloaded it whole.
     *
     * Granting the install permission means a trip out to system settings, and the process can
     * be killed there. Coming back to a 19 MB download that has to start again is how an
     * update stops feeling worth doing.
     */
    private fun alreadyFetched(context: Context, info: UpdateInfo): UpdateState? {
        val file = File(File(context.cacheDir, "updates"), "MixMaster_${info.versionName}.apk")
        val whole = file.isFile && (info.sizeBytes <= 0L || file.length() == info.sizeBytes)
        return if (whole) UpdateState.ReadyToInstall(info, file) else null
    }

    private suspend fun fetchManifest(): UpdateInfo = withContext(Dispatchers.IO) {
        val text = open(MANIFEST_URL).use { connection ->
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Server answered ${connection.responseCode}"
            }
            connection.inputStream.bufferedReader().readText()
        }
        val json = JSONObject(text)
        UpdateInfo(
            versionCode = json.getInt("versionCode"),
            versionName = json.getString("versionName"),
            url = json.getString("url"),
            notes = json.optString("notes"),
            sizeBytes = json.optLong("sizeBytes"),
        )
    }

    private suspend fun fetchApk(context: Context, info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val folder = File(context.cacheDir, "updates").apply { mkdirs() }
        // Only ever one APK waiting, so a half-finished download can't be installed later.
        folder.listFiles()?.forEach { it.delete() }
        val target = File(folder, "MixMaster_${info.versionName}.apk")
        val partial = File(folder, "${target.name}.part")

        open(info.url).use { connection ->
            require(connection.responseCode == HttpURLConnection.HTTP_OK) {
                "Server answered ${connection.responseCode}"
            }
            val total = if (info.sizeBytes > 0) info.sizeBytes else connection.contentLengthLong
            var read = 0L
            var shownPercent = -1
            connection.inputStream.use { input ->
                partial.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        read += count
                        if (total > 0) {
                            // Only on a whole-percent change — a 19 MB APK is ~300 chunks, and
                            // emitting each one just recomposes the screen for nothing.
                            val percent = ((read * 100) / total).toInt()
                            if (percent != shownPercent) {
                                shownPercent = percent
                                _state.value = UpdateState.Downloading(info, percent)
                            }
                        }
                    }
                }
            }
            // A truncated download is a broken APK that still looks like a file, so check the
            // size before renaming it into place.
            require(total <= 0 || read == total) { "Download stopped early" }
        }
        check(partial.renameTo(target)) { "Couldn't finish saving the download" }
        target
    }

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
        }

    private fun <T> HttpURLConnection.use(block: (HttpURLConnection) -> T): T = try {
        block(this)
    } finally {
        disconnect()
    }

    private fun readableReason(error: Throwable): String = when (error) {
        is java.net.UnknownHostException -> "No connection."
        is java.net.SocketTimeoutException -> "The server took too long."
        else -> error.message ?: "Something went wrong."
    }
}
