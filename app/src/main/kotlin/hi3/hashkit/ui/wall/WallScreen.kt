package hi3.hashkit.ui.wall

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.rack.RackGroup
import hi3.hashkit.ui.rack.RackViewModel
import hi3.hashkit.ui.theme.HiBrand

/**
 * Kiosk / wall-dashboard view: a full-screen, glanceable fleet board meant for a spare
 * phone, tablet, or Android TV left on the shelf. Keeps the screen awake while shown and
 * auto-updates from the same poll cycle as the dashboard. Read-only — no controls here.
 */
@Composable
fun WallScreen(
    onExit: () -> Unit,
    viewModel: RackViewModel = hiltViewModel(),
) {
    val groups by viewModel.groups.collectAsStateWithLifecycle()
    val fahrenheit by viewModel.useFahrenheit.collectAsStateWithLifecycle()

    // Keep the screen on while the wall is up; clear the flag when leaving.
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val allMiners = groups.flatMap { it.miners }
    val online = allMiners.count { it.status == MinerStatus.ONLINE }
    val offline = allMiners.count { it.status == MinerStatus.OFFLINE }
    val totalHash = allMiners
        .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }

    Box(Modifier.fillMaxSize().background(HiBrand.background)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(
                            Units.formatHashrate(totalHash),
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold,
                            color = HiBrand.textPrimary,
                        )
                        Text(
                            "$online online · $offline offline · ${allMiners.size} miners",
                            fontSize = 24.sp,
                            color = HiBrand.textSecondary,
                        )
                    }
                    IconButton(onClick = onExit, modifier = Modifier.size(56.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Exit wall mode",
                            tint = HiBrand.textSecondary,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }
            }
            if (allMiners.isEmpty()) {
                item {
                    Text(
                        "No miners to display.",
                        style = MaterialTheme.typography.titleLarge,
                        color = HiBrand.textSecondary,
                    )
                }
            }
            for (group in groups) {
                item(key = "wall-${group.location}") {
                    Column {
                        Text(
                            "${group.location}  ·  ${group.online}/${group.total}",
                            fontSize = 30.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = HiBrand.textPrimary,
                            modifier = Modifier.padding(bottom = 14.dp),
                        )
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp),
                        ) {
                            group.miners.forEach { WallTile(it, fahrenheit) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WallTile(miner: Miner, fahrenheit: Boolean) {
    val color = when (miner.status) {
        MinerStatus.ONLINE -> HiBrand.statusOnline
        MinerStatus.DEGRADED -> HiBrand.statusDegraded
        MinerStatus.OFFLINE -> HiBrand.statusOffline
        MinerStatus.UNKNOWN -> HiBrand.statusUnknown
    }
    Column(
        modifier = Modifier
            .width(320.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(HiBrand.surface)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(22.dp).clip(CircleShape).background(color))
            Text(
                miner.name,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = HiBrand.textPrimary,
                maxLines = 1,
                modifier = Modifier.padding(start = 14.dp),
            )
        }
        val hr = miner.lastTelemetry?.hashrateGhs?.value
        Text(
            if (miner.status == MinerStatus.OFFLINE) "Offline" else Units.formatHashrate(hr),
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            color = if (miner.status == MinerStatus.OFFLINE) HiBrand.statusOffline else HiBrand.textPrimary,
        )
        val temp = miner.lastTelemetry?.chipTempC?.value
        Text(
            temp?.let { Units.formatTemp(it, fahrenheit) } ?: "—",
            fontSize = 22.sp,
            color = HiBrand.textSecondary,
        )
    }
}
