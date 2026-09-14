package hi3.hashkit.ui.provision

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.R
import hi3.hashkit.data.provision.BitaxeProvisioner
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.openUrl

/**
 * "Set up a new Bitaxe": join its `Bitaxe_XXXX` hotspot, enter home Wi-Fi + pool, and the
 * unit reboots onto the home network — no captive portal, no typing on a 128×32 screen.
 */
@Suppress("LongMethod") // declarative wizard: one branch per step
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProvisionScreen(
    onBack: () -> Unit,
    onFindOnNetwork: () -> Unit,
    viewModel: ProvisionViewModel = hiltViewModel(),
) {
    val step by viewModel.step.collectAsStateWithLifecycle()
    val pools by viewModel.savedPools.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prov_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back),
                        )
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
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    when (val s = step) {
                        ProvisionStep.Intro -> IntroStep(
                            supported = viewModel.supported,
                            onConnect = viewModel::connect,
                            onOpenPortal = { context.openUrl(BitaxeProvisioner.BASE_URL) },
                        )
                        ProvisionStep.Connecting -> {
                            Text(stringResource(R.string.prov_connecting), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.common_cancel)) }
                        }
                        is ProvisionStep.Connected -> SetupForm(
                            info = s.info,
                            pools = pools,
                            onApply = viewModel::apply,
                            onCancel = viewModel::reset,
                        )
                        ProvisionStep.Applying -> {
                            Text(stringResource(R.string.prov_applying), style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        is ProvisionStep.Done -> {
                            Text(
                                stringResource(R.string.prov_done, s.homeSsid),
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiBrand.statusOnline,
                            )
                            Text(
                                stringResource(R.string.prov_done_hint),
                                style = MaterialTheme.typography.bodySmall,
                                color = HiBrand.textSecondary,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onFindOnNetwork) { Text(stringResource(R.string.prov_find_it)) }
                                TextButton(onClick = viewModel::reset) { Text(stringResource(R.string.prov_another)) }
                            }
                        }
                        is ProvisionStep.Failed -> {
                            Text(
                                stringResource(R.string.prov_failed, s.message),
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiBrand.statusDegraded,
                            )
                            Button(onClick = viewModel::reset) { Text(stringResource(R.string.prov_start_over)) }
                        }
                    }
                }
            }
            Text(
                stringResource(R.string.prov_privacy_note),
                style = MaterialTheme.typography.labelSmall,
                color = HiBrand.textSecondary,
            )
        }
    }
}

@Composable
private fun IntroStep(supported: Boolean, onConnect: () -> Unit, onOpenPortal: () -> Unit) {
    SectionLabel(stringResource(R.string.prov_intro_header))
    Text(stringResource(R.string.prov_intro_body), style = MaterialTheme.typography.bodyMedium)
    if (supported) {
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.prov_connect_button))
        }
    } else {
        Text(
            stringResource(R.string.prov_unsupported),
            style = MaterialTheme.typography.bodySmall,
            color = HiBrand.statusDegraded,
        )
        OutlinedButton(onClick = onOpenPortal) { Text(stringResource(R.string.prov_open_portal)) }
    }
}

@Suppress("LongMethod", "CyclomaticComplexMethod") // a declarative form: one field per setting
@Composable
private fun SetupForm(
    info: hi3.hashkit.adapters.espminer.ParsedSystemInfo?,
    pools: List<hi3.hashkit.data.db.SavedPoolEntity>,
    onApply: (String, String, String, BitaxeProvisioner.PoolSetup?) -> Unit,
    onCancel: () -> Unit,
) {
    var ssid by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var hostname by rememberSaveable { mutableStateOf(info?.identity?.hostname ?: "") }
    var poolUrl by rememberSaveable { mutableStateOf(info?.telemetry?.poolUrl ?: "") }
    var poolPort by rememberSaveable { mutableStateOf(info?.telemetry?.poolPort?.toString() ?: "3333") }
    var worker by rememberSaveable { mutableStateOf(info?.telemetry?.workerName ?: "") }
    var setPool by rememberSaveable { mutableStateOf(false) }
    val portInt = poolPort.toIntOrNull()
    val poolValid = !setPool ||
        (poolUrl.isNotBlank() && worker.isNotBlank() && portInt != null && portInt in 1..MAX_PORT)
    val valid = ssid.isNotBlank() && ssid.length <= MAX_SSID_CHARS && poolValid

    Text(
        info?.let {
            stringResource(
                R.string.prov_found_unit,
                listOfNotNull(it.identity.model, it.identity.firmwareVersion).joinToString(" · "),
            )
        } ?: stringResource(R.string.prov_found_unknown),
        style = MaterialTheme.typography.bodyMedium,
        color = HiBrand.statusOnline,
    )
    SectionLabel(stringResource(R.string.prov_wifi_header))
    OutlinedTextField(
        value = ssid, onValueChange = { ssid = it },
        label = { Text(stringResource(R.string.prov_wifi_name)) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password, onValueChange = { password = it },
        label = { Text(stringResource(R.string.prov_wifi_password)) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = hostname, onValueChange = { hostname = it },
        label = { Text(stringResource(R.string.prov_hostname)) }, singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        SectionLabel(stringResource(R.string.prov_pool_header), Modifier.weight(1f))
        androidx.compose.material3.Switch(checked = setPool, onCheckedChange = { setPool = it })
    }
    if (setPool) {
        if (pools.isNotEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            ) {
                pools.forEach { p ->
                    FilterChip(
                        selected = poolUrl == p.url && poolPort == p.port.toString(),
                        onClick = {
                            poolUrl = p.url
                            poolPort = p.port.toString()
                            if (worker.isBlank()) worker = p.worker
                        },
                        label = { Text(p.label) },
                    )
                }
            }
        }
        FormField(poolUrl, { poolUrl = it }, stringResource(R.string.det_stratum_url))
        FormField(poolPort, { poolPort = it }, stringResource(R.string.det_port))
        FormField(worker, { worker = it }, stringResource(R.string.det_worker))
    }
    Spacer(Modifier.height(4.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            enabled = valid,
            onClick = {
                onApply(
                    ssid, password, hostname,
                    if (setPool) BitaxeProvisioner.PoolSetup(poolUrl.trim(), portInt ?: 0, worker.trim()) else null,
                )
            },
        ) { Text(stringResource(R.string.prov_apply)) }
        TextButton(onClick = onCancel) { Text(stringResource(R.string.common_cancel)) }
    }
}

private const val MAX_SSID_CHARS = 32
private const val MAX_PORT = 65_535

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
}

@Composable
private fun FormField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) },
        singleLine = true, modifier = Modifier.fillMaxWidth(),
    )
}
