package hi3.hashkit.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSuggestion
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import hi3.hashkit.ui.theme.HiBrand
import java.net.Inet4Address
import java.net.NetworkInterface

/** The field kit's SSID — one kit, one name, always. See docs/HASHKIT-WIFI-AP.md. */
const val HASHKIT_AP_SSID = "Hi3-Hashkit"

private const val MAX_HINTED_SUBNETS = 3
private const val QR_SIZE_PX = 512

/**
 * Settings section for the Hashkit WiFi field kit: save the kit passphrase, add the
 * network to the phone, print/scan a join QR, and verify the connection — including
 * the big caveat: a 169.254.x.x address means the mining LAN gave no DHCP lease, and
 * the fix is a static IP on the phone, NEVER a DHCP server on the kit.
 */
@Suppress("LongMethod") // one declarative settings block; strings extracted with ui/flow later
@Composable
fun HashkitWifiSection(
    passphrase: String,
    farmSubnets: List<String>,
    onSavePassphrase: (String) -> Unit,
    onNetworkScan: () -> Unit,
) {
    val context = LocalContext.current
    var pass by remember(passphrase) { mutableStateOf(passphrase) }
    var showQr by remember { mutableStateOf(false) }
    var statusTick by remember { mutableIntStateOf(0) }
    var hasLocation by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocation = granted; statusTick++ }

    Text(
        "One pocket AP travels to the site, plugs into any switch on the miner LAN and " +
            "serves the \"$HASHKIT_AP_SSID\" network. Join it and every tool — Site Map, " +
            "scanning, logs — works as if you were cabled in. Kit config: docs/HASHKIT-WIFI-AP.md.",
        style = MaterialTheme.typography.labelSmall,
        color = HiBrand.textSecondary,
    )
    OutlinedTextField(
        value = pass,
        onValueChange = { pass = it },
        label = { Text("Kit Wi-Fi passphrase") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = { onSavePassphrase(pass) }) { Text("Save") }
        TextButton(
            onClick = { suggestNetwork(context, pass) },
            enabled = pass.isNotBlank(),
        ) { Text("Add to phone Wi-Fi") }
        TextButton(onClick = { showQr = true }, enabled = pass.isNotBlank()) { Text("Join QR") }
    }

    // ---- Connection status (recomposes on statusTick) ----
    val status = remember(statusTick, hasLocation) { readWifiStatus(context, hasLocation) }
    when {
        !hasLocation -> {
            Text(
                "Grant location to verify you're on the kit's network (Android requires it " +
                    "to read the Wi-Fi name).",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
            TextButton(onClick = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }) {
                Text("Grant location")
            }
        }
        status.onHashkit && status.linkLocal -> {
            Text(
                "⚠ Connected to $HASHKIT_AP_SSID but the phone got a self-assigned address " +
                    "(${status.ip}) — the mining LAN offered no DHCP lease, so scanning will fail.\n" +
                    "Fix: set a STATIC IP on this Wi-Fi network in Android settings" +
                    staticIpHint(farmSubnets) + "\n" +
                    "Never enable a DHCP server on the kit — a rogue DHCP server can disrupt " +
                    "the whole mining subnet.",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.statusDegraded,
            )
        }
        status.onHashkit -> {
            Text(
                "✓ On $HASHKIT_AP_SSID — phone address ${status.ip ?: "?"} (miner subnet).",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.statusOnline,
            )
            OutlinedButton(onClick = onNetworkScan) { Text("Scan this network") }
        }
        else -> {
            Text(
                "Not connected to $HASHKIT_AP_SSID" +
                    (status.ssid?.let { " (current: $it)" } ?: "") + ".",
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
    TextButton(onClick = { statusTick++ }) { Text("Refresh status") }

    if (showQr) {
        val qr = remember(pass) { wifiJoinQr(pass) }
        AlertDialog(
            onDismissRequest = { showQr = false },
            title = { Text("Join $HASHKIT_AP_SSID") },
            text = {
                Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                    qr?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = "Wi-Fi join QR",
                            modifier = Modifier.size(240.dp).padding(8.dp),
                        )
                    } ?: Text("Couldn't build the QR code.")
                    Text(
                        "Print this on the kit — scanning it joins the Hashkit Wi-Fi.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { showQr = false }) { Text("Done") } },
        )
    }
}

private data class WifiStatus(val ssid: String?, val ip: String?, val onHashkit: Boolean, val linkLocal: Boolean)

private fun readWifiStatus(context: Context, hasLocation: Boolean): WifiStatus {
    val ssid = if (hasLocation) {
        runCatching {
            @Suppress("DEPRECATION")
            (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager)
                .connectionInfo.ssid?.trim('"')?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
        }.getOrNull()
    } else null
    // Raw wlan IPv4 including link-local — deliberately not NetworkInspector, which
    // filters 169.254.* out; the whole point here is to catch it.
    val ip = runCatching {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback && it.name.startsWith("wlan") }
            .flatMap { it.inetAddresses.toList() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull()?.hostAddress
    }.getOrNull()
    return WifiStatus(
        ssid = ssid,
        ip = ip,
        onHashkit = ssid == HASHKIT_AP_SSID,
        linkLocal = ip?.startsWith("169.254.") == true,
    )
}

/** Suggest e.g. ".250 in 10.40.12.0/24" from the farms' stored subnets. */
private fun staticIpHint(farmSubnets: List<String>): String {
    val cidrs = farmSubnets.flatMap { it.split(',') }.map { it.trim() }.filter { it.isNotEmpty() }
    if (cidrs.isEmpty()) return " (use a free address in the site's miner subnet, e.g. x.x.x.250)."
    return " — e.g. " + cidrs.take(MAX_HINTED_SUBNETS).joinToString(", ") {
        it.substringBefore('/').substringBeforeLast('.') + ".250 for $it"
    } + "."
}

/** One-tap join via the Android network-suggestion API (user approves in the shade). */
private fun suggestNetwork(context: Context, passphrase: String) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
    runCatching {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val suggestion = WifiNetworkSuggestion.Builder()
            .setSsid(HASHKIT_AP_SSID)
            .setWpa2Passphrase(passphrase)
            .build()
        wifi.addNetworkSuggestions(listOf(suggestion))
    }
}

/** Standard Wi-Fi join payload; backslash-escapes the QR-reserved characters. */
private fun wifiJoinQr(passphrase: String): Bitmap? = runCatching {
    fun esc(s: String) = s.replace(Regex("([\\\\;,:\"])"), "\\\\$1")
    val payload = "WIFI:T:WPA;S:${esc(HASHKIT_AP_SSID)};P:${esc(passphrase)};;"
    val size = QR_SIZE_PX
    val matrix = QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
    Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).apply {
        for (x in 0 until size) for (y in 0 until size) {
            setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}.getOrNull()
