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
    private val remediationEngine: hi3.hashkit.data.remediation.RemediationEngine,
    private val wearSyncManager: hi3.hashkit.data.wear.WearSyncManager,
    private val firmwareChecker: hi3.hashkit.integrations.update.FirmwareUpdateChecker,
) {
    private var job: Job? = null
    private var safetyJob: Job? = null
    private val lastPrune = AtomicLong(0)
    private val lastAnomalyScan = AtomicLong(0)

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
        // Dedicated fast loop so the over-temp cutoff fires promptly regardless of the
        // (possibly slow) per-farm dashboard cadence. Only touches plug-armed miners.
        if (safetyJob?.isActive != true) {
            safetyJob = scope.launch {
                while (isActive) {
                    runCatching { safetyCycle() }
                    delay(SAFETY_INTERVAL_MS)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        safetyJob?.cancel()
        safetyJob = null
    }

    /** Public entry point for the always-on foreground safety service to run one pass. */
    suspend fun runSafetyCycleOnce() = safetyCycle()

    /**
     * Fast safety pass: poll only miners that have an armed smart-plug cutoff and apply it.
     * Cheap when none are configured (a single DB read, then nothing).
     */
    private suspend fun safetyCycle() {
        val armed = repository.observeMinerEntities().first()
            .filter { !it.isDemo && it.plugType != null && it.plugCutoffTempC != null }
        if (armed.isEmpty()) return
        supervisorScope {
            armed.map { entity ->
                launch { runCatching { maybeCutPower(entity, repository.pollMiner(entity)) } }
            }.forEach { it.join() }
        }
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
                        if (!entity.isDemo) {
                            maybeCutPower(entity, telemetry)
                            remediationEngine.onPolled(entity, telemetry.status)
                        }
                    }
                }
            }.forEach { it.join() }
        }
        _lastRefresh.value = Instant.now()
        lastPollDurationMs = (System.nanoTime() - startedAt) / 1_000_000
        totalPollMs += lastPollDurationMs
        pollCount += 1
        runCatching { hi3.hashkit.widget.HashkitWidget().updateAll(appContext) }
        runCatching {
            val domain = miners.map { repository.toDomain(it, Instant.now()) }
            wearSyncManager.publishFleetSummary(domain)
        }
        runCatching { scheduleEngine.runDueSchedules() }
        if (settings.alertsEnabled) {
            runCatching { scanTrendsAndFirmware() }
            runCatching { alertRepository.maybeSendDigest() }
        }
        pruneIfDue(settings.retentionDays)
    }

    /**
     * Throttled (~15 min) background scan for gradual trends (statistical anomalies) and
     * available firmware updates — too costly to run every poll, and both are slow signals.
     */
    private suspend fun scanTrendsAndFirmware() {
        val now = System.currentTimeMillis()
        val last = lastAnomalyScan.get()
        if (now - last < 15 * 60_000L) return
        if (!lastAnomalyScan.compareAndSet(last, now)) return

        runCatching { firmwareChecker.refreshIfEnabled() }
        val latest = firmwareChecker.axeOs.value?.tag
        val miners = repository.observeMinerEntities().first().filter { !it.isDemo && !it.alertsMuted }
        for (entity in miners) {
            // Gradual-trend anomalies over the last ~3h of history.
            runCatching {
                val history = repository.observeTelemetrySince(entity.id, now - 3 * 3_600_000L).first()
                alertRepository.processAnomalies(entity.id, entity.name, history)
            }
            // Firmware update available (AxeOS family only), once per day per miner.
            if (latest != null &&
                hi3.hashkit.integrations.update.FirmwareUpdateChecker.isAxeOsFamily(entity.firmwareFamily) &&
                hi3.hashkit.integrations.update.FirmwareUpdateChecker.isNewer(latest, entity.firmwareVersion)
            ) {
                runCatching {
                    alertRepository.raiseEvent(
                        entity.id, entity.name,
                        hi3.hashkit.domain.alerts.AlertType.FIRMWARE_UPDATE_AVAILABLE,
                        "${entity.name}: firmware $latest is available (running ${entity.firmwareVersion ?: "unknown"}).",
                        cooldownMs = 24 * 3_600_000L,
                    )
                }
            }
        }
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
        if (!ok) {
            cutMiners.remove(entity.id) // let it retry next cycle if the command failed
        } else {
            runCatching {
                alertRepository.raiseEvent(
                    entity.id, entity.name,
                    hi3.hashkit.domain.alerts.AlertType.PLUG_CUTOFF,
                    "${entity.name}: over-temp cutoff switched its smart plug OFF at ${temp.toInt()}°C " +
                        "(limit ${cutoff.toInt()}°C). Power stays off until you turn it back on.",
                    cooldownMs = 10 * 60_000L,
                )
            }
        }
    }

    /** Maintenance: downsample completed hours + prune, at most once per 6h of use. */
    private suspend fun pruneIfDue(retentionDays: Int) {
        val now = System.currentTimeMillis()
        val last = lastPrune.get()
        if (now - last < 6 * 3_600_000) return
        if (!lastPrune.compareAndSet(last, now)) return
        runCatching { repository.downsampleAndPrune(retentionDays) }
    }

    companion object {
        /** Cadence of the dedicated smart-plug safety poll (independent of the dashboard interval). */
        const val SAFETY_INTERVAL_MS = 20_000L
    }
}
