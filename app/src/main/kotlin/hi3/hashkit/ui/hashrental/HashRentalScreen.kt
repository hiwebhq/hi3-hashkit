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
import androidx.compose.material3.Button
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
import androidx.compose.runtime.getValue
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

private const val RENT_URL = "https://hashpower.braiins.com"

data class HashRentalState(
    val loading: Boolean = true,
    val market: BraiinsHashpowerClient.Market? = null,
    val asks: List<BraiinsHashpowerClient.Ask> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class HashRentalViewModel @Inject constructor(
    private val client: BraiinsHashpowerClient,
) : ViewModel() {

    private val _state = MutableStateFlow(HashRentalState())
    val state: StateFlow<HashRentalState> = _state

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            when (val r = client.fetch()) {
                is BraiinsHashpowerClient.Result.Ok ->
                    _state.value = HashRentalState(loading = false, market = r.market, asks = r.asks.take(6))
                is BraiinsHashpowerClient.Result.Error ->
                    _state.value = _state.value.copy(loading = false, error = r.message)
            }
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
    fun openRent() {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(RENT_URL))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
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
                                m.bestAskSat?.let { "${btc(it)} BTC / PH·day" } ?: "—",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = HiBrand.accent,
                            )
                            Text("Best ask (cheapest hashrate to rent)", style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
                            Spacer12()
                            m.lastAvgPriceSat?.let { Line("Last avg price", "${btc(it)} BTC / PH·day") }
                            m.bestBidSat?.let { Line("Best bid", "${btc(it)} BTC / PH·day") }
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
                            Text("${btc(ask.priceSat)} BTC / PH·day", style = MaterialTheme.typography.bodyMedium, color = HiBrand.textPrimary)
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
private fun Spacer12() = androidx.compose.foundation.layout.Spacer(Modifier.padding(6.dp))

/** sats → BTC string. */
private fun btc(sat: Long): String = "%.4f".format(sat / 100_000_000.0)
