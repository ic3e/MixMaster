package com.conwic.mixmaster.data.report

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
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
        canvas.drawText("Client: ${project.clientName}", MARGIN, y, bodyPaint)
        y += 16f
        canvas.drawText("Site address: ${project.address}", MARGIN, y, bodyPaint)
        y += 16f
        canvas.drawText("Start: ${project.startDate ?: "—"}    Target finish: ${project.targetFinishDate ?: "—"}", MARGIN, y, bodyPaint)
        y += 24f

        canvas.drawText("Scope", MARGIN, y, titlePaint)
        y += 16f
        y = drawWrapped(canvas, project.scopeNotes.ifBlank { "No scope notes recorded." }, MARGIN, y, PAGE_WIDTH - 2 * MARGIN, bodyPaint)
        y += 20f

        newPageIfNeeded(24f)
        canvas.drawText("Rooms & materials", MARGIN, y, titlePaint)
        y += 18f
        val totalGrams = roomMixes.values.filterNotNull().sumOf { it.totalGrams }
        for (room in rooms) {
            newPageIfNeeded(30f)
            val mix = roomMixes[room.id]
            val productName = products.firstOrNull { it.id == room.assignedProductId }?.name ?: "Unassigned"
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
        canvas.drawText("Total material across all rooms: ${formatKg(totalGrams)} kg", MARGIN, y, textPaint(size = 10f, bold = true))
        y += 26f

        newPageIfNeeded(24f)
        canvas.drawText("Tasks", MARGIN, y, titlePaint)
        y += 18f
        for (task in tasks.sortedBy { it.dueDate }) {
            newPageIfNeeded(16f)
            val status = if (task.isDone) "[done] " else ""
            canvas.drawText("$status${task.title} — ${task.dueDate ?: "no due date"}", MARGIN, y, bodyPaint)
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

    private fun drawLetterhead(context: Context, canvas: Canvas, project: ProjectEntity): Float {
        val bandPaint = Paint().apply { color = Color.parseColor("#8A5A2E") }
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 64f, bandPaint)

        val logoBitmap = runCatching {
            BitmapFactory.decodeResource(context.resources, R.drawable.conwic_badge)
        }.getOrNull()
        if (logoBitmap != null) {
            val targetHeight = 40
            val scale = targetHeight / logoBitmap.height.toFloat()
            val targetWidth = (logoBitmap.width * scale).toInt()
            val scaled = android.graphics.Bitmap.createScaledBitmap(logoBitmap, targetWidth, targetHeight, true)
            canvas.drawBitmap(scaled, MARGIN, 12f, null)
        }

        val titlePaint = textPaint(size = 16f, bold = true, colorHex = "#FFFFFF")
        canvas.drawText(project.name, MARGIN + 90f, 38f, titlePaint)

        val datePaint = textPaint(size = 9f, bold = false, colorHex = "#FFFFFF")
        canvas.drawText("MixMaster project report · generated ${java.time.LocalDate.now()}", MARGIN + 90f, 54f, datePaint)

        return 90f
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
