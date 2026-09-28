package com.conwic.mixmaster.ui.breaktime

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.conwic.mixmaster.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Send to a friend", under Break time: the Pour Day app, which rides inside MixMaster
 * (assets/share/pourday.apk, packed at build time), handed to whatever the person picks — Quick
 * Share, Bluetooth, a messenger. Only the game goes: it's an app of its own, with nothing of
 * MixMaster in it, and being carried here it needs no signal to send.
 */
object PourDayShare {

    /**
     * The APK copied out where a share target can read it: an app's assets are no file anyone else
     * can open. Named for the build, so the friend's downloads say which one it is. Null if the
     * copy failed — a full phone, say.
     */
    suspend fun prepare(context: Context): Uri? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.cacheDir, "share").apply { mkdirs() }
            // only the one being sent: an old copy is 3 MB of nothing
            dir.listFiles()?.forEach { it.delete() }
            val out = File(dir, "PourDay_${BuildConfig.VERSION_NAME}.apk")
            context.assets.open("share/pourday.apk").use { input -> out.outputStream().use { input.copyTo(it) } }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", out)
        }.getOrNull()
    }

    fun send(context: Context, uri: Uri, subject: String, text: String, chooser: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/vnd.android.package-archive")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .putExtra(Intent.EXTRA_TEXT, text)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, chooser)) }
    }
}
