package com.conwic.mixmaster.data.docs

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The safety and technical sheets kept inside the app.
 *
 * A sheet can be a link the manufacturer publishes, and usually is. But a shed has no signal,
 * a supplier moves its PDFs around, and a client standing on the floor asking for documentation
 * is not going to wait for either — so a sheet can also be the file itself, copied in and handed
 * out from here.
 *
 * A picked document's URI is a grant that dies with the process, exactly as a photo's does,
 * which is why the bytes are copied rather than the address remembered.
 */
object SheetStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "sheets").apply { mkdirs() }

    /** True where this is a file this object holds rather than a link to the web. */
    fun isStored(value: String): Boolean = value.startsWith("file://")

    /** What to show for it: the file's own name, or the link as typed. */
    fun label(value: String): String =
        if (isStored(value)) Uri.parse(value).lastPathSegment.orEmpty() else value

    /** Copies a picked PDF in. Returns the stored URI, or null if it could not be read. */
    suspend fun keep(context: Context, source: Uri, name: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val safe = name.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "sheet.pdf" }
            val file = File(dir(context), "${System.currentTimeMillis()}-$safe")
            context.contentResolver.openInputStream(source)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            if (file.length() == 0L) {
                file.delete()
                null
            } else {
                Uri.fromFile(file).toString()
            }
        }.getOrNull()
    }

    /** Drops the copy behind a stored sheet. Leaves a link, or anything else, alone. */
    suspend fun forget(context: Context, stored: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            if (!isStored(stored)) return@runCatching false
            val file = File(Uri.parse(stored).path ?: return@runCatching false)
            if (file.parentFile?.canonicalPath != dir(context).canonicalPath) return@runCatching false
            file.delete()
        }.getOrDefault(false)
    }

    /** Opens it with whatever handles it: a browser for a link, a reader for a file. */
    fun open(context: Context, value: String) {
        val uri = shareable(context, value) ?: return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            if (isStored(value)) setDataAndType(uri, "application/pdf") else data = uri
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /**
     * Something another app can open.
     *
     * A `file://` URI is refused by Android outright, so a stored sheet is handed over through
     * the app's own provider; a link goes out as it came in.
     */
    fun shareable(context: Context, value: String): Uri? = runCatching {
        if (!isStored(value)) {
            Uri.parse(value)
        } else {
            val file = File(Uri.parse(value).path ?: return@runCatching null)
            if (!file.exists()) {
                null
            } else {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }
        }
    }.getOrNull()
}
