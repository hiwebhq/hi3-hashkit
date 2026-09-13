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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import hi3.hashkit.R
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
                title = {
                    Text(
                        if (cameraEnabled) stringResource(R.string.ar_title_qr) else stringResource(R.string.ar_title_nfc),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
                            stringResource(R.string.ar_camera_needed) +
                                if (nfcAvailable) stringResource(R.string.ar_nfc_still_works) else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiBrand.textSecondary,
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text(stringResource(R.string.ar_grant_camera)) }
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
                            stringResource(R.string.ar_no_match, unmatched.orEmpty())
                        )
                        else -> OverlayHint(
                            stringResource(R.string.ar_point_qr) +
                                (if (nfcAvailable) stringResource(R.string.ar_or_tap_nfc) else "") +
                                stringResource(R.string.ar_guidance_suffix) +
                                (
                                    if (nfcAdapter != null && !nfcAvailable) {
                                        stringResource(R.string.ar_turn_on_nfc_suffix)
                                    } else {
                                        ""
                                    }
                                    )
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
            title = { Text(stringResource(R.string.ar_add_dialog_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ar_add_body,
                        tag.name ?: stringResource(R.string.ar_a_miner),
                        tag.ip.orEmpty(),
                    ) +
                        (tag.location?.let { stringResource(R.string.ar_add_location_line, it) } ?: "") +
                        stringResource(R.string.ar_add_confirm_line)
                )
            },
            confirmButton = { TextButton(onClick = viewModel::addFromTag) { Text(stringResource(R.string.common_add)) } },
            dismissButton = { TextButton(onClick = viewModel::dismissAdd) { Text(stringResource(R.string.common_cancel)) } },
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
                !hasNfcHardware -> stringResource(R.string.ar_no_nfc_hw)
                !nfcAvailable -> stringResource(R.string.ar_turn_on_nfc)
                else -> stringResource(R.string.ar_hold_tag)
            },
            style = MaterialTheme.typography.titleMedium,
            color = HiBrand.textPrimary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.ar_nfc_body),
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
            Stat(stringResource(R.string.ar_stat_hash), hashLabel(t?.hashrateGhs?.value))
            Stat(stringResource(R.string.ar_stat_power), t?.powerW?.value?.let { "%.0f W".format(it) } ?: "—")
            Stat(stringResource(R.string.ar_stat_temp), t?.chipTempC?.value?.let { "%.0f °C".format(it) } ?: "—")
        }
        Text(
            stringResource(R.string.ar_tap_to_open),
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
