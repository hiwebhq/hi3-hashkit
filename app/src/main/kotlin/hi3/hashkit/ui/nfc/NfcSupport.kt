package hi3.hashkit.ui.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef

/** Custom MIME type Hi3 Hashkit tags carry so Android dispatches them straight to this app. */
const val HASHKIT_MIME = "application/vnd.hi3.hashkit"

/** The AAR package written as the last tag record, so a scan opens Hashkit (or offers install). */
const val HASHKIT_AAR_PACKAGE = "hi3.hashkit"

/**
 * Build the NDEF message written to a Hi3 Hashkit tag: the canonical `key=value` payload as a
 * custom-MIME record first (this is what makes Android auto-open Hashkit), a plain Text record so
 * generic NFC tools stay human-readable, and an Android Application Record last so a scan opens
 * Hashkit specifically (or the Play page if it isn't installed).
 */
fun hashkitNdefMessage(payload: String): NdefMessage = NdefMessage(
    arrayOf(
        NdefRecord.createMime(HASHKIT_MIME, payload.toByteArray(Charsets.UTF_8)),
        NdefRecord.createTextRecord("en", payload),
        NdefRecord.createApplicationRecord(HASHKIT_AAR_PACKAGE),
    )
)

/** Extract a miner-tag payload from an NFC intent (EXTRA_NDEF_MESSAGES, else the raw tag). */
fun payloadFromIntent(intent: Intent): String? {
    @Suppress("DEPRECATION")
    val raw = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES)
    val messages = raw?.filterIsInstance<NdefMessage>().orEmpty()
    payloadFromMessages(messages)?.let { return it }
    @Suppress("DEPRECATION")
    val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
    return tag?.let { readNdefText(it) }
}

/** First usable payload across the message records (skips the AAR package record). */
fun payloadFromMessages(messages: List<NdefMessage>): String? {
    for (msg in messages) for (record in msg.records) {
        decodeRecord(record)?.let { if (it.isNotBlank() && it != HASHKIT_AAR_PACKAGE) return it }
    }
    return null
}

/** Read the first usable text from an NFC tag's NDEF message (Text/MIME/URI records), or null. */
fun readNdefText(tag: Tag): String? {
    val ndef = Ndef.get(tag) ?: return null
    val message = ndef.cachedNdefMessage ?: runCatching {
        ndef.connect()
        try { ndef.ndefMessage } finally { runCatching { ndef.close() } }
    }.getOrNull()
    return message?.let { payloadFromMessages(listOf(it)) }
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
    // MIME media (our custom type) and everything else: raw UTF-8, or a URI.
    if (record.tnf == NdefRecord.TNF_MIME_MEDIA) {
        return runCatching { String(record.payload, Charsets.UTF_8) }.getOrNull()
    }
    return runCatching { record.toUri()?.toString() }.getOrNull()
        ?: runCatching { String(record.payload, Charsets.UTF_8) }.getOrNull()
}

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
