package hi3.hashkit.ui.advanced

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CurrencyBitcoin
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Nfc
import androidx.compose.material.icons.filled.PriceChange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material.icons.filled.WbSunny
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.ui.theme.HiBrand

/**
 * Advanced hub — one home for every advanced-gated feature. Reached from the dashboard
 * overflow's "Advanced" item, which appears only when advanced features are unlocked
 * (Settings → Advanced features). Each row navigates to a full feature screen.
 *
 * Per-miner advanced tools (tuning optimizer, acoustic fan check) can also be opened from
 * a miner's detail screen; the acoustic row here starts with its own miner picker.
 */
private data class AdvancedItem(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Suppress("LongMethod") // a declarative list of feature rows, one entry per Advanced tool
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedScreen(
    onBack: () -> Unit,
    onSchedules: () -> Unit,
    onFarms: () -> Unit,
    onAddressBook: () -> Unit,
    onRack: () -> Unit,
    onPoolSpeed: () -> Unit,
    onRules: () -> Unit,
    onHeatReuse: () -> Unit,
    onSolar: () -> Unit,
    onPriceCurtailment: () -> Unit,
    onAcoustic: () -> Unit,
    onArOverlay: () -> Unit,
    onNfcProgram: () -> Unit,
    onHashRental: () -> Unit,
    onEnergyCost: () -> Unit,
    onLiveBitcoin: () -> Unit,
    onSiteMap: () -> Unit,
) {
    val items = listOf(
        AdvancedItem(
            "Energy cost",
            "Per-miner energy use and estimated cost, with a filter",
            Icons.Filled.Bolt, onEnergyCost,
        ),
        AdvancedItem(
            "Heat-reuse dashboard",
            "What your miners' waste heat is worth as space heating",
            Icons.Filled.Thermostat, onHeatReuse,
        ),
        AdvancedItem(
            "Solar-surplus mining",
            "Curtail the fleet to your solar export from Home Assistant",
            Icons.Filled.WbSunny, onSolar,
        ),
        AdvancedItem(
            "Electricity-price curtailment",
            "Pause on price spikes using the Octopus Agile half-hourly feed",
            Icons.Filled.PriceChange, onPriceCurtailment,
        ),
        AdvancedItem(
            "Hash rental",
            "Live Braiins Hashpower spot market — rent hashrate",
            Icons.Filled.ShoppingCart, onHashRental,
        ),
        AdvancedItem(
            "Acoustic fan health check",
            "Record a miner's fans and flag bearing wear on-device",
            Icons.Filled.Hearing, onAcoustic,
        ),
        AdvancedItem(
            "AR rack overlay",
            "Scan a miner's QR sticker or tap its NFC tag to float its live stats",
            Icons.Filled.CameraAlt, onArOverlay,
        ),
        AdvancedItem(
            "NFC tag programmer",
            "Write miner NFC tags (and QR) — whole fleet, or filtered from the Fleet table",
            Icons.Filled.Nfc, onNfcProgram,
        ),
        AdvancedItem(
            "Automation rules",
            "Condition → action rules across the fleet",
            Icons.Filled.Bolt, onRules,
        ),
        AdvancedItem(
            "Schedules",
            "Time-of-use and quiet-hours plans for miners and plugs",
            Icons.Filled.Schedule, onSchedules,
        ),
        AdvancedItem(
            "Farms",
            "Group miners into sites with per-farm rollups",
            Icons.Filled.Warehouse, onFarms,
        ),
        AdvancedItem(
            "Pool address book",
            "Saved stratum endpoints for quick re-pointing",
            Icons.Filled.Bookmark, onAddressBook,
        ),
        AdvancedItem(
            "Site Map",
            "Map buildings/racks/tiers, then capture miners by IP Report button walk",
            Icons.Filled.Map, onSiteMap,
        ),
        AdvancedItem(
            "Rack & site",
            "Physical rack layout, grouped by each miner's Location",
            Icons.Filled.GridView, onRack,
        ),
        AdvancedItem(
            "Pool speed test",
            "Measure stratum latency from this phone's network",
            Icons.Filled.Speed, onPoolSpeed,
        ),
        AdvancedItem(
            "Live Bitcoin",
            "Open the Hi3 live Bitcoin dashboard",
            Icons.Filled.CurrencyBitcoin, onLiveBitcoin,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced", fontWeight = FontWeight.Bold) },
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Power-user tools. Everything here runs on your own network and data — " +
                        "no cloud account required.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HiBrand.textSecondary,
                )
            }
            items.forEach { entry ->
                item { AdvancedRow(entry) }
            }
        }
    }
}

@Composable
private fun AdvancedRow(item: AdvancedItem) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = item.onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                item.icon,
                contentDescription = null,
                tint = HiBrand.accent,
                modifier = Modifier.size(26.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, color = HiBrand.textPrimary)
                Text(item.subtitle, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = HiBrand.textSecondary,
            )
        }
    }
}
