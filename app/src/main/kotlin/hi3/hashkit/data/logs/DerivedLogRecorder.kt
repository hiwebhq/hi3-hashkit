package hi3.hashkit.data.logs

import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.repo.LogRepository
import hi3.hashkit.data.repo.MinerRepository
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.logs.LogEventDeriver
import hi3.hashkit.domain.model.Capability
import hi3.hashkit.domain.model.MinerTelemetry
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Feeds the per-miner log store with DERIVED events for miners whose firmware exposes no
 * log stream (Avalon, Braiins OS, Antminer-class, ESP-Miner forks without /api/ws):
 * telemetry-delta events from [LogEventDeriver] plus any adapter-specific health lines
 * (e.g. Avalon `notify` counter diffs). Miners with real logs are skipped so raw firmware
 * lines never mix with synthesized ones.
 */
@Singleton
class DerivedLogRecorder @Inject constructor(
    private val logRepository: LogRepository,
    private val minerRepository: MinerRepository,
    private val registry: AdapterRegistry,
) {
    /** Last snapshot per miner id; in-memory — after app start the first poll is baseline-only. */
    private val prev = java.util.concurrent.ConcurrentHashMap<Long, LogEventDeriver.Snapshot>()

    suspend fun onPolled(entity: MinerEntity, telemetry: MinerTelemetry, chipTempLimitC: Double?) {
        if (entity.isDemo) return
        val adapter = registry.byType(entity.adapterType) ?: return
        val caps = adapter.getCapabilities(minerRepository.identityOf(entity))
        if (Capability.LOGS in caps.supported) return // real firmware logs — don't mix

        val curr = LogEventDeriver.snapshot(telemetry)
        val lines = LogEventDeriver.derive(prev.put(entity.id, curr), curr, chipTempLimitC) +
            runCatching { adapter.healthEventLines(MinerHost(entity.host, entity.port)) }
                .getOrDefault(emptyList())
        if (lines.isNotEmpty()) runCatching { logRepository.record(entity.id, lines) }
    }
}
