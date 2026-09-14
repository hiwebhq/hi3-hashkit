package hi3.hashkit.ui.share

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.R
import java.io.File
import java.io.FileOutputStream
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Draws a shareable "stats card" PNG for one miner — the thing you post when your Bitaxe
 * finds a big share. Pure android.graphics so it renders identically regardless of
 * theme or screen; the accent color follows the user's UI theme. Output lands in the
 * exports cache directory served by the app's FileProvider.
 */
@Singleton
class StatsCardRenderer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class Stats(
        val minerName: String,
        val model: String?,
        val hashrate: String,
        val bestShare: String,
        val percentOfBlock: String?,
        val uptime: String,
        val efficiency: String,
        val daysMining: Int,
        val accentArgb: Int,
    )

    fun render(stats: Stats): File {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        drawHeader(canvas, stats.accentArgb)
        drawTitle(canvas, stats)
        drawHashrate(canvas, stats)
        drawTiles(canvas, stats)
        drawFooter(canvas)

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safe = stats.minerName.replace(Regex("[^A-Za-z0-9_-]+"), "_").take(MAX_NAME_CHARS)
        val file = File(dir, "hashkit-$safe.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, PNG_QUALITY, it) }
        bitmap.recycle()
        return file
    }

    private fun drawHeader(canvas: Canvas, accent: Int) {
        // Pulse mark: same polyline as the in-app logo, boxed at MARK_SIZE.
        val x0 = MARGIN.toFloat()
        val y0 = MARGIN.toFloat()
        val s = MARK_SIZE.toFloat()
        val pts = MARK_POINTS
        val path = Path().apply {
            moveTo(x0 + pts[0].first * s, y0 + pts[0].second * s)
            pts.drop(1).forEach { (px, py) -> lineTo(x0 + px * s, y0 + py * s) }
        }
        canvas.drawPath(
            path,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = accent; style = Paint.Style.STROKE; strokeWidth = s * MARK_STROKE
                strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
            },
        )
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = HEADER_TEXT
        }
        val baseline = y0 + s * HEADER_BASELINE
        text.color = TEXT_PRIMARY
        canvas.drawText("Hi3 ", x0 + s + GAP, baseline, text)
        val w = text.measureText("Hi3 ")
        text.color = accent
        canvas.drawText("Hashkit", x0 + s + GAP + w, baseline, text)
    }

    private fun drawTitle(canvas: Canvas, stats: Stats) {
        val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = NAME_TEXT; color = TEXT_PRIMARY
        }
        canvas.drawText(ellipsize(stats.minerName, name, CONTENT_WIDTH), MARGIN.toFloat(), NAME_Y, name)
        stats.model?.takeIf { it.isNotBlank() }?.let {
            val model = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = MODEL_TEXT; color = TEXT_SECONDARY }
            canvas.drawText(ellipsize(it, model, CONTENT_WIDTH), MARGIN.toFloat(), MODEL_Y, model)
        }
    }

    private fun drawHashrate(canvas: Canvas, stats: Stats) {
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = LABEL_TEXT; color = TEXT_SECONDARY }
        canvas.drawText(context.getString(R.string.det_share_lbl_hashrate), MARGIN.toFloat(), HASH_LABEL_Y, label)
        val big = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = HASH_TEXT; color = stats.accentArgb
        }
        canvas.drawText(stats.hashrate, MARGIN.toFloat(), HASH_Y, big)
    }

    private fun drawTiles(canvas: Canvas, stats: Stats) {
        val tiles = listOf(
            Triple(context.getString(R.string.det_share_lbl_best), stats.bestShare, stats.percentOfBlock),
            Triple(context.getString(R.string.det_share_lbl_uptime), stats.uptime, null),
            Triple(context.getString(R.string.det_share_lbl_eff), stats.efficiency, null),
            Triple(
                context.getString(R.string.det_share_lbl_days),
                context.getString(R.string.det_share_days_value, stats.daysMining),
                null,
            ),
        )
        val tileW = (CONTENT_WIDTH - GAP) / 2f
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SURFACE }
        val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = LABEL_TEXT; color = TEXT_SECONDARY }
        val value = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = TILE_VALUE_TEXT; color = TEXT_PRIMARY
        }
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = MODEL_TEXT; color = stats.accentArgb }
        tiles.forEachIndexed { i, (title, main, extra) ->
            val col = i % 2
            val row = i / 2
            val left = MARGIN + col * (tileW + GAP)
            val top = TILES_Y + row * (TILE_H + GAP)
            canvas.drawRoundRect(RectF(left, top, left + tileW, top + TILE_H), TILE_RADIUS, TILE_RADIUS, fill)
            canvas.drawText(title, left + TILE_PAD, top + TILE_PAD + LABEL_TEXT, label)
            canvas.drawText(ellipsize(main, value, tileW - 2 * TILE_PAD), left + TILE_PAD, top + TILE_VALUE_Y, value)
            extra?.let { canvas.drawText(it, left + TILE_PAD, top + TILE_SUB_Y, sub) }
        }
    }

    private fun drawFooter(canvas: Canvas) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = LABEL_TEXT; color = TEXT_SECONDARY }
        canvas.drawText(context.getString(R.string.det_share_tagline), MARGIN.toFloat(), FOOTER_Y, paint)
        val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date())
        val right = Paint(paint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText(date, (WIDTH - MARGIN).toFloat(), FOOTER_Y, right)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    @Suppress("MagicNumber") // layout constants for a fixed 1080×1350 canvas
    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 1350
        const val MARGIN = 72
        const val GAP = 24f
        const val CONTENT_WIDTH = 936f // WIDTH - 2 * MARGIN
        const val MARK_SIZE = 72
        const val MARK_STROKE = 0.11f
        /** Pulse polyline normalized to the mark box — same shape as the in-app logo. */
        val MARK_POINTS = listOf(
            0.06f to 0.55f, 0.28f to 0.55f, 0.40f to 0.30f,
            0.52f to 0.80f, 0.64f to 0.42f, 0.72f to 0.55f, 0.94f to 0.55f,
        )
        const val HEADER_BASELINE = 0.72f
        const val HEADER_TEXT = 52f
        const val NAME_TEXT = 72f
        const val NAME_Y = 300f
        const val MODEL_TEXT = 36f
        const val MODEL_Y = 352f
        const val LABEL_TEXT = 30f
        const val HASH_LABEL_Y = 470f
        const val HASH_TEXT = 150f
        const val HASH_Y = 620f
        const val TILES_Y = 720f
        const val TILE_H = 210f
        const val TILE_RADIUS = 28f
        const val TILE_PAD = 32f
        const val TILE_VALUE_TEXT = 62f
        const val TILE_VALUE_Y = 140f
        const val TILE_SUB_Y = 188f
        const val FOOTER_Y = 1270f
        const val MAX_NAME_CHARS = 40
        const val PNG_QUALITY = 100
        val BACKGROUND = Color.rgb(0x0B, 0x0F, 0x14)
        val SURFACE = Color.rgb(0x12, 0x18, 0x20)
        val TEXT_PRIMARY = Color.rgb(0xE8, 0xEE, 0xF4)
        val TEXT_SECONDARY = Color.rgb(0x93, 0xA3, 0xB4)
    }
}
