package hi3.hashkit.ui.sitemap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.ui.theme.HiBrand

/** The live capture page: status, controls, and the filling rack map. */
@Composable
internal fun CaptureContent(vm: SiteMapViewModel, state: SiteMapState, modifier: Modifier) {
    val cfg = state.config ?: return
    var showManual by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { StatusCard(state, cfg) }
        if (state.phase == SitePhase.CAPTURE) {
            item { ControlButtons(vm, state) }
            item { EditButtons(vm, onManual = { showManual = true }) }
        }
        item { RackMap(state, cfg) }
        if (state.phase == SitePhase.DONE) {
            doneItems(vm, state)
        }
    }
    if (showManual) {
        ManualFillDialog(onFill = { vm.manualFill(it); showManual = false }, onDismiss = { showManual = false })
    }
}

@Composable
private fun StatusCard(state: SiteMapState, cfg: SiteMapConfig) {
    val current = SiteWalk.slotAt(cfg, state.cursor)
    Card(colors = CardDefaults.cardColors(containerColor = HiBrand.surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            val headline = when {
                state.phase == SitePhase.DONE -> "Capture finished"
                state.running -> "Listening on UDP 14235 — press the IP Report button on:"
                else -> "Paused — presses are ignored"
            }
            Text(headline, style = MaterialTheme.typography.bodySmall, color = HiBrand.textSecondary)
            if (state.phase == SitePhase.CAPTURE && current != null) {
                Text(current.label, style = MaterialTheme.typography.titleMedium, color = HiBrand.accent)
            }
            Text(
                "${state.captured.size} of ${cfg.totalSlots} captured" +
                    (if (state.skipped.isNotEmpty()) " · ${state.skipped.size} skipped" else ""),
                style = MaterialTheme.typography.bodySmall, color = HiBrand.textPrimary,
            )
            state.lastEvent?.let {
                Text("Last: $it", style = MaterialTheme.typography.bodySmall, color = HiBrand.statusOnline)
            }
            state.error?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = HiBrand.statusOffline)
            }
        }
    }
}

@Composable
private fun ControlButtons(vm: SiteMapViewModel, state: SiteMapState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        if (state.running) {
            OutlinedButton(onClick = vm::pause, modifier = Modifier.weight(1f)) { Text("Pause") }
        } else {
            Button(onClick = vm::resume, modifier = Modifier.weight(1f)) { Text("Resume") }
        }
        OutlinedButton(onClick = vm::stop, modifier = Modifier.weight(1f)) { Text("Stop") }
    }
}

@Composable
private fun EditButtons(vm: SiteMapViewModel, onManual: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        OutlinedButton(onClick = vm::undoLast, modifier = Modifier.weight(1f)) { Text("Undo") }
        OutlinedButton(onClick = vm::skipCurrent, modifier = Modifier.weight(1f)) { Text("Skip slot") }
        OutlinedButton(onClick = onManual, modifier = Modifier.weight(1f)) { Text("Manual") }
    }
}

@Composable
private fun RackMap(state: SiteMapState, cfg: SiteMapConfig) {
    // The map follows the cursor's rack; arrows let you review other racks, and a new
    // capture snaps the view back to the live rack.
    val liveIndex = state.cursor.coerceAtMost(cfg.totalSlots - 1)
    val liveSlot = SiteWalk.slotAt(cfg, liveIndex) ?: return
    val liveRack = (liveSlot.building - 1) * cfg.racksPerBuilding + (liveSlot.rack - 1)
    var viewed by rememberSaveable { mutableStateOf(liveRack) }
    LaunchedEffect(liveRack) { viewed = liveRack }
    val totalRacks = cfg.buildings * cfg.racksPerBuilding
    val building = viewed / cfg.racksPerBuilding + 1
    val rack = viewed % cfg.racksPerBuilding + 1
    Card(colors = CardDefaults.cardColors(containerColor = HiBrand.surface), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { if (viewed > 0) viewed-- }, enabled = viewed > 0) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous rack")
                }
                Text(
                    "Building $building · Rack $rack",
                    style = MaterialTheme.typography.titleSmall, color = HiBrand.textPrimary,
                    modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold,
                )
                IconButton(onClick = { if (viewed < totalRacks - 1) viewed++ }, enabled = viewed < totalRacks - 1) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next rack")
                }
            }
            RackGrid(state, cfg, building, rack)
            Text(
                "Tier 1 is the bottom row; positions run left → right.",
                style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary,
            )
        }
    }
}

@Composable
private fun RackGrid(state: SiteMapState, cfg: SiteMapConfig, building: Int, rack: Int) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        for (tier in cfg.tiersPerRack downTo 1) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "T$tier", style = MaterialTheme.typography.labelSmall,
                    color = HiBrand.textSecondary, modifier = Modifier.width(24.dp),
                )
                for (position in 1..cfg.positionsPerTier) {
                    val index = SiteWalk.indexOf(cfg, Slot(building, rack, tier, position))
                    MapCell(
                        captured = state.captured[index],
                        isCursor = state.phase == SitePhase.CAPTURE && index == state.cursor,
                        isSkipped = index in state.skipped,
                    )
                }
            }
        }
    }
}

@Composable
private fun MapCell(captured: CapturedSlot?, isCursor: Boolean, isSkipped: Boolean) {
    val bg = when {
        isCursor -> HiBrand.accent
        captured != null -> HiBrand.surfaceRaised
        else -> HiBrand.surface
    }
    val fg = when {
        isCursor -> HiBrand.background
        captured != null -> HiBrand.statusOnline
        else -> HiBrand.textSecondary
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(width = 44.dp, height = 36.dp)
            .background(bg, RoundedCornerShape(6.dp))
            .border(1.dp, if (isCursor) HiBrand.accent else HiBrand.outline, RoundedCornerShape(6.dp)),
    ) {
        Text(
            text = captured?.lastOctet ?: if (isSkipped) "×" else "·",
            style = MaterialTheme.typography.labelMedium, color = fg,
            fontWeight = if (captured != null) FontWeight.Bold else FontWeight.Normal,
        )
    }
}
