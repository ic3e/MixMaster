package com.conwic.mixmaster.data.company

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The company server's own files, carried in the app (from `server/` in the source) and handed to
 * whoever sets one up — by email to themselves, to open on a computer. Once the app is handed
 * over, this is the copy the company has.
 */
object ServerFiles {

    private fun dir(context: Context): File = File(context.cacheDir, "server").apply { mkdirs() }

    private fun uri(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    /**
     * The Google server as a plain text file: it opens on any computer, and is copied from there
     * into Apps Script. A .gs attachment is something most computers don't know how to open.
     */
    fun googleScript(context: Context): Uri {
        val out = File(dir(context), "MixMaster-server-Google.txt")
        context.assets.open("google/Code.gs").use { input -> out.outputStream().use { input.copyTo(it) } }
        return uri(context, out)
    }

    /** The website server as one zip holding the folder to upload. */
    fun websiteZip(context: Context): Uri {
        val out = File(dir(context), "mixmaster-website-server.zip")
        ZipOutputStream(out.outputStream()).use { zip ->
            // The data folder's lock (.htaccess) is written by the server itself the first time it runs.
            listOf("api.php", "config.sample.php", "data/index.html").forEach { name ->
                zip.putNextEntry(ZipEntry("mixmaster/$name"))
                context.assets.open("website/mixmaster/$name").use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
        return uri(context, out)
    }

    fun share(context: Context, uri: Uri, mime: String, subject: String, chooser: String) {
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, subject)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        runCatching { context.startActivity(Intent.createChooser(send, chooser)) }
    }
}
