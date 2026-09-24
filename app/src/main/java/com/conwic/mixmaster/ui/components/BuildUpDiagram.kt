package com.conwic.mixmaster.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.domain.rulerStep
import com.conwic.mixmaster.ui.theme.Charcoal
import kotlin.math.roundToInt

/** One coat of the build-up as the picture shows it. */
data class BuildUpRow(
    val number: Int,
    val title: String,
    val detail: String,
    /** How thick it goes on. Null for a coat laid into the ones around it — see [BuildUpCoat]. */
    val millimetres: Double?,
    /** 0..1 against the heaviest coat, which decides how dark it is drawn. */
    val weight: Float,
    /** Whose system it belongs to; a change of brand draws a line across the stack. */
    val brand: String,
    /** The figure at the right of the band, already worded. */
    val mmText: String?,
)

/**
 * The two ends of the slab colouring: a light screed and a dark one.
 *
 * Fixed rather than themed. A build-up is a drawing of a physical thing — the same drawing goes
 * in the report, on paper — and it reads the same whichever way the phone is set.
 */
private val SlabLight = Color(0xFFF2EBE0)
private val SlabDark = Color(0xFF4C5154)
private val StripEdge = Color(0xFFDCD6C9)
private val Rule = Color(0xFF9C9488)
private val RuleFaint = Color(0xFFCFC8BA)

/** The one curve the app opens things with. */
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private val BandHeight = 44.dp
private val BandGap = 6.dp
private val StripWidth = 18.dp
private val RulerWidth = 26.dp

/**
 * The floor, as a strip drawn to scale and a set of bands you can read.
 *
 * Closed it is one row: the strip at thumbnail size and the total. That is the whole point —
 * the drawing costs a line until somebody asks for it. Open, the strip grows to full height
 * with a millimetre scale beside it, and the coats come out of it in laying order.
 *
 * The strip holds only the coats that have a thickness. One that has none — a mesh laid dry, a
 * sand broadcast into a wet primer, a product with no density on file — is a dashed line across
 * the strip where it sits, because that is what it is: something laid into the joint between
 * two coats, adding no height. Which is also what keeps the scale honest: every millimetre of
 * the strip is a millimetre of floor, so the numbers beside it land where they should.
 */
@Composable
fun BuildUpPanel(
    rows: List<BuildUpRow>,
    summary: String,
    totalText: String?,
    expanded: Boolean,
    onToggle: () -> Unit,
    openLabel: String,
    modifier: Modifier = Modifier,
) {
    if (rows.isEmpty()) return
    val calm = rememberMotionOff()
    val measurer = rememberTextMeasurer()
    val turn by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        animationSpec = tween(if (calm) 0 else 280, easing = EaseOut),
        label = "chevron",
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = openLabel, onClick = onToggle)
            .animateHeight(calm),
    ) {
        if (!expanded) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BuildUpStrip(
                    rows = rows,
                    measurer = measurer,
                    ruler = false,
                    grow = null,
                    modifier = Modifier.width(StripWidth).height(46.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = summary, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    totalText?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = turn },
                )
            }
        } else {
            // The strip grows from the bottom as the bands come out of it, so the small strip in
            // the closed row and the tall one here read as the same object.
            val grow = remember { Animatable(if (calm) 1f else 0.1f) }
            LaunchedEffect(Unit) { if (!calm) grow.animateTo(1f, tween(320, easing = EaseOut)) }

            Row(
                modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BuildUpStrip(
                    rows = rows,
                    measurer = measurer,
                    ruler = true,
                    grow = grow,
                    modifier = Modifier.width(RulerWidth + StripWidth).fillMaxHeight(),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(BandGap),
                ) {
                    val topDown = rows.asReversed()
                    topDown.forEachIndexed { fromTop, row ->
                        val appear = remember(row.number) { Animatable(if (calm) 1f else 0f) }
                        LaunchedEffect(row.number) {
                            if (!calm) appear.animateTo(1f, tween(260, delayMillis = 35 * fromTop, easing = EaseOut))
                        }
                        // The system's name where it changes: two brands in one floor is two
                        // systems, and the joint between them is worth seeing.
                        val below = topDown.getOrNull(fromTop + 1)
                        Band(row = row, appear = appear)
                        if (below != null && below.brand.isNotBlank() && !below.brand.equals(row.brand, true)) {
                            SystemLine(brand = below.brand, appear = appear)
                        }
                    }
                }
            }
        }
    }
}

/** A coat: its number, what it is, what goes on and how thick that comes out. */
@Composable
private fun Band(row: BuildUpRow, appear: Animatable<Float, *>) {
    val face = lerp(SlabLight, SlabDark, row.weight.coerceIn(0f, 1f))
    val ink = if (face.luminance() < 0.42f) Color.White else Charcoal
    val dim = ink.copy(alpha = 0.78f)
    val laidIn = row.millimetres == null
    val shape = RoundedCornerShape(11.dp)
    val dash = RuleFaint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(BandHeight)
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * -14.dp.toPx()
            }
            .then(
                if (laidIn) {
                    // Dashed and pale: this one is laid into the coats around it, so it is not
                    // drawn as a solid slab either.
                    Modifier.clip(shape).background(SlabLight.copy(alpha = 0.45f)).drawBehind {
                        drawRoundRect(
                            color = dash,
                            cornerRadius = CornerRadius(11.dp.toPx()),
                            style = Stroke(
                                width = 1.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                            ),
                        )
                    }
                } else {
                    Modifier.clip(shape).background(face)
                },
            )
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (laidIn) Charcoal.copy(alpha = 0.08f) else ink.copy(alpha = 0.20f)),
        ) {
            Text(
                text = "${row.number}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.ExtraBold,
                color = if (laidIn) Charcoal else ink,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = if (laidIn) Charcoal else ink,
                maxLines = 1,
            )
            Text(
                text = row.detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (laidIn) MaterialTheme.colorScheme.onSurfaceVariant else dim,
                maxLines = 1,
            )
        }
        Text(
            text = row.mmText ?: "—",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.ExtraBold,
            color = if (row.mmText == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else if (laidIn) {
                Charcoal
            } else {
                ink
            },
        )
    }
}

/** Where one system stops and the next starts. */
@Composable
private fun SystemLine(brand: String, appear: Animatable<Float, *>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .graphicsLayer { alpha = appear.value },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Text(
            text = brand.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

/**
 * The strip: every coat that has a thickness, to scale, with the millimetres written beside it.
 *
 * Drawn from the bottom, because that is the order a floor goes down in.
 */
@Composable
private fun BuildUpStrip(
    rows: List<BuildUpRow>,
    measurer: TextMeasurer,
    ruler: Boolean,
    grow: Animatable<Float, *>?,
    modifier: Modifier = Modifier,
) {
    val films = rows.mapNotNull { it.millimetres }
    val total = films.sum()
    val labelStyle = TextStyle(fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Rule)
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (total <= 0.0) return@Canvas
        val stripLeft = if (ruler) RulerWidth.toPx() else 0f
        val stripWidth = StripWidth.toPx()
        val height = size.height
        val perMm = height / total.toFloat()

        if (ruler) {
            // A real scale, not a decoration: 0 at the concrete, a mark at every step the
            // figure below is written in, half-marks between them.
            val step = rulerStep(total)
            var mark = 0.0
            while (mark <= total + 0.0005) {
                val y = height - (mark * perMm).toFloat()
                drawLine(
                    color = Rule,
                    start = Offset(stripLeft - 7.dp.toPx(), y),
                    end = Offset(stripLeft - 1.dp.toPx(), y),
                    strokeWidth = 1f,
                )
                val text = measurer.measure(formatMark(mark), labelStyle)
                drawText(
                    textLayoutResult = text,
                    topLeft = Offset(
                        x = stripLeft - 9.dp.toPx() - text.size.width,
                        y = (y - text.size.height / 2f).coerceIn(0f, height - text.size.height),
                    ),
                )
                val half = mark + step / 2.0
                if (half <= total + 0.0005) {
                    val halfY = height - (half * perMm).toFloat()
                    drawLine(
                        color = RuleFaint,
                        start = Offset(stripLeft - 4.dp.toPx(), halfY),
                        end = Offset(stripLeft - 1.dp.toPx(), halfY),
                        strokeWidth = 1f,
                    )
                }
                mark += step
            }
        }

        // Grows out of the closed row's thumbnail, from the bottom, like a floor does.
        scale(scaleX = 1f, scaleY = grow?.value ?: 1f, pivot = Offset(stripLeft + stripWidth / 2f, height)) {
            val radius = CornerRadius(4.dp.toPx())
            val outline = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(
                            offset = Offset(stripLeft, 0f),
                            size = Size(stripWidth, height),
                        ),
                        cornerRadius = radius,
                    ),
                )
            }
            clipPath(outline) {
                var bottom = height
                rows.forEach { row ->
                    val mm = row.millimetres ?: return@forEach
                    val band = (mm * perMm).toFloat()
                    drawRect(
                        color = lerp(SlabLight, SlabDark, row.weight.coerceIn(0f, 1f)),
                        topLeft = Offset(stripLeft, bottom - band),
                        size = Size(stripWidth, band),
                    )
                    bottom -= band
                }
            }
            drawPath(outline, StripEdge, style = Stroke(width = 1.dp.toPx()))

            // The ones with no thickness of their own: a dashed line across the joint they are
            // laid into, running a little past the strip on both sides so it reads as a line
            // rather than a very thin coat.
            var laid = height
            rows.forEach { row ->
                val mm = row.millimetres
                if (mm == null) {
                    drawLine(
                        color = Rule,
                        start = Offset(stripLeft - 3.dp.toPx(), laid),
                        end = Offset(stripLeft + stripWidth + 3.dp.toPx(), laid),
                        strokeWidth = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 4f)),
                    )
                } else {
                    laid -= (mm * perMm).toFloat()
                }
            }

            // And a tick where one system hands over to the next.
            var edge = height
            rows.forEachIndexed { index, row ->
                val above = rows.getOrNull(index + 1)
                row.millimetres?.let { edge -= (it * perMm).toFloat() }
                if (above != null && above.brand.isNotBlank() && !above.brand.equals(row.brand, true)) {
                    drawLine(
                        color = primary,
                        start = Offset(stripLeft - 5.dp.toPx(), edge),
                        end = Offset(stripLeft, edge),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }
        }
    }
}

/** 0, 0.5, 1 — the way somebody would write the mark down, without trailing noise. */
private fun formatMark(mm: Double): String {
    val rounded = (mm * 100).roundToInt() / 100.0
    return if (rounded == rounded.toLong().toDouble()) {
        rounded.toLong().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}

/** Opening and closing changes the card's height; that is the one size worth animating. */
private fun Modifier.animateHeight(calm: Boolean): Modifier =
    if (calm) this else this.animateContentSize(animationSpec = tween(320, easing = EaseOut))
