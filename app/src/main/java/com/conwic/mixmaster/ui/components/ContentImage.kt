package com.conwic.mixmaster.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext

/**
 * Renders a photo picked from the gallery/camera by decoding its content:// URI directly via
 * BitmapFactory — avoids pulling in an image-loading library (Coil) just for this. Fine for the
 * modest number of photos a single project accumulates; not meant for large lists/paging.
 */
@Composable
fun ContentImage(uri: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }

    LaunchedEffect(uri) {
        bitmap = runCatching {
            context.contentResolver.openInputStream(android.net.Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
        }.getOrNull()
    }

    val loaded = bitmap
    if (loaded != null) {
        Image(bitmap = loaded.asImageBitmap(), contentDescription = null, modifier = modifier)
    } else {
        androidx.compose.foundation.layout.Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant))
    }
}
