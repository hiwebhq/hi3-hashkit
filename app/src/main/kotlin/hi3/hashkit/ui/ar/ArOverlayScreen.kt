package hi3.hashkit.ui.ar

import android.Manifest
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
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
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Nfc
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
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
    androidx.compose.runtime.LaunchedEffect(Unit) {
        viewModel.scanToast.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
    val cameraEnabled by viewModel.cameraEnabled.collectAsStateWithLifecycle()
    val matched by viewModel.matched.collectAsStateWithLifecycle()
    // A scanned QR that resolves to a miner jumps straight to its detail (Live Telemetry).
    androidx.compose.runtime.LaunchedEffect(matched?.id) {
        matched?.let { onMinerClick(it.id) }
    }
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

    DisposableEffect(hasCamera, cameraEnabled) {
        // Only ask for the camera when QR scanning is actually used (NFC-only skips the camera).
        if (cameraEnabled && !hasCamera) permissionLauncher.launch(Manifest.permission.CAMERA)
        onDispose { }
    }

    // NFC reader mode: active only while this screen is resumed.
    // NFC is handled by the OS: scanning a Hi3 Hashkit tag opens/foregrounds the app and the
    // payload arrives through NfcRouter → the ViewModel. No in-app reader mode needed here.
    val nfcAdapter = remember { NfcAdapter.getDefaultAdapter(context) }
    val nfcAvailable = nfcAdapter?.isEnabled == true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (cameraEnabled) "Scan tag / QR" else "Scan NFC", fontWeight = FontWeight.Bold) },
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
            if (!cameraEnabled) {
                // NFC-only: no camera. Just an on-screen "hold a tag" indicator; the OS delivers
                // the tag and the app jumps straight to that miner's Live Telemetry.
                NfcOnlyIndicator(nfcAvailable = nfcAvailable, hasNfcHardware = nfcAdapter != null)
            } else {
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

                // Bottom overlay: matched miner's live card, or guidance (camera mode only).
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
private fun NfcOnlyIndicator(nfcAvailable: Boolean, hasNfcHardware: Boolean) {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "nfc")
    val scale by transition.animateFloat(
        initialValue = 1f, targetValue = 1.18f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(900),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Filled.Nfc,
            contentDescription = null,
            tint = if (nfcAvailable) HiBrand.accent else HiBrand.textSecondary,
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    if (nfcAvailable) { scaleX = scale; scaleY = scale }
                },
        )
        Spacer(Modifier.height(24.dp))
        Text(
            when {
                !hasNfcHardware -> "This device has no NFC."
                !nfcAvailable -> "Turn on NFC to scan tags."
                else -> "Hold a miner's NFC tag to the back of your phone."
            },
            style = MaterialTheme.typography.titleMedium,
            color = HiBrand.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "It opens that miner's live telemetry straight away — no camera needed.",
            style = MaterialTheme.typography.bodyMedium,
            color = HiBrand.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
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
