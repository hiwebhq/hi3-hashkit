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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.R
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
    onSiteHeatmap: () -> Unit,
) {
    val items = listOf(
        AdvancedItem(
            stringResource(R.string.adv_energy_title),
            stringResource(R.string.adv_energy_subtitle),
            Icons.Filled.Bolt, onEnergyCost,
        ),
        AdvancedItem(
            stringResource(R.string.adv_heat_reuse_title),
            stringResource(R.string.adv_heat_reuse_subtitle),
            Icons.Filled.Thermostat, onHeatReuse,
        ),
        AdvancedItem(
            stringResource(R.string.adv_solar_title),
            stringResource(R.string.adv_solar_subtitle),
            Icons.Filled.WbSunny, onSolar,
        ),
        AdvancedItem(
            stringResource(R.string.adv_price_curtailment_title),
            stringResource(R.string.adv_price_curtailment_subtitle),
            Icons.Filled.PriceChange, onPriceCurtailment,
        ),
        AdvancedItem(
            stringResource(R.string.adv_hash_rental_title),
            stringResource(R.string.adv_hash_rental_subtitle),
            Icons.Filled.ShoppingCart, onHashRental,
        ),
        AdvancedItem(
            stringResource(R.string.adv_acoustic_title),
            stringResource(R.string.adv_acoustic_subtitle),
            Icons.Filled.Hearing, onAcoustic,
        ),
        AdvancedItem(
            stringResource(R.string.adv_ar_overlay_title),
            stringResource(R.string.adv_ar_overlay_subtitle),
            Icons.Filled.CameraAlt, onArOverlay,
        ),
        AdvancedItem(
            stringResource(R.string.adv_nfc_title),
            stringResource(R.string.adv_nfc_subtitle),
            Icons.Filled.Nfc, onNfcProgram,
        ),
        AdvancedItem(
            stringResource(R.string.adv_rules_title),
            stringResource(R.string.adv_rules_subtitle),
            Icons.Filled.Bolt, onRules,
        ),
        AdvancedItem(
            stringResource(R.string.adv_schedules_title),
            stringResource(R.string.adv_schedules_subtitle),
            Icons.Filled.Schedule, onSchedules,
        ),
        AdvancedItem(
            stringResource(R.string.adv_address_book_title),
            stringResource(R.string.adv_address_book_subtitle),
            Icons.Filled.Bookmark, onAddressBook,
        ),
        AdvancedItem(
            stringResource(R.string.adv_site_map_title),
            stringResource(R.string.adv_site_map_subtitle),
            Icons.Filled.Map, onSiteMap,
        ),
        AdvancedItem(
            stringResource(R.string.adv_heatmap_title),
            stringResource(R.string.adv_heatmap_subtitle),
            Icons.Filled.Thermostat, onSiteHeatmap,
        ),
        AdvancedItem(
            stringResource(R.string.adv_rack_title),
            stringResource(R.string.adv_rack_subtitle),
            Icons.Filled.GridView, onRack,
        ),
        AdvancedItem(
            stringResource(R.string.adv_pool_speed_title),
            stringResource(R.string.adv_pool_speed_subtitle),
            Icons.Filled.Speed, onPoolSpeed,
        ),
        AdvancedItem(
            stringResource(R.string.adv_live_bitcoin_title),
            stringResource(R.string.adv_live_bitcoin_subtitle),
            Icons.Filled.CurrencyBitcoin, onLiveBitcoin,
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adv_title), fontWeight = FontWeight.Bold) },
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
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    stringResource(R.string.adv_intro),
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
