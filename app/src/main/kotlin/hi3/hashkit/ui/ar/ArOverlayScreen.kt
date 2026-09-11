package hi3.hashkit.ui.ar

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
    val matched by viewModel.matched.collectAsStateWithLifecycle()
    val unmatched by viewModel.unmatched.collectAsStateWithLifecycle()

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
                        "Camera access is needed to read the miner stickers.",
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
                when {
                    matched != null -> MatchedCard(matched!!, onOpen = { onMinerClick(matched!!.id) }, onClear = viewModel::clear)
                    unmatched != null -> OverlayHint("No miner matches \"$unmatched\". Put a QR sticker with the miner's name, IP or ID on each unit.")
                    hasCamera -> OverlayHint("Point at a miner's QR sticker (its name, IP, MAC or ID). Its live stats will appear here.")
                }
            }
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
private fun MatchedCard(miner: Miner, onOpen: () -> Unit, onClear: () -> Unit) {
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
        Text(miner.host, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            Stat("HASH", hashLabel(t?.hashrateGhs?.value))
            Stat("POWER", t?.powerW?.value?.let { "%.0f W".format(it) } ?: "—")
            Stat("TEMP", t?.chipTempC?.value?.let { "%.0f °C".format(it) } ?: "—")
        }
        Text(
            "Tap to open · scan another sticker to switch",
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
