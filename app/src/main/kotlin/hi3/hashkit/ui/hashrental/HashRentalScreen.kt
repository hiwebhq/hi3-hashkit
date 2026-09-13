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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import hi3.hashkit.R
import hi3.hashkit.data.prefs.DEFAULT_HASH_RENTAL_URL
import hi3.hashkit.integrations.hashpower.BraiinsHashpowerClient
import hi3.hashkit.ui.theme.HiBrand
import hi3.hashkit.ui.util.openUrl
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    val rentUrl: String = DEFAULT_HASH_RENTAL_URL,
    /** When the shown market data was fetched; used to stamp stale data after a failed refresh. */
    val asOfEpochMs: Long = 0,
)

@HiltViewModel
class HashRentalViewModel @Inject constructor(
    private val client: BraiinsHashpowerClient,
    private val settingsRepository: hi3.hashkit.data.prefs.SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HashRentalState())
    val state: StateFlow<HashRentalState> = _state

    init {
        refresh()
        // All settings this screen shows arrive reactively; refresh() never touches them.
        viewModelScope.launch {
            settingsRepository.settings
                .map { Triple(it.hashRentalUrl, it.btcPrice, it.currencyCode) }
                .distinctUntilChanged()
                .collect { (url, price, code) ->
                    _state.value = _state.value.copy(rentUrl = url, btcPrice = price, currency = code)
                }
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = client.fetch()) {
                is BraiinsHashpowerClient.Result.Ok ->
                    _state.value = _state.value.copy(
                        loading = false, market = r.market, asks = r.asks.take(6),
                        error = null, asOfEpochMs = System.currentTimeMillis(),
                    )
                is BraiinsHashpowerClient.Result.Error ->
                    // Keep showing the last good snapshot (this screen's, or the session cache),
                    // stale-stamped; the error renders as a banner above it.
                    _state.value = _state.value.copy(
                        loading = false, error = r.message,
                        market = _state.value.market ?: r.cached?.market,
                        asks = _state.value.asks.ifEmpty { r.cached?.asks.orEmpty().take(6) },
                        asOfEpochMs = if (_state.value.market != null) _state.value.asOfEpochMs else r.cachedAtEpochMs,
                    )
            }
        }
    }

    fun setRentUrl(value: String) {
        viewModelScope.launch { settingsRepository.setHashRentalUrl(value) }
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
    fun openRent() = context.openUrl(state.rentUrl)

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
                title = { Text(stringResource(R.string.rental_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    IconButton(onClick = { editingUrl = true }) {
                        Icon(Icons.Filled.Link, contentDescription = stringResource(R.string.rental_edit_link))
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
                    stringResource(R.string.rental_intro),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }

            item {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.rental_show_in), style = MaterialTheme.typography.bodyMedium, color = HiBrand.textSecondary)
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
                        stringResource(R.string.rental_set_btc_price, state.currency),
                        style = MaterialTheme.typography.labelSmall, color = HiBrand.statusDegraded,
                    )
                }
            }
            if (state.loading) {
                item { CircularProgressIndicator(Modifier.padding(8.dp)) }
            }
            state.error?.let { err ->
                item {
                    var showDetails by remember { mutableStateOf(false) }
                    Column {
                        Text(
                            stringResource(R.string.rental_unavailable),
                            style = MaterialTheme.typography.bodyMedium,
                            color = HiBrand.statusDegraded,
                        )
                        if (state.market != null && state.asOfEpochMs > 0) {
                            Text(
                                stringResource(
                                    R.string.rental_showing_from,
                                    java.text.DateFormat.getTimeInstance(
                                        java.text.DateFormat.SHORT,
                                    ).format(java.util.Date(state.asOfEpochMs)),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = HiBrand.textSecondary,
                            )
                        }
                        Row {
                            TextButton(onClick = { viewModel.refresh() }) { Text(stringResource(R.string.common_retry)) }
                            TextButton(onClick = { showDetails = !showDetails }) {
                                Text(if (showDetails) stringResource(R.string.rental_hide_details) else stringResource(R.string.rental_details))
                            }
                        }
                        if (showDetails) {
                            Text(err, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                        }
                    }
                }
            }

            state.market?.let { m ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(stringResource(R.string.rental_spot_market), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                            Text(
                                m.bestAskSat?.let { price(it) } ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = HiBrand.accent,
                            )
                            Text(stringResource(R.string.rental_best_ask_caption), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                            Spacer12()
                            m.lastAvgPriceSat?.let { Line(stringResource(R.string.rental_last_avg_price), price(it)) }
                            m.bestBidSat?.let { Line(stringResource(R.string.rental_best_bid), price(it)) }
                            m.availablePh?.let { Line(stringResource(R.string.rental_hashrate_available), "%,.0f PH/s".format(it)) }
                            m.matchedPh?.let { Line(stringResource(R.string.rental_hashrate_matched), "%,.0f PH/s".format(it)) }
                        }
                    }
                }
            }

            if (state.asks.isNotEmpty()) {
                item { Text(stringResource(R.string.rental_cheapest_offers), style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary) }
                for (ask in state.asks) {
                    item {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(price(ask.priceSat), style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
                            Text(
                                stringResource(R.string.rental_ph_avail, "%,.0f".format(ask.availablePh)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiBrand.textSecondary,
                            )
                        }
                    }
                }
            }

            item {
                Button(onClick = { openRent() }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(stringResource(R.string.rental_rent_button))
                }
            }
            item {
                Text(
                    stringResource(R.string.rental_footer),
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
        title = { Text(stringResource(R.string.rental_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.rental_dialog_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text(stringResource(R.string.rental_url_label)) },
                    placeholder = { Text(DEFAULT_HASH_RENTAL_URL) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.common_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))

/**
 * The API quotes price_sat as sats per EH/s per day (≈49,000,000); Braiins' UI shows the
 * familiar sats per PH/s per day, which is price_sat / 1000.
 */
private fun satPerPhDay(sat: Long): String = "%,.0f".format(sat / 1000.0)
