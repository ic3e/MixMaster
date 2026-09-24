package com.conwic.mixmaster.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import com.conwic.mixmaster.domain.rulerStep

/**
 * As much of a line as fits, and an ellipsis where it does not.
 *
 * Nothing on this page wraps: a name that is too long runs into the figure beside it or off the
 * edge of the paper, and both look like a broken report rather than a long name.
 */
internal fun fitText(text: String, paint: Paint, maxWidth: Float): String {
    if (text.isEmpty() || maxWidth <= 0f) return ""
    if (paint.measureText(text) <= maxWidth) return text
    val room = maxWidth - paint.measureText("…")
    if (room <= 0f) return "…"
    val kept = paint.breakText(text, true, room, null)
    return text.take(kept.coerceAtLeast(0)).trimEnd() + "…"
}

/** One coat of the build-up as the report draws it. */
internal data class ReportCoat(
    val number: Int,
    val title: String,
    val detail: String,
    /** Null for a coat laid into the ones around it: a line on the strip, no figure. */
    val millimetres: Double?,
    val weight: Float,
    val brand: String,
    val mmText: String?,
)

/**
 * The floor build-up on the page: a strip drawn to scale against a millimetre rule, and the
 * coats beside it in laying order.
 *
 * The same picture as the one on the project screen, worked out from the same figures — a
 * report a client reads should show the floor they are buying, not only a list of what went
 * into it.
 */
internal object BuildUpArt {

    // Tightened once the report was read on paper: the drawing was taking a third of a page
    // per room and pushing the figures a client actually reads onto the next one.
    private const val RowHeight = 20f
    private const val RowGap = 5f
    private const val SystemGap = 12f
    private const val StripWidth = 18f
    private const val RulerWidth = 26f
    private const val ColumnGap = 10f
    private const val Pad = 5f

    /** How tall the drawing will be, asked before there is a page to put it on. */
    fun height(rows: List<ReportCoat>, width: Float): Float {
        if (rows.isEmpty()) return 0f
        return rows.size * RowHeight + (rows.size - 1) * RowGap + systemBreaks(rows) * SystemGap + 2 * Pad
    }

    /** Draws it and returns the y it finished at. */
    fun draw(canvas: Canvas, rows: List<ReportCoat>, left: Float, top: Float, width: Float): Float {
        if (rows.isEmpty()) return top
        val total = rows.mapNotNull { it.millimetres }.sum()
        val stackTop = top + Pad
        val stackHeight = height(rows, width) - 2 * Pad
        val stripLeft = left + RulerWidth
        val rowsLeft = stripLeft + StripWidth + ColumnGap
        val rowsWidth = width - (rowsLeft - left)

        val namePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262322"); textSize = 9f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B6259"); textSize = 7.5f; typeface = Typeface.SANS_SERIF
        }
        val figurePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262322"); textSize = 9.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
        }
        val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B6259"); textSize = 7f
            typeface = Typeface.SANS_SERIF; textAlign = Paint.Align.RIGHT
        }
        val rulePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9C9488"); strokeWidth = 0.6f
        }
        val faintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#CFC8BA"); strokeWidth = 0.6f
        }
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#DCD6C9"); style = Paint.Style.STROKE; strokeWidth = 0.8f
        }
        val dashPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9C9488"); strokeWidth = 1.1f
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(3f, 2.5f), 0f)
        }
        val systemPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A5A2E"); strokeWidth = 1.1f
        }
        val systemText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8A5A2E"); textSize = 7f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD); textAlign = Paint.Align.RIGHT
        }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        val bottom = stackTop + stackHeight
        if (total > 0.0) {
            val perMm = (stackHeight / total).toFloat()

            // The scale: 0 at the concrete, a labelled mark at every step, half-marks between.
            val step = rulerStep(total)
            var mark = 0.0
            while (mark <= total + 0.0005) {
                val y = bottom - (mark * perMm).toFloat()
                canvas.drawLine(stripLeft - 7f, y, stripLeft - 1.5f, y, rulePaint)
                canvas.drawText(formatMark(mark), stripLeft - 9f, y + 2.2f, markPaint)
                val half = mark + step / 2.0
                if (half <= total + 0.0005) {
                    val halfY = bottom - (half * perMm).toFloat()
                    canvas.drawLine(stripLeft - 4f, halfY, stripLeft - 1.5f, halfY, faintPaint)
                }
                mark += step
            }

            // The strip itself: only the coats that have a thickness, to scale.
            canvas.save()
            val outline = RectF(stripLeft, stackTop, stripLeft + StripWidth, bottom)
            canvas.clipRect(outline)
            var fill = bottom
            rows.forEach { row ->
                val mm = row.millimetres ?: return@forEach
                val band = (mm * perMm).toFloat()
                facePaint.color = slabColour(row.weight)
                canvas.drawRect(stripLeft, fill - band, stripLeft + StripWidth, fill, facePaint)
                fill -= band
            }
            canvas.restore()
            canvas.drawRoundRect(outline, 3f, 3f, edgePaint)

            // A dashed line for anything laid into the joint rather than onto it, and a tick
            // where one system hands over to the next.
            var laid = bottom
            var edge = bottom
            rows.forEachIndexed { index, row ->
                if (row.millimetres == null) {
                    canvas.drawLine(stripLeft - 3f, laid, stripLeft + StripWidth + 3f, laid, dashPaint)
                } else {
                    laid -= (row.millimetres * perMm).toFloat()
                    edge -= (row.millimetres * perMm).toFloat()
                }
                val above = rows.getOrNull(index + 1)
                if (above != null && above.brand.isNotBlank() && !above.brand.equals(row.brand, true)) {
                    canvas.drawLine(stripLeft - 5f, edge, stripLeft, edge, systemPaint)
                }
            }
        }

        // The coats, top of the floor first, with the system named where it changes.
        var y = stackTop
        val topDown = rows.asReversed()
        topDown.forEachIndexed { fromTop, row ->
            val laidIn = row.millimetres == null
            val face = slabColour(row.weight)
            val band = RectF(rowsLeft, y, rowsLeft + rowsWidth, y + RowHeight)
            if (laidIn) {
                facePaint.color = Color.parseColor("#FAF7F1")
                canvas.drawRoundRect(band, 5f, 5f, facePaint)
                canvas.drawRoundRect(band, 5f, 5f, dashPaint)
            } else {
                facePaint.color = face
                canvas.drawRoundRect(band, 5f, 5f, facePaint)
            }
            val ink = if (!laidIn && luminance(face) < 0.42f) Color.WHITE else Color.parseColor("#262322")
            namePaint.color = ink
            figurePaint.color = if (row.mmText == null) Color.parseColor("#9C9488") else ink
            detailPaint.color = if (!laidIn && luminance(face) < 0.42f) {
                Color.parseColor("#E2DED6")
            } else {
                Color.parseColor("#6B6259")
            }
            // The name stops where the figure starts. A long product name used to run under it.
            val figureWidth = figurePaint.measureText(row.mmText ?: "—")
            val textRoom = rowsWidth - 18f - figureWidth - 9f
            canvas.drawText(fitText("${row.number} · ${row.title}", namePaint, textRoom), rowsLeft + 9f, y + 9f, namePaint)
            canvas.drawText(fitText(row.detail, detailPaint, textRoom), rowsLeft + 9f, y + 17.5f, detailPaint)
            canvas.drawText(row.mmText ?: "—", rowsLeft + rowsWidth - 9f, y + 13.5f, figurePaint)
            y += RowHeight + RowGap

            val below = topDown.getOrNull(fromTop + 1)
            if (below != null && below.brand.isNotBlank() && !below.brand.equals(row.brand, true)) {
                val lineY = y + SystemGap / 2f - RowGap / 2f
                canvas.drawLine(rowsLeft, lineY, rowsLeft + rowsWidth - 90f, lineY, faintPaint)
                canvas.drawText(below.brand.uppercase(), rowsLeft + rowsWidth, lineY + 2.6f, systemText)
                y += SystemGap
            }
        }
        return top + height(rows, width)
    }

    /** How many times the system changes on the way up. */
    private fun systemBreaks(rows: List<ReportCoat>): Int =
        rows.zipWithNext().count { (below, above) ->
            above.brand.isNotBlank() && !above.brand.equals(below.brand, true)
        }

    /** Heavier coats sit darker, the way the coarse layers do on a datasheet. */
    private fun slabColour(weight: Float): Int {
        val light = intArrayOf(0xF2, 0xEB, 0xE0)
        val dark = intArrayOf(0x4C, 0x51, 0x54)
        val mix = weight.coerceIn(0f, 1f)
        return Color.rgb(
            (light[0] + (dark[0] - light[0]) * mix).toInt(),
            (light[1] + (dark[1] - light[1]) * mix).toInt(),
            (light[2] + (dark[2] - light[2]) * mix).toInt(),
        )
    }

    private fun luminance(colour: Int): Float =
        (0.2126f * Color.red(colour) + 0.7152f * Color.green(colour) + 0.0722f * Color.blue(colour)) / 255f

    /** 0, 0.5, 1 — the way somebody would write the mark down. */
    private fun formatMark(mm: Double): String {
        val rounded = Math.round(mm * 100) / 100.0
        return if (rounded == rounded.toLong().toDouble()) {
            rounded.toLong().toString()
        } else {
            rounded.toString().trimEnd('0').trimEnd('.')
        }
    }
}
