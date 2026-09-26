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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.conwic.mixmaster.domain.rulerStep
import com.conwic.mixmaster.ui.theme.Charcoal
import kotlin.math.roundToInt
import com.conwic.mixmaster.ui.theme.AppFontFamily

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
 * in the report, on paper — and it reads the same whichever way the phone is set. Which is also
 * why the ink on the pale bands is spelled out here instead of taken from the colour scheme: a
 * band that is always near-white needs a dark word on it in both settings of the phone.
 */
private val SlabLight = Color(0xFFF2EBE0)
private val SlabDark = Color(0xFF4C5154)
private val StripEdge = Color(0xFFDCD6C9)
private val Rule = Color(0xFF9C9488)
private val RuleFaint = Color(0xFFCFC8BA)
private val PaleInk = Color(0xFF6C6459)
private val HatchGround = Color(0xFFFBFAF8)
private val HatchLine = Color(0xFFEDEAE2)

/** The one curve the app opens things with. */
private val EaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

private val BandHeight = 44.dp
/** A shade shorter, because it is not a layer of the floor in its own right. */
private val BandHeightLaidIn = 40.dp
private val BandGap = 6.dp
private val BandRadius = 10.dp
private val SystemRowHeight = 22.dp
private val StripWidth = 18.dp
private val RulerWidth = 26.dp
/** Headroom above the strip, for the cap that says what the scale is counting in. */
private val RulerCap = 14.dp

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
 *
 * The line along the bottom stays in both states. Open used to take the total away with it,
 * which is the one figure somebody reads the drawing for.
 */
@Composable
fun BuildUpPanel(
    rows: List<BuildUpRow>,
    summary: String,
    totalText: String?,
    /** The small word along the bottom left — what to do with the panel, or what it is showing. */
    footNote: String,
    /** The total on the bottom right, written short: "2.03 mm". */
    totalShort: String?,
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
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
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
                        Band(row = row, appear = appear)
                        // The system's name under the bands that belong to it, the way a bracket
                        // on a drawing names the group it spans. Two brands in one floor is two
                        // systems, and the joint between them is worth seeing.
                        val below = topDown.getOrNull(fromTop + 1)
                        val lowestOfItsSystem = below == null || !below.brand.equals(row.brand, true)
                        if (lowestOfItsSystem && row.brand.isNotBlank()) {
                            SystemLine(brand = row.brand, appear = appear)
                        }
                    }
                }
            }
        }

        BuildUpFoot(note = footNote, total = totalShort)
    }
}

/**
 * The line along the bottom: what the panel is showing on the left, what it comes to on the right.
 *
 * The same in both states, so the total does not disappear at the moment somebody opens the
 * drawing to look for it.
 */
@Composable
private fun BuildUpFoot(note: String, total: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 9.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
            )
            total?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.primary,
                )
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
    val shape = RoundedCornerShape(BandRadius)
    val dash = RuleFaint

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (laidIn) BandHeightLaidIn else BandHeight)
            .graphicsLayer {
                alpha = appear.value
                translationX = (1f - appear.value) * -14.dp.toPx()
            }
            .then(
                if (laidIn) {
                    // Hatched and dashed, the way a section drawing marks something that is not
                    // a layer in its own right: this one is worked into the coats around it.
                    Modifier.clip(shape).background(HatchGround).drawBehind {
                        val step = 5.dp.toPx()
                        var x = -size.height
                        while (x < size.width + size.height) {
                            drawLine(
                                color = HatchLine,
                                start = Offset(x, size.height),
                                end = Offset(x + size.height, 0f),
                                strokeWidth = 1.dp.toPx(),
                            )
                            x += step
                        }
                        drawRoundRect(
                            color = dash,
                            cornerRadius = CornerRadius(BandRadius.toPx()),
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
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(19.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        laidIn -> Charcoal.copy(alpha = 0.08f)
                        ink == Color.White -> Color.White.copy(alpha = 0.28f)
                        else -> Charcoal.copy(alpha = 0.12f)
                    },
                ),
        ) {
            Text(
                text = "${row.number}",
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (laidIn) Charcoal else ink,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            // One line each, cut with an ellipsis: a long product name must not push the
            // millimetres off the end of the band.
            Text(
                text = row.title,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (laidIn) Charcoal else ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.detail,
                style = MaterialTheme.typography.labelSmall,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = if (laidIn) PaleInk else dim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = row.mmText ?: "—",
            // The figures in the display face, like every other number in the app that is meant
            // to be read off rather than read through.
            style = MaterialTheme.typography.titleMedium,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = when {
                row.mmText == null -> Rule
                laidIn -> Charcoal
                else -> ink
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
            .height(SystemRowHeight)
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
            fontSize = 9.sp,
            fontWeight = FontWeight.ExtraBold,
            letterSpacing = 1.sp,
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
    // Drawn straight onto the canvas, where nothing hands down the theme's type: without the
    // family named here the ruler's figures came out in Roboto.
    val labelStyle = TextStyle(fontFamily = AppFontFamily, fontSize = 8.sp, fontWeight = FontWeight.Bold, color = Rule)
    val capStyle = TextStyle(
        fontFamily = AppFontFamily,
        fontSize = 8.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.5.sp,
        color = Rule,
    )
    val primary = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier) {
        if (total <= 0.0) return@Canvas
        val stripLeft = if (ruler) RulerWidth.toPx() else 0f
        val stripWidth = StripWidth.toPx()
        // Open, the top of the strip is held clear of the bands so the scale can be capped with
        // what it counts in. Closed there is nothing to cap, so the thumbnail fills its row.
        val cap = if (ruler) RulerCap.toPx() else 0f
        val height = size.height
        val top = cap
        val stripHeight = height - cap
        val perMm = stripHeight / total.toFloat()

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
                        y = (y - text.size.height / 2f).coerceIn(top, height - text.size.height),
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
            // What the figures on the scale are: millimetres, said once at the head of it.
            val capText = measurer.measure("MM", capStyle)
            drawText(
                textLayoutResult = capText,
                topLeft = Offset(
                    x = stripLeft - 9.dp.toPx() - capText.size.width,
                    y = (top - capText.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )
        }

        // Grows out of the closed row's thumbnail, from the bottom, like a floor does.
        scale(scaleX = 1f, scaleY = grow?.value ?: 1f, pivot = Offset(stripLeft + stripWidth / 2f, height)) {
            val radius = CornerRadius(4.dp.toPx())
            val outline = Path().apply {
                addRoundRect(
                    androidx.compose.ui.geometry.RoundRect(
                        rect = androidx.compose.ui.geometry.Rect(
                            offset = Offset(stripLeft, top),
                            size = Size(stripWidth, stripHeight),
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
