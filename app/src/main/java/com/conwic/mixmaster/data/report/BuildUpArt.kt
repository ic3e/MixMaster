package com.conwic.mixmaster.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface

/** One coat of the build-up as the report draws it. */
internal data class ReportSlab(val title: String, val detail: String, val weight: Float)

/**
 * The floor build-up, drawn on the page the way a system datasheet draws it: the coats pulled
 * apart, the bottom one first, each with a line out to its name.
 *
 * The same picture as the one on the project screen and worked out from the same figures — a
 * report a client reads should show the floor they are buying, not only a list of what went
 * into it.
 */
internal object BuildUpArt {

    // Far enough apart that the slab in front cannot swallow the one behind it, and the
    // names further apart again, so the lines fan out to reach them.
    private const val SlabStep = 20f
    private const val LabelStep = 30f
    private const val Thinnest = 3f
    private const val Thickest = 12f
    private const val StackShare = 0.38f
    private const val DepthShare = 0.34f

    /** How tall the drawing will be, asked before there is a page to put it on. */
    fun height(rows: Int, width: Float): Float {
        if (rows <= 0) return 0f
        val depth = width * StackShare * DepthShare
        val stack = depth + SlabStep * (rows - 1) + Thickest
        // Four points of air above and eight below, so the top slab is not flush against
        // whatever line came before it.
        return maxOf(stack, LabelStep * rows) + 12f
    }

    /** Draws the stack and returns the y it finished at. */
    fun draw(canvas: Canvas, rows: List<ReportSlab>, left: Float, top: Float, width: Float): Float {
        if (rows.isEmpty()) return top
        val stack = width * StackShare
        val depth = stack * DepthShare
        val labelX = left + stack + 26f
        val labelWidth = width - stack - 26f

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#262322")
            textSize = 8.5f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#6B6259")
            textSize = 7.5f
            typeface = Typeface.SANS_SERIF
        }
        val leaderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#B4ADA3")
            strokeWidth = 0.6f
        }
        val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#8C867D")
            style = Paint.Style.STROKE
            strokeWidth = 0.6f
        }
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // The slabs sit close enough to overlap, the way a stack seen at an angle does; the
        // names need more room than that, so they are spread further apart and the lines fan
        // out to reach them. That fan is what a datasheet's callouts are.
        val stackHeight = depth + SlabStep * (rows.size - 1) + Thickest
        val labelsHeight = LabelStep * rows.size
        val total = maxOf(stackHeight, labelsHeight)
        val slabTop = top + 4f + (total - stackHeight) / 2f + depth / 2f
        val labelTop = top + 4f + (total - labelsHeight) / 2f

        // From the top of the stack down: on the page a coat lower down is the one nearer the
        // eye, so it is drawn last and covers the one behind it.
        for (fromTop in rows.indices) {
            val index = rows.size - 1 - fromTop
            val row = rows[index]
            val y = slabTop + fromTop * SlabStep
            val thickness = Thinnest + row.weight * (Thickest - Thinnest)
            val face = shade(row.weight)

            facePaint.color = darken(face, 0.84f)
            canvas.drawPath(side(left, y, left + stack / 2f, y + depth / 2f, thickness), facePaint)
            facePaint.color = darken(face, 0.68f)
            canvas.drawPath(side(left + stack / 2f, y + depth / 2f, left + stack, y, thickness), facePaint)
            facePaint.color = face
            val topFace = topFace(left, y, stack, depth)
            canvas.drawPath(topFace, facePaint)
            canvas.drawPath(topFace, edgePaint)

            val labelY = labelTop + LabelStep * (fromTop + 0.5f)
            val bendX = left + stack + (labelX - left - stack) * 0.45f
            canvas.drawLine(left + stack, y, bendX, labelY, leaderPaint)
            canvas.drawLine(bendX, labelY, labelX - 5f, labelY, leaderPaint)
            canvas.drawText(fit(row.title, titlePaint, labelWidth), labelX, labelY - 1f, titlePaint)
            canvas.drawText(fit(row.detail, detailPaint, labelWidth), labelX, labelY + 9f, detailPaint)
        }
        return top + total + 12f
    }

    /** The lit top of a slab: a flat diamond, left corner first. */
    private fun topFace(left: Float, y: Float, stack: Float, depth: Float): Path = Path().apply {
        moveTo(left, y)
        lineTo(left + stack / 2f, y - depth / 2f)
        lineTo(left + stack, y)
        lineTo(left + stack / 2f, y + depth / 2f)
        close()
    }

    /** One side of a slab, hanging [thickness] below the edge from (x1,y1) to (x2,y2). */
    private fun side(x1: Float, y1: Float, x2: Float, y2: Float, thickness: Float): Path =
        Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
            lineTo(x2, y2 + thickness)
            lineTo(x1, y1 + thickness)
            close()
        }

    /** Heavier coats sit darker, the way the coarse layers do on a datasheet. */
    private fun shade(weight: Float): Int {
        val light = intArrayOf(0xF2, 0xEB, 0xE0)
        val dark = intArrayOf(0x4C, 0x51, 0x54)
        val mix = weight.coerceIn(0f, 1f)
        return Color.rgb(
            (light[0] + (dark[0] - light[0]) * mix).toInt(),
            (light[1] + (dark[1] - light[1]) * mix).toInt(),
            (light[2] + (dark[2] - light[2]) * mix).toInt(),
        )
    }

    private fun darken(colour: Int, factor: Float): Int = Color.rgb(
        (Color.red(colour) * factor).toInt(),
        (Color.green(colour) * factor).toInt(),
        (Color.blue(colour) * factor).toInt(),
    )

    /** As much of the name as fits on the line, and an ellipsis where it does not. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (text.isEmpty() || paint.measureText(text) <= maxWidth) return text
        val room = maxWidth - paint.measureText("…")
        if (room <= 0f) return "…"
        val kept = paint.breakText(text, true, room, null)
        return text.take(kept.coerceAtLeast(0)).trimEnd() + "…"
    }
}
