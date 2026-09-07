package hi3.hashkit.ui.privacy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.ui.theme.HiBrand

private data class PolicyItem(val title: String, val body: String)

private val POLICY = listOf(
    PolicyItem(
        "Local-first by design",
        "Hi3 Hashkit runs entirely on your device and your own network. There is no Hi3 " +
            "Hashkit account, no analytics, no advertising, and no telemetry sent to us or " +
            "any third party. Your miner data is never uploaded anywhere.",
    ),
    PolicyItem(
        "What stays on your device",
        "Miner addresses, identities, telemetry history, farms, alert settings and app " +
            "preferences are stored only in this app's private storage. Optional secrets (an " +
            "MMP API key) are encrypted with the Android Keystore. Nothing is backed up to a " +
            "cloud unless you explicitly export a backup file and choose where it goes.",
    ),
    PolicyItem(
        "Who the app talks to",
        "By default the app connects only to the miner IP addresses you add or discover, over " +
            "your LAN or your Tailscale VPN. It never contacts our servers on its own.",
    ),
    PolicyItem(
        "Optional, off-by-default connections",
        "Three integrations reach the internet only if you turn them on: a Bitcoin " +
            "network-difficulty fetch (mempool.space); Hi3 Pool stats, which send your payout " +
            "address to the pool URL you set; and the Hi3 MMP fleet view, authenticated by an " +
            "API key you provide. Each states exactly what it transmits, and turning it off " +
            "stops those requests immediately.",
    ),
    PolicyItem(
        "Camera (QR scanning)",
        "The camera is used only when you tap the QR icon to scan a payout address. Scanning " +
            "happens on-device; no image or video is stored or transmitted. You can decline " +
            "the camera permission and type the address instead.",
    ),
    PolicyItem(
        "Notifications",
        "Alerts are off by default. If you enable them, notifications are generated on-device " +
            "from your miners' readings; no notification content leaves the device.",
    ),
    PolicyItem(
        "Your control",
        "You can delete any miner, farm or all stored history from within the app, adjust " +
            "retention, and revoke permissions in Android settings at any time. Uninstalling " +
            "the app removes its local data.",
    ),
    PolicyItem(
        "Contact",
        "Questions about privacy or the app? Reach us at hi3.cc/contact or mmp.hi3.cc.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Privacy Policy", fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "${HiBrand.appName} keeps your data on your device. This summary explains " +
                        "what is stored and the few things that leave the device only when you " +
                        "opt in.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HiBrand.textSecondary,
                )
            }
            POLICY.forEach { policy ->
                item(key = policy.title) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(policy.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
                            Text(policy.body, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
