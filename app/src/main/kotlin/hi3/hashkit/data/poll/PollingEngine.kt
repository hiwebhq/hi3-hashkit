package hi3.hashkit.data.poll

import androidx.glance.appwidget.updateAll
import hi3.hashkit.data.alerts.AlertRepository
import hi3.hashkit.domain.alerts.withOverrides
import hi3.hashkit.data.prefs.SettingsRepository
import hi3.hashkit.data.repo.MinerRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Foreground polling: runs while the app is open. Background monitoring is a separate,
 * explicitly enabled WorkManager path ([MonitorWorker]) with Android's >=15-minute
 * granularity — the UI never pretends otherwise.
 */
@Singleton
class PollingEngine @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val repository: MinerRepository,
    private val alertRepository: AlertRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduleEngine: hi3.hashkit.data.schedule.ScheduleEngine,
    private val farmRepository: hi3.hashkit.data.repo.FarmRepository,
    private val smartPlugClient: hi3.hashkit.integrations.plug.SmartPlugClient,
    private val auditDao: hi3.hashkit.data.db.AuditDao,
) {
    private var job: Job? = null
    private val lastPrune = AtomicLong(0)

    /** Miners whose plug we've already cut this over-temp episode (avoids repeated commands). */
    private val cutMiners = java.util.Collections.synchronizedSet(mutableSetOf<Long>())

    private val _lastRefresh = MutableStateFlow<Instant?>(null)
    val lastRefresh: StateFlow<Instant?> = _lastRefresh

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling

    /** Rolling poll-cycle timing, exposed in the diagnostics bundle. */
    @Volatile var lastPollDurationMs: Long = 0L; private set
    @Volatile var pollCount: Long = 0L; private set
    private var totalPollMs: Long = 0L
    val avgPollDurationMs: Long get() = if (pollCount == 0L) 0L else totalPollMs / pollCount

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            _isPolling.value = true
            try {
                while (isActive) {
                    pollAllOnce()
                    delay(currentIntervalMs())
                }
            } finally {
                _isPolling.value = false
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /**
     * Foreground cadence follows the active farm's per-farm interval; when no farm is
     * active it falls back to the global default. Clamped to 5s..1d.
     */
    private suspend fun currentIntervalMs(): Long {
        val farmInterval = runCatching { farmRepository.activeOrDefaultFarm()?.refreshIntervalMs }.getOrNull()
        val interval = farmInterval ?: settingsRepository.current().pollIntervalMs
        return interval.coerceIn(5_000L, 86_400_000L)
    }

    suspend fun pollAllOnce() {
        val startedAt = System.nanoTime()
        val settings = settingsRepository.current()
        val miners = repository.observeMinerEntities().first()
        supervisorScope {
            miners.map { entity ->
                launch {
                    runCatching {
                        val telemetry = repository.pollMiner(entity)
                        if (settings.alertsEnabled && !entity.isDemo && !entity.alertsMuted) {
                            alertRepository.processTelemetry(
                                minerId = entity.id,
                                minerName = entity.name,
                                telemetry = telemetry,
                                expectedHashrateGhs = entity.expectedHashrateGhs,
                                thresholds = settings.alertThresholds.withOverrides(
                                    hi3.hashkit.domain.alerts.AlertOverrides(
                                        hashrateBelowPercent = entity.alertHashBelowPct,
                                        chipTempC = entity.alertChipTempC,
                                        vrTempC = entity.alertVrTempC,
                                        rejectRatePercent = entity.alertRejectPct,
                                    )
                                ),
                            )
                        }
                        if (!entity.isDemo) maybeCutPower(entity, telemetry)
                    }
                }
            }.forEach { it.join() }
        }
        _lastRefresh.value = Instant.now()
        lastPollDurationMs = (System.nanoTime() - startedAt) / 1_000_000
        totalPollMs += lastPollDurationMs
        pollCount += 1
        runCatching { hi3.hashkit.widget.HashkitWidget().updateAll(appContext) }
        runCatching { scheduleEngine.runDueSchedules() }
        pruneIfDue(settings.retentionDays)
    }

    /**
     * Over-temp safety cutoff: if the miner has a smart plug configured and its chip temp
     * reaches the cutoff, switch the plug OFF once. Turning it back on is always manual, so
     * we never oscillate power; the cut flag clears when the temp falls back below the limit.
     */
    private suspend fun maybeCutPower(entity: hi3.hashkit.data.db.MinerEntity, telemetry: hi3.hashkit.domain.model.MinerTelemetry) {
        val type = hi3.hashkit.integrations.plug.PlugType.fromName(entity.plugType) ?: return
        val cutoff = entity.plugCutoffTempC ?: return
        val temp = telemetry.chipTempC.value ?: return
        if (temp < cutoff) { cutMiners.remove(entity.id); return }
        if (!cutMiners.add(entity.id)) return // already cut this episode
        val plug = hi3.hashkit.integrations.plug.SmartPlugClient.Plug(type, entity.plugHost, entity.plugOnUrl, entity.plugOffUrl)
        val ok = smartPlugClient.turnOff(plug)
        runCatching {
            auditDao.insert(
                hi3.hashkit.data.db.AuditEventEntity(
                    minerId = entity.id,
                    atEpochMs = System.currentTimeMillis(),
                    action = "smart_plug_cutoff",
                    previousJson = "{\"chipTempC\":$temp}",
                    appliedJson = "{\"cutoffC\":$cutoff,\"plug\":\"${type.name}\"}",
                    outcome = if (ok) "power cut" else "cut FAILED",
                )
            )
        }
        if (!ok) cutMiners.remove(entity.id) // let it retry next cycle if the command failed
    }

    /** Maintenance: downsample completed hours + prune, at most once per 6h of use. */
    private suspend fun pruneIfDue(retentionDays: Int) {
        val now = System.currentTimeMillis()
        val last = lastPrune.get()
        if (now - last < 6 * 3_600_000) return
        if (!lastPrune.compareAndSet(last, now)) return
        runCatching { repository.downsampleAndPrune(retentionDays) }
    }
}
