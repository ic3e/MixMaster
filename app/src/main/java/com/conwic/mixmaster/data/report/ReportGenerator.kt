package com.conwic.mixmaster.data.report

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.graphics.Matrix
import android.graphics.Path
import androidx.core.graphics.PathParser
import androidx.core.content.FileProvider
import com.conwic.mixmaster.R
import com.conwic.mixmaster.data.db.entity.ProductEntity
import com.conwic.mixmaster.data.db.entity.ProjectEntity
import com.conwic.mixmaster.data.db.entity.RoomAreaEntity
import com.conwic.mixmaster.data.db.entity.TaskEntity
import com.conwic.mixmaster.domain.MixResult
import com.conwic.mixmaster.domain.formatArea
import com.conwic.mixmaster.domain.formatKg
import java.io.File
import java.io.FileOutputStream

private const val PAGE_WIDTH = 595 // A4 @ 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 48f

/**
 * Builds a branded, letterhead-style PDF project report — the "full detailed report" feature
 * from the original brief — using the platform's own PdfDocument/Canvas APIs, so no extra PDF
 * library dependency is needed.
 */
object ReportGenerator {

    fun generate(
        context: Context,
        project: ProjectEntity,
        rooms: List<RoomAreaEntity>,
        roomMixes: Map<Long, MixResult?>,
        products: List<ProductEntity>,
        tasks: List<TaskEntity>,
    ): Uri {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas
        var y = drawLetterhead(context, canvas, project)

        fun newPageIfNeeded(nextRowHeight: Float) {
            if (y + nextRowHeight > PAGE_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN
            }
        }

        val titlePaint = textPaint(size = 12f, bold = true)
        val bodyPaint = textPaint(size = 10f, bold = false)
        val dimPaint = textPaint(size = 9f, bold = false, colorHex = "#6B6259")

        y += 12f
        canvas.drawText(context.getString(R.string.report_client, project.clientName), MARGIN, y, bodyPaint)
        y += 16f
        canvas.drawText(context.getString(R.string.report_site, project.address), MARGIN, y, bodyPaint)
        y += 16f
        canvas.drawText(
            context.getString(
                R.string.report_dates,
                project.startDate?.toString() ?: "—",
                project.targetFinishDate?.toString() ?: "—",
            ),
            MARGIN,
            y,
            bodyPaint,
        )
        y += 24f

        canvas.drawText(context.getString(R.string.report_scope), MARGIN, y, titlePaint)
        y += 16f
        y = drawWrapped(canvas, project.scopeNotes.ifBlank { context.getString(R.string.report_no_scope) }, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, bodyPaint)
        y += 20f

        newPageIfNeeded(24f)
        canvas.drawText(context.getString(R.string.report_rooms), MARGIN, y, titlePaint)
        y += 18f
        val totalGrams = roomMixes.values.filterNotNull().sumOf { it.totalGrams }
        for (room in rooms) {
            newPageIfNeeded(30f)
            val mix = roomMixes[room.id]
            val productName = products.firstOrNull { it.id == room.assignedProductId }?.name ?: context.getString(R.string.prj_unassigned)
            canvas.drawText("${room.name} — ${formatArea(room.areaM2)} m²", MARGIN, y, bodyPaint)
            y += 13f
            val detail = if (mix != null) {
                "$productName · ${formatKg(mix.totalGrams)} kg (" + mix.components.joinToString(" / ") { "${formatKg(it.grams)} kg ${it.label}" } + ")"
            } else {
                productName
            }
            canvas.drawText(detail, MARGIN + 12f, y, dimPaint)
            y += 18f
        }
        newPageIfNeeded(20f)
        canvas.drawText(context.getString(R.string.report_total_material, formatKg(totalGrams)), MARGIN, y, textPaint(size = 10f, bold = true))
        y += 26f

        newPageIfNeeded(24f)
        canvas.drawText(context.getString(R.string.report_tasks), MARGIN, y, titlePaint)
        y += 18f
        for (task in tasks.sortedBy { it.dueDate }) {
            newPageIfNeeded(16f)
            val status = if (task.isDone) context.getString(R.string.report_task_done) else ""
            canvas.drawText(
                context.getString(
                    R.string.report_task_line,
                    status,
                    task.title,
                    task.dueDate?.toString() ?: context.getString(R.string.report_no_due),
                ),
                MARGIN,
                y,
                bodyPaint,
            )
            y += 15f
        }

        pdf.finishPage(page)

        val reportsDir = File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }
        val safeName = project.name.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        val file = File(reportsDir, "MixMaster-Report-$safeName-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    /**
     * Scales one path from its drawable viewport into place on the page and fills it.
     *
     * [left] and [top] are the lockup's own origin, so every piece of the badge is scaled and
     * placed identically and the knockout stays registered with the block behind it.
     */
    private fun drawVectorPath(
        canvas: Canvas,
        pathData: String,
        viewportHeight: Float,
        left: Float,
        top: Float,
        height: Float,
        color: Int,
        evenOdd: Boolean,
    ) {
        // Throws rather than returning null if the data is malformed; a letterhead is
        // not worth losing the whole report over.
        val path = runCatching { PathParser.createPathFromPathData(pathData) }.getOrNull() ?: return
        if (evenOdd) path.fillType = Path.FillType.EVEN_ODD
        val scale = height / viewportHeight
        path.transform(
            Matrix().apply {
                setScale(scale, scale)
                postTranslate(left, top)
            },
        )
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = Paint.Style.FILL
            },
        )
    }

    private fun drawLetterhead(context: Context, canvas: Canvas, project: ProjectEntity): Float {
        // The full company lockup at the head of the page — badge, CONWIC, and the line under it.
        //
        // Drawn as paths, not as the drawables. VectorDrawable.draw() renders into a bitmap the
        // size of its bounds, so asking for it at 34pt put a 34px image in the PDF: the logo
        // came out visibly chewed while the text next to it, which PdfDocument records as real
        // text, stayed sharp. drawPath is recorded as vector art and stays sharp at any zoom.
        val logoHeight = 34f
        val logoTop = 30f
        var cursorX = MARGIN

        val badgeW = context.resources.getInteger(R.integer.conwic_badge_viewport_w).toFloat()
        val badgeH = context.resources.getInteger(R.integer.conwic_badge_viewport_h).toFloat()
        drawVectorPath(
            canvas, context.getString(R.string.conwic_badge_block_path),
            badgeH, cursorX, logoTop, logoHeight,
            Color.parseColor("#704727"), evenOdd = false,
        )
        drawVectorPath(
            canvas, context.getString(R.string.conwic_badge_mark_path),
            badgeH, cursorX, logoTop, logoHeight,
            Color.WHITE, evenOdd = true,
        )
        cursorX += logoHeight * badgeW / badgeH + logoHeight * 37f / 246f

        val wordH = context.resources.getInteger(R.integer.conwic_wordmark_viewport_h).toFloat()
        // The wordmark ships white so it can sit on anything; on paper it wants ink.
        drawVectorPath(
            canvas, context.getString(R.string.conwic_wordmark_path),
            wordH, cursorX, logoTop, logoHeight,
            Color.parseColor("#262322"), evenOdd = true,
        )

        val titlePaint = textPaint(size = 18f, bold = true)
        canvas.drawText(project.name, MARGIN, 104f, titlePaint)

        val datePaint = textPaint(size = 9f, bold = false, colorHex = "#6C6459")
        canvas.drawText(
            context.getString(R.string.report_subtitle, java.time.LocalDate.now().toString()),
            MARGIN,
            120f,
            datePaint,
        )

        val rulePaint = Paint().apply { color = Color.parseColor("#8A5A2E") }
        canvas.drawRect(MARGIN, 132f, PAGE_WIDTH - MARGIN, 134f, rulePaint)

        return 160f
    }

    private fun textPaint(size: Float, bold: Boolean, colorHex: String = "#262322"): Paint = Paint().apply {
        textSize = size
        isAntiAlias = true
        color = Color.parseColor(colorHex)
        typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
    }

    /** Simple greedy word-wrap since Canvas has no built-in multi-line text drawing. */
    private fun drawWrapped(canvas: Canvas, text: String, x: Float, startY: Float, maxWidth: Float, paint: Paint): Float {
        var y = startY
        val words = text.split(" ")
        var line = StringBuilder()
        for (word in words) {
            val candidate = if (line.isEmpty()) word else "$line $word"
            if (paint.measureText(candidate) > maxWidth && line.isNotEmpty()) {
                canvas.drawText(line.toString(), x, y, paint)
                y += 14f
                line = StringBuilder(word)
            } else {
                line = StringBuilder(candidate)
            }
        }
        if (line.isNotEmpty()) {
            canvas.drawText(line.toString(), x, y, paint)
            y += 14f
        }
        return y
    }
}
