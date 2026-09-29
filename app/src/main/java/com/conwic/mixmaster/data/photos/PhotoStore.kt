package com.conwic.mixmaster.data.photos

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
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
 *
 * The copy is made smaller on the way in. A phone's camera takes 12 megapixels and more, 4–8 MB a
 * photo; in a company every one of them goes through the server to every phone, over mobile data
 * on site. 2048 pixels on the long side is still enough to zoom in on a crack.
 */
object PhotoStore {

    private const val LONG_SIDE = 2048
    private const val QUALITY = 85

    private fun dir(context: Context): File =
        File(context.filesDir, "photos").apply { mkdirs() }

    /** Copies [source] into the app's own storage. Returns the stored URI, or null if it failed. */
    suspend fun keep(context: Context, source: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(dir(context), "photo-${System.currentTimeMillis()}.jpg")
            // A photo that can't be decoded here is kept as it came, rather than not at all.
            if (!shrink(context, source, file)) {
                context.contentResolver.openInputStream(source)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                } ?: return@runCatching null
            }
            if (file.length() == 0L) {
                file.delete()
                null
            } else {
                Uri.fromFile(file).toString()
            }
        }.getOrNull()
    }

    /**
     * Writes [source] to [target] as a JPEG no longer than [LONG_SIDE] on its long side, turned
     * the way the camera was held — the turn is only a note in the original, which a re-encoded
     * copy would lose. False when it couldn't be read as a picture.
     */
    private fun shrink(context: Context, source: Uri, target: File): Boolean = runCatching {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(source)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return@runCatching false
        var sample = 1
        while (longest / (sample * 2) >= LONG_SIDE) sample *= 2
        val decoded = resolver.openInputStream(source)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return@runCatching false
        val turn = resolver.openInputStream(source)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        } ?: 0f
        val scale = minOf(1f, LONG_SIDE.toFloat() / maxOf(decoded.width, decoded.height))
        val matrix = Matrix().apply {
            postScale(scale, scale)
            postRotate(turn)
        }
        val finished = if (scale < 1f || turn != 0f) {
            Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        } else {
            decoded
        }
        target.outputStream().use { out -> finished.compress(Bitmap.CompressFormat.JPEG, QUALITY, out) }
        if (finished !== decoded) finished.recycle()
        decoded.recycle()
        true
    }.getOrDefault(false)

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
