package hi3.hashkit.ui.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

/**
 * Enables NFC reader mode while the calling screen is on-screen and delivers each tag's text
 * payload to [onText] on the main thread.
 *
 * Reader mode is turned on **immediately** when the effect runs and again on every ON_RESUME —
 * not only via the lifecycle observer — because on a Navigation-Compose destination the back-stack
 * entry can already be RESUMED (or still settling) when the composable first runs, so relying on a
 * future ON_RESUME alone can miss enabling it. It's turned off on ON_PAUSE and on dispose.
 */
@Composable
fun NfcReaderEffect(adapter: NfcAdapter?, enabled: Boolean = true, onText: (String) -> Unit) {
    if (adapter == null || !enabled) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnText by rememberUpdatedState(onText)

    DisposableEffect(lifecycleOwner, activity, adapter) {
        if (activity == null) return@DisposableEffect onDispose { }
        val main = Handler(Looper.getMainLooper())
        val callback = NfcAdapter.ReaderCallback { tag ->
            readNdefText(tag)?.let { text -> main.post { currentOnText(text) } }
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V

        fun enable() = runCatching { adapter.enableReaderMode(activity, callback, flags, null) }
        fun disable() = runCatching { adapter.disableReaderMode(activity) }

        enable() // don't wait for a possibly-missed ON_RESUME
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> enable()
                Lifecycle.Event.ON_PAUSE -> disable()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            disable()
        }
    }
}

/** Read the first usable text from an NFC tag's NDEF message (Text/URI records), or null. */
fun readNdefText(tag: Tag): String? {
    val ndef = Ndef.get(tag) ?: return null
    val message = ndef.cachedNdefMessage ?: runCatching {
        ndef.connect()
        try { ndef.ndefMessage } finally { runCatching { ndef.close() } }
    }.getOrNull()
    val records = message?.records ?: return null
    for (record in records) {
        decodeRecord(record)?.let { if (it.isNotBlank()) return it }
    }
    return null
}

private fun decodeRecord(record: NdefRecord): String? {
    // Well-known Text record: [status byte][language code][UTF-8/16 text].
    if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
        val payload = record.payload
        if (payload.isEmpty()) return null
        val status = payload[0].toInt()
        val langLen = status and 0x3F
        val charset = if (status and 0x80 == 0) Charsets.UTF_8 else Charsets.UTF_16
        if (payload.size <= 1 + langLen) return null
        return runCatching { String(payload, 1 + langLen, payload.size - 1 - langLen, charset) }.getOrNull()
    }
    // URI records (well-known RTD_URI or absolute URI), else raw UTF-8.
    return runCatching { record.toUri()?.toString() }.getOrNull()
        ?: runCatching { String(record.payload, Charsets.UTF_8) }.getOrNull()
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
