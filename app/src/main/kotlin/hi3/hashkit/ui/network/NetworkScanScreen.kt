package hi3.hashkit.ui.network

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.ui.theme.HiBrand
import java.time.Duration
import java.time.Instant

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkScanScreen(
    onBack: () -> Unit,
    viewModel: NetworkScanViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val autoOnStartup by viewModel.autoScanOnStartup.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Network scan", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetwork() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh network info")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Scans your local subnet for miners on their known ports (HTTP 80, CGMiner " +
                    "4028). Only private LAN and Tailscale addresses are probed. Runs " +
                    "automatically at launch so devices appear before you open Add Miner.",
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )

            // --- Current network ---------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("This device", style = MaterialTheme.typography.labelMedium, color = HiBrand.textSecondary)
                    Text(
                        state.localIp ?: "Not on a Wi-Fi / LAN network",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (state.localIp != null) HiBrand.textPrimary else HiBrand.statusDegraded,
                    )
                }
            }

            // --- Target range ------------------------------------------------------
            OutlinedTextField(
                value = state.cidr,
                onValueChange = viewModel::onCidrChange,
                label = { Text("Scan range (CIDR)") },
                placeholder = { Text("192.168.1.0/24") },
                singleLine = true,
                enabled = !state.running,
                modifier = Modifier.fillMaxWidth(),
            )

            // --- Progress / status -------------------------------------------------
            if (state.running) {
                val frac = if (state.total > 0) state.scanned.toFloat() / state.total else 0f
                LinearProgressIndicator(
                    progress = { frac },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "Scanning ${state.scanned} / ${state.total} — ${state.found} found",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            } else {
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
                }
                state.lastFinishedAtMs?.let { ts ->
                    Text(
                        "Last scan ${ago(ts)}.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                }
            }

            // --- Controls ----------------------------------------------------------
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (state.running) {
                    Button(
                        onClick = { viewModel.stop() },
                        colors = ButtonDefaults.buttonColors(containerColor = HiBrand.statusOffline),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Stop")
                    }
                    OutlinedButton(onClick = { viewModel.restart() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Restart")
                    }
                } else {
                    Button(onClick = { viewModel.start() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Start scan")
                    }
                }
            }

            // --- Auto-scan toggle --------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Scan automatically at launch", color = HiBrand.textPrimary)
                    Text(
                        "Kick off a subnet scan when the app opens.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                }
                Switch(
                    checked = autoOnStartup,
                    onCheckedChange = { viewModel.setAutoScanOnStartup(it) },
                )
            }
        }
    }
}

private fun ago(epochMs: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(epochMs), Instant.now())
    val mins = d.toMinutes()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        else -> "${d.toHours()}h ago"
    }
}
