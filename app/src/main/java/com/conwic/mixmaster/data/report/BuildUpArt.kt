package com.conwic.mixmaster.data.report

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
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
 * The words around the drawing: everything in it that had to be said in the reader's language.
 *
 * Kept out of the drawing itself so the drawing has no opinion about wording, and so all of the
 * translating happens where the rest of the report's translating happens.
 */
internal data class ReportBuildUp(
    /** The small capitals at the head of the block. */
    val caption: String,
    /** "Showroom — 48 m²". */
    val title: String,
    /** "8 coats · 2 systems · ≈ 2.03 mm of film, wet", along the top right. */
    val facts: String,
    /** The total, written short, for the dimension line down the left: "2.03 mm". */
    val totalLabel: String?,
    /** What the dashed lines mean, along the bottom. */
    val note: String,
    /** Whose drawing it is, bottom right. */
    val mark: String,
)

/**
 * The floor build-up on the page: a strip drawn to scale against a millimetre rule, and the
 * coats beside it in laying order.
 *
 * The same picture as the one on the project screen, worked out from the same figures — a
 * report a client reads should show the floor they are buying, not only a list of what went
 * into it. On paper it gets the apparatus a section drawing gets: a dimension line down the
 * left carrying the total, leaders from the strip across to the coats they belong to, and a
 * bracket down the right around each system, so nobody has to count bands to see where one
 * manufacturer's system stops and the next starts.
 */
internal object BuildUpArt {

    // Tightened twice once the report was read on paper: the drawing was taking a third of a
    // page per room and pushing the figures a client actually reads onto the next one.
    private const val RowHeight = 16f
    private const val RowGap = 3.5f

    /** The dimension line down the left, with the total turned on its side against it. */
    private const val DimWidth = 22f
    private const val RulerWidth = 22f
    private const val RulerGap = 4f
    private const val StripWidth = 15f
    /** Where the leaders run from the strip across to the coats. */
    private const val LeadWidth = 20f
    private const val BracketGap = 6f
    private const val BracketWidth = 58f

    /** Caption, title and the rule under them. */
    private const val HeaderHeight = 40f
    /** The hairline, the note under it, and air. Two lines of note are always allowed for. */
    private const val FootHeight = 30f
    private const val NoteLines = 2

    /** How tall the whole block will be, asked before there is a page to put it on. */
    fun height(rows: List<ReportCoat>): Float {
        if (rows.isEmpty()) return 0f
        return HeaderHeight + stackHeight(rows) + FootHeight
    }

    private fun stackHeight(rows: List<ReportCoat>): Float =
        rows.size * RowHeight + (rows.size - 1) * RowGap

    /** Draws it and returns the y it finished at. */
    fun draw(
        canvas: Canvas,
        rows: List<ReportCoat>,
        block: ReportBuildUp,
        left: Float,
        top: Float,
        width: Float,
    ): Float {
        if (rows.isEmpty()) return top
        val total = rows.mapNotNull { it.millimetres }.sum()
        // Without a single thickness on file there is no scale to draw, so the coats take the
        // width the scale would have had rather than sitting beside an empty column.
        val scaled = total > 0.0

        val namePaint = paint("#262322", 8f, bold = true)
        val detailPaint = paint("#6B6259", 6.5f)
        val figurePaint = paint("#262322", 8.5f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val numberPaint = paint("#262322", 6.5f, bold = true).apply { textAlign = Paint.Align.CENTER }
        val markPaint = paint("#6B6259", 6f).apply { textAlign = Paint.Align.RIGHT }
        val capsPaint = paint("#6B6259", 6.5f, bold = true).apply { letterSpacing = 0.14f }
        val headPaint = paint("#262322", 11f, bold = true)
        val factsPaint = paint("#6B6259", 7f).apply { textAlign = Paint.Align.RIGHT }
        val notePaint = paint("#6B6259", 6f)
        val signPaint = paint("#9C9488", 6f).apply { textAlign = Paint.Align.RIGHT }
        val dimPaint = paint("#141311", 8f, bold = true).apply { textAlign = Paint.Align.CENTER }
        val bracketText = paint("#8A5A2E", 6f, bold = true).apply { letterSpacing = 0.13f }

        val rulePaint = stroke("#9C9488", 0.6f)
        val faintPaint = stroke("#CFC8BA", 0.6f)
        val leadPaint = stroke("#CFC8BA", 0.5f).apply { style = Paint.Style.STROKE }
        val bracketPaint = stroke("#C4BCAC", 0.6f)
        val hairPaint = stroke("#EBE7DE", 0.6f)
        val hatchPaint = stroke("#F0EDE6", 0.7f)
        val brownPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#8A5A2E") }
        val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
        val edgePaint = stroke("#DCD6C9", 0.8f).apply { style = Paint.Style.STROKE }
        val dashPaint = stroke("#9C9488", 1.1f).apply {
            style = Paint.Style.STROKE
            pathEffect = DashPathEffect(floatArrayOf(3f, 2.5f), 0f)
        }
        val systemPaint = stroke("#8A5A2E", 1.1f)
        val facePaint = Paint(Paint.ANTI_ALIAS_FLAG)

        // The head of the block: what this is, which floor, and what it comes to — over a brown
        // rule, the way every other heading on the report is set.
        canvas.drawText(block.caption.uppercase(), left, top + 8f, capsPaint)
        canvas.drawText(fitText(block.title, headPaint, width * 0.55f), left, top + 22f, headPaint)
        canvas.drawText(fitText(block.facts, factsPaint, width * 0.42f), left + width, top + 22f, factsPaint)
        canvas.drawRect(left, top + 27f, left + width, top + 28.2f, brownPaint)

        val stackTop = top + HeaderHeight
        val stack = stackHeight(rows)
        val bottom = stackTop + stack

        val dimW = if (scaled) DimWidth else 0f
        val rulerW = if (scaled) RulerWidth + RulerGap else 0f
        val stripW = if (scaled) StripWidth else 0f
        val leadW = if (scaled) LeadWidth else 0f
        val dimLine = left + 13f
        val stripLeft = left + dimW + rulerW
        val leadLeft = stripLeft + stripW
        val rowsLeft = leadLeft + leadW
        val bracketLeft = left + width - BracketWidth
        val rowsRight = bracketLeft - BracketGap

        // Where each coat sits on the strip, from the concrete up: the middle of its band, or
        // the joint it is laid into for one that has no thickness of its own. The leaders are
        // drawn from these, so they land on the coat they name rather than near it.
        val onStrip = FloatArray(rows.size)
        if (scaled) {
            val perMm = (stack / total).toFloat()
            var cursor = bottom
            rows.forEachIndexed { index, row ->
                val mm = row.millimetres
                if (mm == null) {
                    onStrip[index] = cursor
                } else {
                    val band = (mm * perMm).toFloat()
                    onStrip[index] = cursor - band / 2f
                    cursor -= band
                }
            }

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
            // What the figures on the scale are, said once at the head of it.
            canvas.drawText("MM", stripLeft - 9f, stackTop - 3f, markPaint)

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

            // The dimension line: the whole build-up measured off down the left, with the figure
            // turned on its side against it and the rule broken to let it through.
            canvas.drawLine(dimLine, stackTop, dimLine, bottom, rulePaint)
            canvas.drawLine(dimLine - 5f, stackTop, dimLine + 5f, stackTop, rulePaint)
            canvas.drawLine(dimLine - 5f, bottom, dimLine + 5f, bottom, rulePaint)
            block.totalLabel?.let { label ->
                canvas.save()
                canvas.translate(dimLine, (stackTop + bottom) / 2f)
                canvas.rotate(-90f)
                val half = dimPaint.measureText(label) / 2f
                canvas.drawRect(-half - 3f, -5.5f, half + 3f, 5.5f, whitePaint)
                canvas.drawText(label, 0f, 3f, dimPaint)
                canvas.restore()
            }
        }

        // The coats, top of the floor first.
        val topDown = rows.asReversed()
        topDown.forEachIndexed { fromTop, row ->
            val y = stackTop + fromTop * (RowHeight + RowGap)
            val laidIn = row.millimetres == null
            val face = slabColour(row.weight)
            val band = RectF(rowsLeft, y, rowsRight, y + RowHeight)
            if (laidIn) {
                // Hatched and dashed, the way a section drawing marks something that is not a
                // layer in its own right: this one is worked into the coats around it.
                facePaint.color = Color.parseColor("#FBFAF8")
                canvas.drawRoundRect(band, 4f, 4f, facePaint)
                canvas.save()
                canvas.clipPath(Path().apply { addRoundRect(band, 4f, 4f, Path.Direction.CW) })
                var hatch = band.left - RowHeight
                while (hatch < band.right) {
                    canvas.drawLine(hatch, band.bottom, hatch + RowHeight, band.top, hatchPaint)
                    hatch += 4f
                }
                canvas.restore()
                canvas.drawRoundRect(band, 4f, 4f, dashPaint)
            } else {
                facePaint.color = face
                canvas.drawRoundRect(band, 4f, 4f, facePaint)
            }
            val pale = !laidIn && luminance(face) < 0.42f
            val ink = if (pale) Color.WHITE else Color.parseColor("#262322")
            namePaint.color = ink
            numberPaint.color = ink
            figurePaint.color = if (row.mmText == null) Color.parseColor("#9C9488") else ink
            detailPaint.color = if (pale) Color.parseColor("#E2DED6") else Color.parseColor("#6B6259")

            // The coat's number in a disc, the way it is on the screen, so the two drawings are
            // read the same way round.
            val middle = y + RowHeight / 2f
            facePaint.color = if (pale) Color.argb(72, 255, 255, 255) else Color.argb(30, 20, 19, 17)
            canvas.drawCircle(rowsLeft + 12.5f, middle, 5.5f, facePaint)
            canvas.drawText("${row.number}", rowsLeft + 12.5f, middle + 2.3f, numberPaint)

            // The name stops where the figure starts. A long product name used to run under it.
            val textLeft = rowsLeft + 23f
            val figureWidth = figurePaint.measureText(row.mmText ?: "—")
            val textRoom = rowsRight - 7f - figureWidth - 6f - textLeft
            canvas.drawText(fitText(row.title, namePaint, textRoom), textLeft, y + 7.5f, namePaint)
            canvas.drawText(fitText(row.detail, detailPaint, textRoom), textLeft, y + 14f, detailPaint)
            canvas.drawText(row.mmText ?: "—", rowsRight - 7f, y + 11f, figurePaint)

            // And the leader across from the strip, stepped rather than straight, so eight of
            // them can cross the gap without turning it into a fan.
            if (scaled) {
                val from = onStrip[rows.size - 1 - fromTop]
                val path = Path().apply {
                    moveTo(leadLeft + 1f, from)
                    lineTo(leadLeft + 6f, from)
                    lineTo(leadLeft + 13f, middle)
                    lineTo(rowsLeft, middle)
                }
                canvas.drawPath(path, leadPaint)
            }
        }

        // A bracket down the right around each system, named beside it: what a datasheet does,
        // and the one way of saying "these four coats are one product family" that survives
        // being read in a van.
        var groupStart = 0
        topDown.forEachIndexed { fromTop, row ->
            val below = topDown.getOrNull(fromTop + 1)
            val ends = below == null || !below.brand.equals(row.brand, true)
            if (!ends) return@forEachIndexed
            if (row.brand.isNotBlank()) {
                val yTop = stackTop + groupStart * (RowHeight + RowGap)
                val yBottom = stackTop + fromTop * (RowHeight + RowGap) + RowHeight
                canvas.drawLine(bracketLeft, yTop, bracketLeft, yBottom, bracketPaint)
                canvas.drawLine(bracketLeft, yTop, bracketLeft + 6f, yTop, bracketPaint)
                canvas.drawLine(bracketLeft, yBottom, bracketLeft + 6f, yBottom, bracketPaint)
                val lines = wrapCaps(row.brand.uppercase(), bracketText, BracketWidth - 9f, 2)
                val first = (yTop + yBottom) / 2f - (lines.size - 1) * 4f + 2.2f
                lines.forEachIndexed { line, text ->
                    canvas.drawText(text, bracketLeft + 9f, first + line * 8f, bracketText)
                }
            }
            groupStart = fromTop + 1
        }

        // The small print: why two of the coats have no figure, and whose drawing this is.
        val hair = bottom + 9f
        canvas.drawLine(left, hair, left + width, hair, hairPaint)
        val signWidth = signPaint.measureText(block.mark) + 10f
        wrapCaps(block.note, notePaint, width - signWidth, NoteLines).forEachIndexed { line, text ->
            canvas.drawText(text, left, hair + 8f + line * 7.5f, notePaint)
        }
        canvas.drawText(block.mark, left + width, hair + 8f, signPaint)

        return top + height(rows)
    }

    /**
     * Breaks a line over at most [maxLines], on a space where there is one, and ellipsises the
     * last one if the words run out of room.
     */
    private fun wrapCaps(text: String, paint: Paint, maxWidth: Float, maxLines: Int): List<String> {
        if (text.isBlank() || maxWidth <= 0f) return emptyList()
        val words = text.trim().split(' ').filter { it.isNotEmpty() }
        val lines = mutableListOf<String>()
        var line = StringBuilder()
        words.forEachIndexed { index, word ->
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) <= maxWidth || line.isEmpty()) {
                line = StringBuilder(candidate)
            } else {
                lines += line.toString()
                if (lines.size == maxLines) {
                    // Out of lines with words still to place: the last one takes what it can and
                    // says so. Counted by position, not by looking the word up — a note that
                    // says "the" twice would otherwise rewind to the first one.
                    val rest = words.drop(index).joinToString(" ")
                    lines[maxLines - 1] = fitText("${lines[maxLines - 1]} $rest", paint, maxWidth)
                    return lines
                }
                line = StringBuilder(word)
            }
        }
        if (line.isNotEmpty()) lines += line.toString()
        return if (lines.size <= maxLines) lines else lines.take(maxLines)
    }

    private fun paint(colour: String, size: Float, bold: Boolean = false): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colour)
            textSize = size
            typeface = if (bold) Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD) else Typeface.SANS_SERIF
        }

    private fun stroke(colour: String, width: Float): Paint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor(colour)
            strokeWidth = width
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
