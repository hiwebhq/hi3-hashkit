package hi3.hashkit.ui.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMinerScreen(
    onDone: () -> Unit,
    viewModel: AddMinerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.addm_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.addm_manual_entry),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.addm_manual_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.manualHost,
                        onValueChange = viewModel::onManualHostChange,
                        label = { Text(stringResource(R.string.addm_host_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = viewModel::addManual, enabled = !state.manualBusy) {
                        Text(
                            if (state.manualBusy) stringResource(R.string.addm_probing)
                            else stringResource(R.string.addm_probe_add)
                        )
                    }
                    state.manualMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.accentAlt)
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                shape = RoundedCornerShape(14.dp),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.addm_scan_local_network),
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.addm_scan_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = HiBrand.textSecondary,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = state.scanCidr,
                        onValueChange = viewModel::onScanCidrChange,
                        label = { Text(stringResource(R.string.addm_cidr_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.savedSubnets.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.savedSubnets.forEach { subnet ->
                                androidx.compose.material3.FilterChip(
                                    selected = state.scanCidr == subnet,
                                    onClick = { viewModel.onScanCidrChange(subnet) },
                                    label = { Text(subnet) },
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = viewModel::startScan, enabled = !state.scanning) {
                            Text(stringResource(R.string.addm_scan))
                        }
                        OutlinedButton(onClick = viewModel::startMdnsSearch, enabled = !state.scanning) {
                            Text("mDNS")
                        }
                        if (state.scanning) {
                            OutlinedButton(onClick = viewModel::cancelScan) { Text(stringResource(R.string.common_cancel)) }
                        }
                    }
                    state.scanProgress?.let { (done, total) ->
                        if (state.scanning && total > 0) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { done.toFloat() / total },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                stringResource(R.string.addm_hosts_progress, done, total),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                    }
                    state.scanMessage?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.accentAlt)
                    }
                    state.discovered.forEach { found ->
                        Spacer(Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("${found.label}  ·  ${found.host}", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                if (found.added) stringResource(R.string.addm_added)
                                else stringResource(R.string.addm_known),
                                style = MaterialTheme.typography.labelMedium,
                                color = if (found.added) HiBrand.accent else HiBrand.textSecondary,
                            )
                        }
                    }
                }
            }
        }
    }
}
