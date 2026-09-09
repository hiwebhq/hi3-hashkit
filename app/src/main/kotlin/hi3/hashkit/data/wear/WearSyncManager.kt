package hi3.hashkit.data.wear

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import dagger.hilt.android.qualifiers.ApplicationContext
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.tasks.Tasks
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Publishes a small fleet summary to the Wearable Data Layer for a paired Wear OS tile.
 *
 * Fire-and-forget and best-effort: if no watch is paired, or Google Play services isn't
 * present, the put simply fails silently — this is never on the critical path. Only totals
 * and counts leave the phone (see [WearContract]); no addresses, credentials, or worker
 * data. Nothing is sent over the internet — the Data Layer is a local Bluetooth/Wi-Fi link
 * between the phone and its paired watch, brokered by Play services.
 */
@Singleton
class WearSyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: hi3.hashkit.data.prefs.SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Compute the summary from the current fleet and push it to the Data Layer. */
    fun publishFleetSummary(miners: List<Miner>) {
        val real = miners.filter { !it.isDemo }
        val online = real.count { it.status == MinerStatus.ONLINE }
        val offline = real.count { it.status == MinerStatus.OFFLINE }
        val totalHashrate = real
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val worstTemp = real
            .filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
            .mapNotNull { it.lastTelemetry?.chipTempC?.value }
            .maxOrNull()

        scope.launch {
            // Accent color from the app's selected UI theme (dark variant for the watch).
            val accentArgb = runCatching {
                settingsRepository.current().themeColor.darkAccent.toArgb()
            }.getOrElse { 0xFF3987E5.toInt() }
            runCatching {
                val request = PutDataMapRequest.create(WearContract.PATH_FLEET_SUMMARY).apply {
                    dataMap.putDouble(WearContract.KEY_TOTAL_HASHRATE_GHS, totalHashrate)
                    dataMap.putInt(WearContract.KEY_ONLINE, online)
                    dataMap.putInt(WearContract.KEY_OFFLINE, offline)
                    dataMap.putInt(WearContract.KEY_TOTAL, real.size)
                    if (worstTemp != null) dataMap.putDouble(WearContract.KEY_WORST_TEMP_C, worstTemp)
                    dataMap.putInt(WearContract.KEY_ACCENT_ARGB, accentArgb)
                    // Ensures each poll produces a distinct DataItem so the watch is notified.
                    dataMap.putLong(WearContract.KEY_UPDATED_AT_MS, System.currentTimeMillis())
                }.asPutDataRequest().setUrgent()
                // Blocking wait is fine here: we're already on Dispatchers.IO, and this
                // avoids pulling in the coroutines-play-services bridge just for one call.
                Tasks.await(Wearable.getDataClient(context).putDataItem(request))
            }
        }
    }
}
