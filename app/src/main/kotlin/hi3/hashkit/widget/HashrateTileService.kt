package hi3.hashkit.widget

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import hi3.hashkit.R
import hi3.hashkit.core.Units
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant

/**
 * Quick Settings tile: fleet hashrate in the notification shade, online count as the
 * subtitle, tap to open the app. Read-only on purpose — a shade tile is too easy to hit
 * by accident for anything that changes a miner.
 */
class HashrateTileService : TileService() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface TileEntryPoint {
        fun minerRepository(): MinerRepository
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { runCatching { refresh() } }
    }

    override fun onStopListening() {
        scope.coroutineContext.cancelChildren()
        super.onStopListening()
    }

    override fun onDestroy() {
        scope.coroutineContext.cancelChildren()
        super.onDestroy()
    }

    private suspend fun refresh() {
        val tile = qsTile ?: return
        val repo = EntryPointAccessors
            .fromApplication(applicationContext, TileEntryPoint::class.java)
            .minerRepository()
        val now = Instant.now()
        val miners = repo.observeMinerEntities().first().filter { !it.isDemo }.map { repo.toDomain(it, now) }
        val live = miners.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val total = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        tile.label = if (miners.isEmpty()) getString(R.string.widget_tile_name) else Units.formatHashrate(total)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = getString(R.string.widget_online_count, live.size, miners.size)
        }
        tile.state = if (live.isNotEmpty()) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
