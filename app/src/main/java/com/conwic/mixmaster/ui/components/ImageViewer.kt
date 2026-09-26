package com.conwic.mixmaster.ui.components

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.conwic.mixmaster.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

/** One picture the viewer can show, and the line under it. */
data class ViewerImage(val uri: String, val caption: String)

/**
 * Pictures full screen, to be looked into: two fingers to zoom, a double tap to zoom in on a spot
 * and out again, a swipe to the next one.
 *
 * Site photos opened in a sheet at the width of the phone, which is no use for reading a crack or
 * the figures on a drawing — a blueprint had to go out to the gallery to be zoomed at all.
 *
 * [onAction], when given, is offered under the picture for the one on screen — "Remove" for a
 * photo — and is handed its index.
 */
@Composable
fun ImageViewer(
    images: List<ViewerImage>,
    startAt: Int,
    onDismiss: () -> Unit,
    actionLabel: String? = null,
    onAction: ((Int) -> Unit)? = null,
) {
    var index by remember { mutableIntStateOf(startAt) }
    // One gone from under it: stay in range, or close once there is nothing left to show.
    LaunchedEffect(images.size) {
        if (images.isEmpty()) onDismiss() else index = index.coerceIn(0, images.lastIndex)
    }
    val current = images.getOrNull(index.coerceIn(0, (images.size - 1).coerceAtLeast(0))) ?: return
    val light = Color.White

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // A new picture starts unzoomed: the zoom belongs to the one it was set on.
            key(current.uri) {
                ZoomablePicture(
                    uri = current.uri,
                    onSwipe = { step -> index = (index + step).coerceIn(0, images.lastIndex) },
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = light)
                }
                if (images.size > 1) {
                    Text(
                        text = stringResource(R.string.viewer_count, index + 1, images.size),
                        style = MaterialTheme.typography.titleMedium,
                        color = light,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }

            // Arrows as well as the swipe: a zoomed picture takes the swipe to move about in.
            if (images.size > 1) {
                if (index > 0) {
                    StepButton(
                        forward = false,
                        onClick = { index -= 1 },
                        modifier = Modifier.align(Alignment.CenterStart),
                    )
                }
                if (index < images.lastIndex) {
                    StepButton(
                        forward = true,
                        onClick = { index += 1 },
                        modifier = Modifier.align(Alignment.CenterEnd),
                    )
                }
            }

            if (current.caption.isNotBlank() || (actionLabel != null && onAction != null)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = current.caption,
                        style = MaterialTheme.typography.bodyMedium,
                        color = light.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                    )
                    if (actionLabel != null && onAction != null) {
                        TextButton(onClick = { onAction(index) }) {
                            Text(text = actionLabel, color = ViewerAlert)
                        }
                    }
                }
            }
        }
    }
}

/** The app's warning red, lifted to read on black. */
private val ViewerAlert = Color(0xFFE0584F)

@Composable
private fun StepButton(forward: Boolean, onClick: () -> Unit, modifier: Modifier) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .padding(horizontal = 6.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.45f)),
    ) {
        Icon(
            imageVector = if (forward) Icons.Filled.ChevronRight else Icons.Filled.ChevronLeft,
            contentDescription = stringResource(if (forward) R.string.viewer_next else R.string.viewer_previous),
            tint = Color.White,
        )
    }
}

/**
 * One picture that zooms under the fingers.
 *
 * Unzoomed, a one-finger drag is left alone until it ends and then read as a swipe to the next
 * picture; zoomed, it moves the picture about instead. The picture is never let off its edges.
 */
@Composable
private fun ZoomablePicture(uri: String, onSwipe: (Int) -> Unit) {
    val context = LocalContext.current
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(uri) {
        val decoded = decodeForViewer(context, uri, MaxSidePx)
        bitmap = decoded
        failed = decoded == null
    }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val swipeDistance = with(LocalDensity.current) { 72.dp.toPx() }

    fun keptOn(wanted: Offset, atScale: Float): Offset {
        val maxX = size.width * (atScale - 1f) / 2f
        val maxY = size.height * (atScale - 1f) / 2f
        return Offset(wanted.x.coerceIn(-maxX, maxX), wanted.y.coerceIn(-maxY, maxY))
    }

    Box(
        modifier = Modifier.fillMaxSize().onSizeChanged { size = it },
        contentAlignment = Alignment.Center,
    ) {
        val loaded = bitmap
        when {
            loaded != null -> Image(
                bitmap = loaded,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onDoubleTap = { tap ->
                                if (scale > 1f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                } else {
                                    // In on the spot that was tapped, not on the middle.
                                    val middle = Offset(size.width / 2f, size.height / 2f)
                                    scale = DoubleTapScale
                                    offset = keptOn((middle - tap) * (DoubleTapScale - 1f), DoubleTapScale)
                                }
                            },
                        )
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var dragX = 0f
                            var twoFingers = false
                            do {
                                val event = awaitPointerEvent()
                                val fingers = event.changes.count { it.pressed }
                                if (fingers > 1) twoFingers = true
                                if (fingers > 1 || scale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    val middle = Offset(size.width / 2f, size.height / 2f)
                                    val centroid = event.calculateCentroid(useCurrent = true)
                                    val around = if (centroid == Offset.Unspecified) Offset.Zero else centroid - middle
                                    val newScale = (scale * zoom).coerceIn(1f, MaxScale)
                                    // Keeps the point between the fingers under the fingers.
                                    val moved = (offset - around) * (newScale / scale) + around + pan
                                    scale = newScale
                                    offset = if (newScale <= 1f) Offset.Zero else keptOn(moved, newScale)
                                    event.changes.forEach { if (it.positionChanged()) it.consume() }
                                } else {
                                    dragX += event.calculatePan().x
                                }
                            } while (event.changes.any { it.pressed })
                            if (!twoFingers && scale <= 1f && abs(dragX) > swipeDistance) {
                                onSwipe(if (dragX < 0f) 1 else -1)
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            )
            failed -> Text(
                text = stringResource(R.string.image_cant_open),
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(24.dp),
            )
            else -> CircularProgressIndicator(color = Color.White)
        }
    }
}

private const val MaxScale = 6f
private const val DoubleTapScale = 2.5f

/**
 * Big enough to zoom a drawing into, small enough not to run a phone out of memory: a photo off a
 * modern camera is 4000 px and more on its long side, and decoded whole that is 50 MB a picture.
 */
private const val MaxSidePx = 2560

/**
 * The picture at up to [maxSide] on its long side, the right way up.
 *
 * ImageDecoder turns a photo by the camera's own note of which way up it was held; BitmapFactory,
 * all there is before Android 9, does not, and a portrait photo comes out on its side there.
 */
private suspend fun decodeForViewer(context: Context, uri: String, maxSide: Int): ImageBitmap? =
    withContext(Dispatchers.IO) {
        runCatching {
            val parsed = Uri.parse(uri)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, parsed)
                ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val width = info.size.width
                    val height = info.size.height
                    val longest = maxOf(width, height)
                    if (longest > maxSide) {
                        val ratio = maxSide.toFloat() / longest
                        decoder.setTargetSize(
                            (width * ratio).toInt().coerceAtLeast(1),
                            (height * ratio).toInt().coerceAtLeast(1),
                        )
                    }
                }.asImageBitmap()
            } else {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                context.contentResolver.openInputStream(parsed)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                var sample = 1
                while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSide) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                context.contentResolver.openInputStream(parsed)
                    ?.use { BitmapFactory.decodeStream(it, null, options) }
                    ?.asImageBitmap()
            }
        }.getOrNull()
    }
