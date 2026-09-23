package com.conwic.mixmaster.data.report

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private const val PAGE_WIDTH = 595 // A4 @ 72dpi
private const val PAGE_HEIGHT = 842
private const val MARGIN = 48f

/** One line of the list, already worded by the screen — this file knows no language. */
data class PickupLine(val name: String, val amount: String, val short: String?)

/**
 * The pick-up list on paper.
 *
 * A printed list is what gets taken to the rack and marked off, so every line carries an empty
 * box to tick and the figure is set out on the right where a column of them can be read down.
 * Printing goes through Android's own dialog, which is also where "Save as PDF" lives — so the
 * same sheet can be printed in the office or sent from the van.
 */
object PickupList {

    fun print(context: Context, title: String, subtitle: String, lines: List<PickupLine>) {
        // The print service will only take a job from an activity, and a sheet's context is the
        // dialog window it lives in — asking that one to print throws. Unwrapped rather than
        // passed in, so every caller does not have to know this.
        val activity = context.findActivity() ?: return
        val file = write(activity, title, subtitle, lines)
        val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return
        printManager.print(
            title,
            PdfFileAdapter(file, title),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build(),
        )
    }

    private fun write(context: Context, title: String, subtitle: String, lines: List<PickupLine>): File {
        val pdf = PdfDocument()
        var pageNumber = 1
        var page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
        var canvas = page.canvas

        val headingPaint = textPaint(18f, bold = true)
        val subPaint = textPaint(10f, bold = false, colorHex = "#6B6259")
        val namePaint = textPaint(11f, bold = false)
        val amountPaint = textPaint(12f, bold = true).apply { textAlign = Paint.Align.RIGHT }
        val shortPaint = textPaint(9f, bold = false, colorHex = "#B3261E")
        val boxPaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.parseColor("#6B6259")
            isAntiAlias = true
        }
        val rulePaint = Paint().apply { color = Color.parseColor("#E5DFD7") }

        var y = MARGIN + 18f
        canvas.drawText(title, MARGIN, y, headingPaint)
        y += 16f
        canvas.drawText(subtitle, MARGIN, y, subPaint)
        y += 12f
        canvas.drawRect(MARGIN, y, PAGE_WIDTH - MARGIN, y + 1.5f, rulePaint)
        y += 22f

        lines.forEach { line ->
            if (y + 34f > PAGE_HEIGHT - MARGIN) {
                pdf.finishPage(page)
                pageNumber += 1
                page = pdf.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
                canvas = page.canvas
                y = MARGIN + 18f
            }
            // The box is what makes it a list rather than a printout: it gets ticked at the rack.
            canvas.drawRect(MARGIN, y - 9f, MARGIN + 11f, y + 2f, boxPaint)
            canvas.drawText(line.name, MARGIN + 22f, y, namePaint)
            canvas.drawText(line.amount, PAGE_WIDTH - MARGIN, y, amountPaint)
            y += 13f
            line.short?.let { note ->
                canvas.drawText(note, MARGIN + 22f, y, shortPaint)
                y += 12f
            }
            y += 9f
        }

        pdf.finishPage(page)

        val dir = File(context.getExternalFilesDir(null), "reports").apply { mkdirs() }
        val safeName = title.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-')
        val file = File(dir, "MixMaster-$safeName-${System.currentTimeMillis()}.pdf")
        FileOutputStream(file).use { pdf.writeTo(it) }
        pdf.close()
        return file
    }

    private fun Context.findActivity(): Activity? {
        var context: Context? = this
        while (context is ContextWrapper) {
            if (context is Activity) return context
            context = context.baseContext
        }
        return null
    }

    private fun textPaint(size: Float, bold: Boolean, colorHex: String = "#262322"): Paint = Paint().apply {
        textSize = size
        isAntiAlias = true
        color = Color.parseColor(colorHex)
        typeface = android.graphics.Typeface.create(
            android.graphics.Typeface.DEFAULT,
            if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL,
        )
    }
}

/** Hands a finished PDF straight to the print service, page count and all. */
private class PdfFileAdapter(private val file: File, private val jobName: String) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.LayoutResultCallback,
        extras: Bundle?,
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback.onLayoutCancelled()
            return
        }
        callback.onLayoutFinished(
            PrintDocumentInfo.Builder(jobName)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build(),
            true,
        )
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal?,
        callback: PrintDocumentAdapter.WriteResultCallback,
    ) {
        runCatching {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
        }.onFailure {
            callback.onWriteFailed(it.message)
            return
        }
        callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
    }
}
