package hi3.hashkit.ui.ar

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.tech.Ndef
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.ui.theme.HiBrand

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArOverlayScreen(
    onBack: () -> Unit,
    onMinerClick: (Long) -> Unit,
    viewModel: ArOverlayViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val matched by viewModel.matched.collectAsStateWithLifecycle()
    val unmatched by viewModel.unmatched.collectAsStateWithLifecycle()
    val pendingAdd by viewModel.pendingAdd.collectAsStateWithLifecycle()
    val tagLocation by viewModel.tagLocation.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    var hasCamera by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasCamera = granted }

    DisposableEffect(hasCamera) {
        if (!hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    // NFC reader mode: active only while this screen is resumed.
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcAvailable = nfcAdapter?.isEnabled == true
    NfcReader(nfcAdapter, onText = viewModel::onScanned)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AR rack overlay", fontWeight = FontWeight.Bold) },
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
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasCamera) {
                CameraScanner(onScanned = viewModel::onScanned, modifier = Modifier.fillMaxSize())
            } else {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "Camera access is needed to read the miner QR stickers." +
                            if (nfcAvailable) " NFC tags still work — just tap one." else "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiBrand.textSecondary,
                    )
                    Button(
                        onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Grant camera") }
                }
            }

            // Bottom overlay: matched miner's live card, or guidance.
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                message?.let { OverlayHint(it) }
                when {
                    matched != null -> MatchedCard(
                        matched!!, tagLocation,
                        onOpen = { onMinerClick(matched!!.id) },
                        onClear = viewModel::clear,
                    )
                    unmatched != null -> OverlayHint(
                        "No miner matches \"$unmatched\". Program the tag/QR with the miner's name, " +
                            "IP, MAC or id."
                    )
                    else -> OverlayHint(
                        buildString {
                            append("Point at a miner's QR sticker")
                            if (nfcAvailable) append(" or tap its NFC tag")
                            append(" — name, IP, MAC or id. Live stats appear here.")
                            if (nfcAdapter != null && !nfcAvailable) append("  (Turn on NFC to tap tags.)")
                        }
                    )
                }
            }
        }
    }

    // Offer to add an unknown miner from its tag IP.
    pendingAdd?.let { tag ->
        AlertDialog(
            onDismissRequest = viewModel::dismissAdd,
            title = { Text("Add this miner?") },
            text = {
                Text(
                    "The tag points to ${tag.name ?: "a miner"} at ${tag.ip}, which isn't in the app " +
                        "yet." + (tag.location?.let { "\nLocation: $it" } ?: "") +
                        "\n\nAdd it now by probing that address?"
                )
            },
            confirmButton = { TextButton(onClick = viewModel::addFromTag) { Text("Add") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAdd) { Text("Cancel") } },
        )
    }
}

@Composable
private fun NfcReader(adapter: NfcAdapter?, onText: (String) -> Unit) {
    if (adapter == null) return
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        if (activity == null) return@DisposableEffect onDispose { }
        val callback = NfcAdapter.ReaderCallback { tag ->
            readNdefText(tag)?.let(onText)
        }
        val flags = NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME ->
                    runCatching { adapter.enableReaderMode(activity, callback, flags, null) }
                Lifecycle.Event.ON_PAUSE ->
                    runCatching { adapter.disableReaderMode(activity) }
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

@Composable
private fun CameraScanner(onScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val barcodeView = remember { mutableStateOf<DecoratedBarcodeView?>(null) }
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            DecoratedBarcodeView(ctx).apply {
                setStatusText("")
                decodeContinuous(object : BarcodeCallback {
                    override fun barcodeResult(result: BarcodeResult) {
                        result.text?.let(onScanned)
                    }
                    override fun possibleResultPoints(resultPoints: MutableList<com.google.zxing.ResultPoint>?) {}
                })
                barcodeView.value = this
                resume()
            }
        },
    )
    DisposableEffect(Unit) {
        onDispose { barcodeView.value?.pause() }
    }
}

@Composable
private fun MatchedCard(miner: Miner, tagLocation: String?, onOpen: () -> Unit, onClear: () -> Unit) {
    val t = miner.lastTelemetry
    Column(
        Modifier
            .fillMaxWidth()
            .background(HiBrand.surface.copy(alpha = 0.94f), RoundedCornerShape(14.dp))
            .clickable(onClick = onOpen)
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(miner.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = HiBrand.textPrimary)
            Text(miner.status.name, style = MaterialTheme.typography.labelMedium, color = statusColor(miner))
        }
        val where = tagLocation ?: miner.location
        Text(
            miner.host + (where?.let { "  ·  $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
        )
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("HASH", hashLabel(t?.hashrateGhs?.value))
            Stat("POWER", t?.powerW?.value?.let { "%.0f W".format(it) } ?: "—")
            Stat("TEMP", t?.chipTempC?.value?.let { "%.0f °C".format(it) } ?: "—")
        }
        Text(
            "Tap to open · scan another marker to switch",
            style = MaterialTheme.typography.labelSmall,
            color = HiBrand.textSecondary,
            modifier = Modifier.padding(top = 8.dp).clickable(onClick = onClear),
        )
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = HiBrand.textPrimary)
    }
}

@Composable
private fun OverlayHint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(12.dp))
            .padding(14.dp),
    )
}

private fun statusColor(miner: Miner) = when (miner.status) {
    hi3.hashkit.domain.model.MinerStatus.ONLINE -> HiBrand.statusOnline
    hi3.hashkit.domain.model.MinerStatus.DEGRADED -> HiBrand.statusDegraded
    hi3.hashkit.domain.model.MinerStatus.OFFLINE -> HiBrand.statusOffline
    else -> HiBrand.statusUnknown
}

private fun hashLabel(ghs: Double?): String {
    if (ghs == null) return "—"
    return if (ghs >= 1000) "%.2f TH/s".format(ghs / 1000) else "%.0f GH/s".format(ghs)
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/** Read the first usable text from an NFC tag's NDEF message (Text/URI records), or null. */
private fun readNdefText(tag: android.nfc.Tag): String? {
    val ndef = Ndef.get(tag) ?: return null
    val message: NdefMessage? = ndef.cachedNdefMessage ?: runCatching {
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
    // URI records (well-known RTD_URI or absolute URI).
    return runCatching { record.toUri()?.toString() }.getOrNull()
        ?: runCatching { String(record.payload, Charsets.UTF_8) }.getOrNull()
}
