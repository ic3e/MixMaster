package com.conwic.mixmaster.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/** One coat of the build-up as the picture shows it: what it says, and how thick it is drawn. */
data class BuildUpRow(val title: String, val detail: String, val weight: Float)

/**
 * The two ends of the slab colouring: a light screed and a dark one.
 *
 * Fixed rather than themed. A floor build-up is a drawing of a physical thing — the same
 * drawing goes in the report, on paper — and it should read the same whichever way the phone
 * is set.
 */
private val SlabLight = Color(0xFFF2EBE0)
private val SlabDark = Color(0xFF4C5154)

/**
 * The floor drawn the way a datasheet draws it: the coats pulled apart, bottom one first, each
 * with a line out to its name.
 *
 * Thickness is not to scale and cannot be — a primer is one part in thirty of a screed, and to
 * scale it is a hairline nobody can see. It is flattened ([slabWeights]) so the order and the
 * obvious differences survive while every coat stays visible. The figures next to it are the
 * real ones.
 */
@Composable
fun BuildUpDiagram(rows: List<BuildUpRow>, modifier: Modifier = Modifier) {
    if (rows.isEmpty()) return
    val measurer = rememberTextMeasurer()
    val titleStyle = MaterialTheme.typography.labelMedium.copy(
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    val detailStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    val leader = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.28f)
    val edge = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val density = LocalDensity.current
        // The stack takes about a third of the width and the names take the rest: a product
        // name and its rate both want a line they can finish on, and on a phone the names are
        // what run out of room first.
        val stackWidth = maxWidth * 0.36f
        val labelLeft = stackWidth + 18.dp
        val labelWidth = (maxWidth - labelLeft).coerceAtLeast(56.dp)
        val labelPx = with(density) { labelWidth.roundToPx() }

        val titles = rows.map {
            measurer.measure(
                text = it.title,
                style = titleStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 2,
                constraints = Constraints(maxWidth = labelPx),
            )
        }
        val details = rows.map {
            measurer.measure(
                text = it.detail,
                style = detailStyle,
                overflow = TextOverflow.Ellipsis,
                // Two lines, because the rate, the thickness and the colour it is tinted with
                // do not fit on one at this width — and the colour is the half that was being
                // cut off.
                maxLines = 2,
                constraints = Constraints(maxWidth = labelPx),
            )
        }
        val textHeights = rows.indices.map { titles[it].size.height + details[it].size.height }
        val tallest = with(density) { (textHeights.maxOrNull() ?: 0).toDp() }

        val thickest = 12.dp
        val thinnest = 3.dp
        val depth = stackWidth * 0.34f
        // The slabs sit close enough to overlap, the way a stack seen at an angle does; the
        // names need more room than that, so they are spread further apart and the lines fan
        // out to reach them. That fan is what a datasheet's callouts are.
        val slabStep = 22.dp
        val labelStep = maxOf(tallest + 8.dp, 32.dp)
        val stackHeight = depth + slabStep * (rows.size - 1) + thickest
        val labelsHeight = labelStep * rows.size
        val height = maxOf(stackHeight, labelsHeight) + 8.dp

        Canvas(modifier = Modifier.fillMaxWidth().height(height)) {
            val w = stackWidth.toPx()
            val d = depth.toPx()
            val thin = thinnest.toPx()
            val thick = thickest.toPx()
            val labelX = labelLeft.toPx()
            val slabGap = slabStep.toPx()
            val labelGap = labelStep.toPx()
            // The stack floats against the middle of the names, so neither end runs out first.
            val stackTop = (size.height - stackHeight.toPx()) / 2f + d / 2f
            val labelTop = (size.height - labelsHeight.toPx()) / 2f

            // Drawn from the top of the stack downwards: on this page a coat lower down is the
            // one nearer the eye, so it is painted last and wins wherever they overlap.
            for (fromTop in rows.indices) {
                val index = rows.size - 1 - fromTop
                val row = rows[index]
                val y = stackTop + fromTop * slabGap
                val thickness = thin + row.weight * (thick - thin)
                val face = lerp(SlabLight, SlabDark, row.weight)

                val left = Offset(0f, y)
                val front = Offset(w / 2f, y + d / 2f)
                val right = Offset(w, y)
                val back = Offset(w / 2f, y - d / 2f)
                val drop = Offset(0f, thickness)

                // The two sides you can see from here, then the top laid over them. The light
                // is on the left, so the right side is the darker of the two.
                drawPath(slab(left, front, front + drop, left + drop), face.shade(0.84f))
                drawPath(slab(front, right, right + drop, front + drop), face.shade(0.68f))
                drawPath(slab(left, back, right, front), face)
                drawPath(slab(left, back, right, front), edge, style = Stroke(width = 1f))

                // Out to the name: a line off the corner, bent once, the way a datasheet calls
                // out a layer.
                val labelY = labelTop + labelGap * (fromTop + 0.5f)
                val bend = Offset(w + (labelX - w) * 0.45f, labelY)
                drawLine(leader, right, bend, strokeWidth = 1f)
                drawLine(leader, bend, Offset(labelX - 5.dp.toPx(), labelY), strokeWidth = 1f)

                val textTop = labelY - textHeights[index] / 2f
                drawText(titles[index], topLeft = Offset(labelX, textTop))
                drawText(details[index], topLeft = Offset(labelX, textTop + titles[index].size.height))
            }
        }
    }
}

/** One flat face of a slab. */
private fun slab(vararg corners: Offset): Path = Path().apply {
    moveTo(corners[0].x, corners[0].y)
    for (index in 1 until corners.size) lineTo(corners[index].x, corners[index].y)
    close()
}

/** The same colour with the light taken off it, for a face turned away. */
private fun Color.shade(factor: Float): Color = Color(red * factor, green * factor, blue * factor, alpha)
