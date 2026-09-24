package com.conwic.mixmaster.data.photos

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Keeps a copy of every picked photo inside the app.
 *
 * The photo picker hands back a content:// URI the app may read *for now* — the grant dies with
 * the process, and unlike a document picker's it can't be made persistent. Storing that URI meant
 * a site photo looked fine until the app was closed and then never loaded again. So the bytes are
 * copied somewhere the app owns, and that file is what gets remembered.
 */
object PhotoStore {

    private fun dir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    /** Copies [source] into the app's own storage. Returns the stored URI, or null if it failed. */
    suspend fun keep(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir(context), "photo-${System.currentTimeMillis()}.jpg")
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

    /**
     * Drops the copy behind a stored URI.
     *
     * A photo taken off a job used to leave its file behind for the life of the install — the
     * row went, the megabytes stayed. Only touches files this object wrote: anything that isn't
     * a file:// URI under the app's own photo folder is left alone.
     */
    suspend fun forget(context: Context, stored: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val uri = Uri.parse(stored)
            if (uri.scheme != "file") return@runCatching false
            val file = File(uri.path ?: return@runCatching false)
            if (file.parentFile?.canonicalPath != dir(context).canonicalPath) return@runCatching false
            file.delete()
        }.getOrDefault(false)
    }
}
