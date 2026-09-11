package hi3.hashkit.ui.hashrental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.integrations.hashpower.BraiinsHashpowerClient
import hi3.hashkit.ui.theme.HiBrand
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HashRentalState(
    val loading: Boolean = true,
    val market: BraiinsHashpowerClient.Market? = null,
    val asks: List<BraiinsHashpowerClient.Ask> = emptyList(),
    val error: String? = null,
    /** Last known BTC price (in [currency]); 0 when not fetched/entered. */
    val btcPrice: Double = 0.0,
    val currency: String = "USD",
    /** URL the "Rent" button opens — configurable (e.g. your Braiins referral link). */
    val rentUrl: String = "https://hashpower.braiins.com",
)

@HiltViewModel
class HashRentalViewModel @Inject constructor(
    private val client: BraiinsHashpowerClient,
    private val settingsRepository: hi3.hashkit.data.prefs.SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HashRentalState())
    val state: StateFlow<HashRentalState> = _state

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val s = settingsRepository.current()
            when (val r = client.fetch()) {
                is BraiinsHashpowerClient.Result.Ok ->
                    _state.value = HashRentalState(
                        loading = false, market = r.market, asks = r.asks.take(6),
                        btcPrice = s.btcPrice, currency = s.currencyCode, rentUrl = s.hashRentalUrl,
                    )
                is BraiinsHashpowerClient.Result.Error ->
                    _state.value = _state.value.copy(
                        loading = false, error = r.message, btcPrice = s.btcPrice,
                        currency = s.currencyCode, rentUrl = s.hashRentalUrl,
                    )
            }
        }
    }

    fun setRentUrl(value: String) {
        viewModelScope.launch {
            settingsRepository.setHashRentalUrl(value)
            _state.value = _state.value.copy(rentUrl = settingsRepository.current().hashRentalUrl)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HashRentalScreen(
    onBack: () -> Unit,
    viewModel: HashRentalViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showUsd by remember { mutableStateOf(false) }
    var editingUrl by remember { mutableStateOf(false) }
    // USD needs a known BTC price; fall back to sat if we don't have one.
    val usd = showUsd && state.btcPrice > 0
    fun price(sat: Long): String =
        if (usd) "%,.2f %s/PH·day".format(sat * state.btcPrice / 100_000_000_000.0, state.currency)
        else "${satPerPhDay(sat)} sat/PH·day"
    fun openRent() {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(state.rentUrl))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    if (editingUrl) {
        RentUrlDialog(
            current = state.rentUrl,
            onDismiss = { editingUrl = false },
            onSave = { viewModel.setRentUrl(it); editingUrl = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hash rental", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { editingUrl = true }) {
                        Icon(Icons.Filled.Link, contentDescription = "Edit rent link")
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
                    "Rent Bitcoin hashrate on the Braiins Hashpower spot market. This shows the live " +
                        "public market — renting happens on Braiins, not in this app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Show in", style = MaterialTheme.typography.bodyMedium, color = HiBrand.textSecondary)
                    FilterChip(selected = !showUsd, onClick = { showUsd = false }, label = { Text("sat") })
                    FilterChip(
                        selected = showUsd,
                        onClick = { showUsd = true },
                        enabled = state.btcPrice > 0,
                        label = { Text(state.currency) },
                    )
                }
            }
            if (showUsd && state.btcPrice <= 0) {
                item {
                    Text(
                        "Set or fetch the BTC price in Settings to see ${state.currency} values.",
                        style = MaterialTheme.typography.labelSmall, color = HiBrand.statusDegraded,
                    )
                }
            }
            if (state.loading) {
                item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            }
            state.error?.let { err ->
                item { Text(err, style = MaterialTheme.typography.bodyMedium, color = HiBrand.statusDegraded) }
            }

            state.market?.let { m ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("SPOT MARKET", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                            Text(
                                m.bestAskSat?.let { price(it) } ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = HiBrand.accent,
                            )
                            Text("Best ask (cheapest hashrate to rent)", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                            Spacer12()
                            m.lastAvgPriceSat?.let { Line("Last avg price", price(it)) }
                            m.bestBidSat?.let { Line("Best bid", price(it)) }
                            m.availablePh?.let { Line("Hashrate available", "%,.0f PH/s".format(it)) }
                            m.matchedPh?.let { Line("Hashrate matched", "%,.0f PH/s".format(it)) }
                        }
                    }
                }
            }

            if (state.asks.isNotEmpty()) {
                item { Text("CHEAPEST OFFERS", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
                for (ask in state.asks) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(price(ask.priceSat), style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
                            Text("%,.0f PH/s avail".format(ask.availablePh), style = MaterialTheme.typography.bodyMedium, color = HiBrand.textSecondary)
                        }
                    }
                }
            }

            item {
                Button(onClick = { openRent() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text("Rent on Braiins Hashpower ↗")
                }
            }
            item {
                Text(
                    "Prices are live from Braiins Hashpower's public market API. Renting is completed " +
                        "on hashpower.braiins.com with your own account.",
                    style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HiBrand.textSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
    }
}

@Composable
private fun RentUrlDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rent link") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The URL the \"Rent\" button opens. Paste your Braiins Hashpower referral link " +
                        "here if you have one — leave blank to reset to the default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("URL") },
                    placeholder = { Text("https://hashpower.braiins.com") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))

/**
 * The API quotes price_sat as sats per EH/s per day (≈49,000,000); Braiins' UI shows the
 * familiar sats per PH/s per day, which is price_sat / 1000.
 */
private fun satPerPhDay(sat: Long): String = "%,.0f".format(sat / 1000.0)
