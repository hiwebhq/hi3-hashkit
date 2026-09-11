package hi3.hashkit.ui.nfcprog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.integrations.print.QrCode
import hi3.hashkit.ui.theme.HiBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NfcProgramScreen(
    onBack: () -> Unit,
    viewModel: NfcProgramViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcReady = nfcAdapter?.isEnabled == true
    val current = state.current

    // Reader mode: when a tag is presented, write the CURRENT target's payload to it.
    NfcWriteMode(nfcAdapter, enabled = current != null) { tag ->
        val target = viewModel.state.value.current ?: return@NfcWriteMode
        when (val r = writeTag(tag, target.payload)) {
            is WriteOutcome.Ok -> viewModel.markWritten(target.id)
            is WriteOutcome.Err -> viewModel.setMessage("Write failed: ${r.message}")
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Program NFC tags", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.loading) {
                Text("Loading…", Modifier.padding(16.dp), color = HiBrand.textSecondary)
                return@Column
            }
            if (state.targets.isEmpty()) {
                Text(
                    state.message ?: "No miners to program.",
                    Modifier.padding(16.dp), color = HiBrand.textSecondary,
                )
                return@Column
            }

            // Progress + NFC status.
            Text(
                "${state.writtenCount} of ${state.total} written" +
                    if (!nfcReady) "  ·  ${if (nfcAdapter == null) "no NFC on this device" else "turn on NFC to write"}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = if (nfcReady) HiBrand.textSecondary else HiBrand.statusDegraded,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Current target card.
            current?.let { t ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (t.id in state.written) HiBrand.statusOnline.copy(alpha = 0.14f) else HiBrand.surface,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "Programming ${state.index + 1} of ${state.total}",
                            style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(t.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = HiBrand.textPrimary)
                            if (t.id in state.written) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "written", tint = HiBrand.statusOnline)
                            }
                        }
                        Spacer8()
                        PayloadLine("MAC", t.mac)
                        PayloadLine("IP", t.ip)
                        PayloadLine("Location", t.location)
                        Spacer8()
                        Text(
                            if (nfcReady) "Hold a blank NFC tag to the back of the phone to write it."
                            else "Enable NFC to write, or use Show QR to print a sticker instead.",
                            style = MaterialTheme.typography.bodyMedium, color = HiBrand.textSecondary,
                        )
                        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::prev, enabled = state.index > 0) { Text("Prev") }
                            OutlinedButton(onClick = viewModel::skip, enabled = state.index < state.total - 1) { Text("Skip") }
                            OutlinedButton(onClick = viewModel::next, enabled = state.index < state.total - 1) { Text("Next") }
                            OutlinedButton(onClick = viewModel::toggleQr) { Text("Show QR") }
                        }
                    }
                }
            }

            state.message?.let { msg ->
                Text(msg, style = MaterialTheme.typography.labelMedium, color = HiBrand.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            }

            // Full list — tap to jump; check = written.
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                itemsIndexed(state.targets, key = { _, t -> t.id }) { i, t ->
                    Row(
                        Modifier.fillMaxWidth().clickable { viewModel.goTo(i) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            (if (i == state.index) "▶ " else "") + t.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (i == state.index) HiBrand.accent else HiBrand.textPrimary,
                        )
                        if (t.id in state.written) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = "written", tint = HiBrand.statusOnline, modifier = Modifier.size(18.dp))
                        } else {
                            Text(t.ip ?: "", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                        }
                    }
                }
            }
        }
    }

    // QR dialog for the current miner.
    if (state.showQr && current != null) {
        QrDialog(payload = current.payload, title = current.name, onDismiss = viewModel::toggleQr)
    }
}

@Composable
private fun Spacer8() = androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))

@Composable
private fun PayloadLine(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$label:", style = MaterialTheme.typography.labelMedium, color = HiBrand.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
    }
}

@Composable
private fun QrDialog(payload: String, title: String, onDismiss: () -> Unit) {
    val bmp = remember(payload) { QrCode.bitmap(payload, 640) }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (bmp != null) {
                    Image(
                        bitmap = bmp.asImageBitmap(),
                        contentDescription = "QR for $title",
                        modifier = Modifier.size(240.dp).background(androidx.compose.ui.graphics.Color.White).padding(8.dp),
                    )
                }
                Text(
                    "Screenshot to print as a sticker — scans in the AR rack overlay.",
                    style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        },
    )
}

@Composable
private fun NfcWriteMode(adapter: NfcAdapter?, enabled: Boolean, onTag: (Tag) -> Unit) {
    if (adapter == null || !enabled) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val callback = NfcAdapter.ReaderCallback { tag -> onTag(tag) }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runCatching { adapter.enableReaderMode(activity, callback, flags, null) }
                Lifecycle.Event.ON_PAUSE -> runCatching { adapter.disableReaderMode(activity) }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { adapter.disableReaderMode(activity) }
        }
    }
}

private sealed interface WriteOutcome {
    data object Ok : WriteOutcome
    data class Err(val message: String) : WriteOutcome
}

/**
 * Write [payload] to an NFC tag as the Hi3 Hashkit record set (custom-MIME + Text + AAR) so the
 * tag auto-opens the app when scanned. Handles blank & pre-formatted tags.
 */
private fun writeTag(tag: Tag, payload: String): WriteOutcome {
    val message = hi3.hashkit.ui.nfc.hashkitNdefMessage(payload)
    val size = message.toByteArray().size
    Ndef.get(tag)?.let { ndef ->
        return runCatching {
            ndef.connect()
            try {
                if (!ndef.isWritable) return@runCatching WriteOutcome.Err("tag is read-only")
                if (ndef.maxSize < size) return@runCatching WriteOutcome.Err("tag too small ($size B > ${ndef.maxSize} B)")
                ndef.writeNdefMessage(message)
                WriteOutcome.Ok
            } finally { runCatching { ndef.close() } }
        }.getOrElse { WriteOutcome.Err(it.message ?: "NDEF write error") }
    }
    NdefFormatable.get(tag)?.let { formatable ->
        return runCatching {
            formatable.connect()
            try { formatable.format(message); WriteOutcome.Ok }
            finally { runCatching { formatable.close() } }
        }.getOrElse { WriteOutcome.Err(it.message ?: "format error") }
    }
    return WriteOutcome.Err("tag doesn't support NDEF")
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
