package hi3.hashkit.integrations.print

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import hi3.hashkit.domain.tag.MinerTag
import java.io.File
import java.io.FileOutputStream

/**
 * Prints QR asset tags for miners: a grid of ~38 mm stickers on A4/Letter, each with a QR
 * code plus the miner's name, MAC, IP and location in text. The QR encodes the canonical
 * [MinerTag] `key=value` payload, so a printed sticker scans in the AR rack overlay (and
 * matches the NFC tag format). Rendering is a plain PDF handed to Android's print framework,
 * so any printer — or "Save as PDF" — works.
 */
object AssetTagPrinter {

    data class TagData(val name: String, val mac: String?, val ip: String?, val location: String?)

    // All layout in PDF points (1/72"). 1 mm = 2.8346 pt.
    private const val MM = 2.8346f
    private const val PAGE_W = (210 * MM)   // A4 portrait
    private const val PAGE_H = (297 * MM)
    private const val MARGIN = 10 * MM

    /** A sticker layout, in millimetres. All the on-page geometry scales from these. */
    private class Sheet(tagMm: Float, gapMm: Float, qrMm: Float, nameMm: Float, subMm: Float) {
        val tag = tagMm * MM        // sticker edge
        val gap = gapMm * MM
        val qr = qrMm * MM          // QR edge inside the tag
        val nameSize = nameMm * MM
        val subSize = subMm * MM
        val qrTop = tag * 0.04f     // gap above the QR
        val lineStep = subSize * 1.3f
    }

    /** Sticker size the user can pick when printing. */
    enum class TagSize(val label: String) {
        STANDARD("Standard — 38 mm"),
        SMALL("Small — 25 mm"),
    }

    private fun sheetFor(size: TagSize): Sheet = when (size) {
        TagSize.STANDARD -> Sheet(38f, 5f, 22f, 3.4f, 2.5f)
        TagSize.SMALL -> Sheet(25f, 4f, 15f, 2.3f, 1.8f)
    }

    fun print(context: Context, tags: List<TagData>, size: TagSize = TagSize.STANDARD) {
        if (tags.isEmpty()) return
        val sheet = sheetFor(size)
        val pdf = File(context.cacheDir, "asset-tags.pdf")
        renderPdf(tags, pdf, sheet)
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
        printManager.print(
            "Hashkit asset tags",
            PdfPrintAdapter(pdf, pageCount(tags.size, sheet)),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_MONOCHROME)
                .build(),
        )
    }

    private fun columns(s: Sheet) = ((PAGE_W - 2 * MARGIN + s.gap) / (s.tag + s.gap)).toInt()
    private fun rows(s: Sheet) = ((PAGE_H - 2 * MARGIN + s.gap) / (s.tag + s.gap)).toInt()
    private fun perPage(s: Sheet) = columns(s) * rows(s)
    private fun pageCount(n: Int, s: Sheet) = (n + perPage(s) - 1) / perPage(s)

    private fun renderPdf(tags: List<TagData>, out: File, s: Sheet) {
        val doc = PdfDocument()
        val border = Paint().apply {
            style = Paint.Style.STROKE; strokeWidth = 0.5f; color = Color.LTGRAY
        }
        val nameText = Paint().apply {
            isAntiAlias = true; textSize = s.nameSize; typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER; color = Color.BLACK
        }
        val smallText = Paint().apply {
            isAntiAlias = true; textSize = s.subSize; typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER; color = Color.DKGRAY
        }
        val cols = columns(s)
        tags.chunked(perPage(s)).forEachIndexed { pageIdx, pageTags ->
            val page = doc.startPage(
                PdfDocument.PageInfo.Builder(PAGE_W.toInt(), PAGE_H.toInt(), pageIdx + 1).create()
            )
            val canvas = page.canvas
            pageTags.forEachIndexed { i, tag ->
                val col = i % cols
                val row = i / cols
                val x = MARGIN + col * (s.tag + s.gap)
                val y = MARGIN + row * (s.tag + s.gap)
                // Cut line.
                canvas.drawRect(x, y, x + s.tag, y + s.tag, border)
                // QR, centered near the top.
                val qr = qrBitmap(
                    MinerTag.encode(tag.name, tag.mac, tag.ip, tag.location),
                    s.qr.toInt() * 4, // 4x supersample keeps modules crisp at print resolution
                )
                val qrLeft = x + (s.tag - s.qr) / 2
                canvas.drawBitmap(qr, null, android.graphics.RectF(qrLeft, y + s.qrTop, qrLeft + s.qr, y + s.qrTop + s.qr), null)
                qr.recycle()
                // Text block under the QR: name, MAC, IP, location (skip blanks).
                val cx = x + s.tag / 2
                var ty = y + s.qrTop + s.qr + s.nameSize
                canvas.drawText(fit(tag.name, nameText, s.tag - 3 * MM), cx, ty, nameText)
                listOfNotNull(tag.mac, tag.ip, tag.location)
                    .filter { it.isNotBlank() }
                    .forEach { line ->
                        ty += s.lineStep
                        canvas.drawText(fit(line, smallText, s.tag - 3 * MM), cx, ty, smallText)
                    }
            }
            doc.finishPage(page)
        }
        FileOutputStream(out).use { doc.writeTo(it) }
        doc.close()
    }

    /** Ellipsize [text] so it fits [maxWidth] at the paint's text size. */
    private fun fit(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    private fun qrBitmap(payload: String, sizePx: Int): Bitmap {
        val matrix = QRCodeWriter().encode(
            payload, BarcodeFormat.QR_CODE, sizePx, sizePx,
            mapOf(
                EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                EncodeHintType.MARGIN to 1, // quiet zone in modules; the tag's white space adds more
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        for (yy in 0 until sizePx) for (xx in 0 until sizePx) {
            bmp.setPixel(xx, yy, if (matrix[xx, yy]) Color.BLACK else Color.WHITE)
        }
        return bmp
    }

    /** Serves a pre-rendered PDF file to the print framework. */
    private class PdfPrintAdapter(private val file: File, private val pages: Int) : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal?,
            callback: LayoutResultCallback,
            extras: android.os.Bundle?,
        ) {
            if (cancellationSignal?.isCanceled == true) { callback.onLayoutCancelled(); return }
            callback.onLayoutFinished(
                PrintDocumentInfo.Builder("hashkit-asset-tags.pdf")
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .setPageCount(pages)
                    .build(),
                true,
            )
        }

        override fun onWrite(
            pageRanges: Array<out PageRange>?,
            destination: ParcelFileDescriptor,
            cancellationSignal: CancellationSignal?,
            callback: WriteResultCallback,
        ) {
            runCatching {
                file.inputStream().use { input ->
                    FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) }
                }
            }.onSuccess {
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure {
                callback.onWriteFailed(it.message)
            }
        }
    }
}
