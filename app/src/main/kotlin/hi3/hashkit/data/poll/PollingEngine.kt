package hi3.hashkit.data.poll

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
    private val repository: MinerRepository,
    private val alertRepository: AlertRepository,
    private val settingsRepository: SettingsRepository,
    private val scheduleEngine: hi3.hashkit.data.schedule.ScheduleEngine,
) {
    private var job: Job? = null
    private val lastPrune = AtomicLong(0)

    private val _lastRefresh = MutableStateFlow<Instant?>(null)
    val lastRefresh: StateFlow<Instant?> = _lastRefresh

    private val _isPolling = MutableStateFlow(false)
    val isPolling: StateFlow<Boolean> = _isPolling

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            _isPolling.value = true
            try {
                while (isActive) {
                    pollAllOnce()
                    delay(settingsRepository.current().pollIntervalMs)
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

    suspend fun pollAllOnce() {
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
                    }
                }
            }.forEach { it.join() }
        }
        _lastRefresh.value = Instant.now()
        runCatching { scheduleEngine.runDueSchedules() }
        pruneIfDue(settings.retentionDays)
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
