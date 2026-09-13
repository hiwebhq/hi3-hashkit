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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
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
                title = { Text(stringResource(R.string.net_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refreshNetwork() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.net_refresh_network_info))
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
                stringResource(R.string.net_intro),
                style = MaterialTheme.typography.bodySmall,
                color = HiBrand.textSecondary,
            )

            // --- Current network ---------------------------------------------------
            Card(
                colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        stringResource(R.string.net_this_device),
                        style = MaterialTheme.typography.labelMedium,
                        color = HiBrand.textSecondary,
                    )
                    Text(
                        state.localIp ?: stringResource(R.string.net_not_on_network),
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
                label = { Text(stringResource(R.string.net_scan_range_label)) },
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
                    stringResource(R.string.net_scanning_progress, state.scanned, state.total, state.found),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            } else {
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
                }
                state.lastFinishedAtMs?.let { ts ->
                    Text(
                        stringResource(R.string.net_last_scan, ago(ts)),
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
                        Text(stringResource(R.string.common_stop))
                    }
                    OutlinedButton(onClick = { viewModel.restart() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.net_restart))
                    }
                } else {
                    Button(onClick = { viewModel.start() }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.net_start_scan))
                    }
                    val mdnsRunning by viewModel.mdnsRunning.collectAsStateWithLifecycle()
                    OutlinedButton(
                        onClick = { viewModel.discoverMdns() },
                        enabled = !mdnsRunning,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            if (mdnsRunning) stringResource(R.string.net_discovering)
                            else stringResource(R.string.net_mdns_discover)
                        )
                    }
                }
            }

            // --- Auto-scan toggle --------------------------------------------------
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.net_auto_scan_title), color = HiBrand.textPrimary)
                    Text(
                        stringResource(R.string.net_auto_scan_subtitle),
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

@Composable
private fun ago(epochMs: Long): String {
    val d = Duration.between(Instant.ofEpochMilli(epochMs), Instant.now())
    val mins = d.toMinutes()
    return when {
        mins < 1 -> stringResource(R.string.net_just_now)
        mins < 60 -> stringResource(R.string.net_minutes_ago, mins)
        else -> stringResource(R.string.net_hours_ago, d.toHours())
    }
}
