package hi3.hashkit.integrations.print

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** On-device QR bitmap generation (bundled zxing), for showing a miner tag on screen. */
object QrCode {
    fun bitmap(payload: String, sizePx: Int): Bitmap? {
        if (payload.isBlank() || sizePx <= 0) return null
        return runCatching {
            val matrix = QRCodeWriter().encode(
                payload, BarcodeFormat.QR_CODE, sizePx, sizePx,
                mapOf(
                    EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN to 1,
                    EncodeHintType.CHARACTER_SET to "UTF-8",
                ),
            )
            Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565).apply {
                for (y in 0 until sizePx) for (x in 0 until sizePx) {
                    setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
        }.getOrNull()
    }
}
