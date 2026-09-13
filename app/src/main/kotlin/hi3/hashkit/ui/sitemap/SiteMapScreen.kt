package hi3.hashkit.ui.sitemap

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.ui.theme.HiBrand

/**
 * Site Map (Advanced): define the site geometry, then walk the racks pressing each
 * miner's IP Report button; the map fills in walk order with each miner's last octet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SiteMapScreen(onBack: () -> Unit, vm: SiteMapViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Site Map", fontWeight = FontWeight.Bold) },
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
        if (state.phase == SitePhase.SETUP) {
            SetupContent(vm, state, Modifier.padding(padding))
        } else {
            CaptureContent(vm, state, Modifier.padding(padding))
        }
    }
}

@Composable
private fun SetupContent(vm: SiteMapViewModel, state: SiteMapState, modifier: Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                "Define the site, then walk the racks pressing each miner's IP Report button. " +
                    "Capture always starts at Building 1, Rack 1, Tier 1, Position 1 (left end), " +
                    "fills each tier left→right, then the next tier up, then the next rack.",
                style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary,
            )
        }
        item {
            OutlinedTextField(
                value = state.siteName, onValueChange = vm::setSiteName,
                label = { Text("Site name (used for Save to farm)") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        item { NumberField(state.buildingsText, vm::setBuildings, "Buildings") }
        item { NumberField(state.racksText, vm::setRacks, "Racks per building") }
        item { NumberField(state.tiersText, vm::setTiers, "Tiers per rack (shelves)") }
        item { NumberField(state.positionsText, vm::setPositions, "Positions per tier (miners per row)") }
        item { SetupSummary(vm) }
        item { SupportedModelsCard() }
        item {
            Button(onClick = vm::start, modifier = Modifier.fillMaxWidth(), enabled = vm.parsedConfig() != null) {
                Text("Start capture")
            }
        }
        state.error?.let { err ->
            item { Text(err, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusOffline) }
        }
    }
}

@Composable
private fun NumberField(value: String, onChange: (String) -> Unit, label: String) {
    OutlinedTextField(
        value = value, onValueChange = { onChange(it.filter { ch -> ch.isDigit() }) },
        label = { Text(label) }, singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun SetupSummary(vm: SiteMapViewModel) {
    val cfg = vm.parsedConfig() ?: return
    Text(
        "${cfg.totalSlots} slots total — ${cfg.slotsPerRack} per rack, ${cfg.slotsPerBuilding} per building.",
        style = MaterialTheme.typography.bodySmall, color = HiBrand.textPrimary,
    )
}

@Composable
private fun SupportedModelsCard() {
    Card(colors = CardDefaults.cardColors(containerColor = HiBrand.surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Supported models", style = MaterialTheme.typography.titleSmall, color = HiBrand.textPrimary)
            Text(
                "Capture listens for the IP Report button broadcast (UDP 14235): " +
                    "Bitmain Antminer (S9 through S21 era) on stock firmware, Braiins OS, and LuxOS. " +
                    "The app echoes an ack so the miner blinks green when its press is recorded. " +
                    "WhatsMiner, Canaan, and Bitaxe/ESP-Miner don't send this broadcast — fill their " +
                    "slots with Manual. The phone must be on the miners' LAN wifi (broadcasts don't " +
                    "cross routers or Tailscale).",
                style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary,
            )
        }
    }
}
