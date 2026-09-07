package hi3.hashkit.data.repo

import hi3.hashkit.data.db.MinerAddressEntity
import hi3.hashkit.data.db.MinerDao
import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.db.RawResponseEntity
import hi3.hashkit.data.db.TelemetryDao
import hi3.hashkit.domain.adapter.AdapterRegistry
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

sealed interface AddMinerResult {
    data class Added(val minerId: Long) : AddMinerResult
    data class AlreadyKnown(val minerId: Long) : AddMinerResult
    data class NotSupported(val message: String) : AddMinerResult
    data class Unreachable(val message: String) : AddMinerResult
}

@Singleton
class MinerRepository @Inject constructor(
    private val minerDao: MinerDao,
    private val telemetryDao: TelemetryDao,
    private val registry: AdapterRegistry,
    private val hourlyDao: hi3.hashkit.data.db.HourlyDao? = null,
) {
    /** Telemetry older than this renders as stale/UNKNOWN rather than pretending freshness. */
    val staleAfterMs: Long = 120_000

    fun observeMinerEntities(): Flow<List<MinerEntity>> = minerDao.observeAll()

    fun observeMinerEntity(id: Long): Flow<MinerEntity?> = minerDao.observeById(id)

    suspend fun latestTelemetry(minerId: Long): MinerTelemetry? =
        telemetryDao.latest(minerId)?.toDomain()

    suspend fun latestRawResponse(minerId: Long): String? =
        telemetryDao.latestRaw(minerId)?.body

    suspend fun toDomain(entity: MinerEntity, now: Instant = Instant.now()): Miner =
        entity.toDomain(latestTelemetry(entity.id), staleAfterMs, now)

    /** Stored identity fields of a miner, for capability checks without a network call. */
    fun identityOf(entity: MinerEntity): MinerIdentity = MinerIdentity(
        macAddress = entity.macAddress,
        serialNumber = entity.serialNumber,
        hostname = entity.hostname,
        manufacturer = entity.manufacturer,
        model = entity.model,
        boardVersion = entity.boardVersion,
        asicModel = entity.asicModel,
        firmwareFamily = entity.firmwareFamily,
        firmwareVersion = entity.firmwareVersion,
    )

    /** Probe a host with every real adapter (each on its own port) and register if supported. */
    suspend fun addByHost(host: String, port: Int = 0): AddMinerResult {
        var lastUnreachable: String? = null
        for (adapter in registry.probeable()) {
            val probePort = if (port > 0) port else adapter.defaultPort
            when (val probe = adapter.probe(MinerHost(host, probePort))) {
                is ProbeResult.Supported ->
                    return upsertDiscovered(host, probePort, probe.adapterType, probe.identity)
                is ProbeResult.Unreachable -> lastUnreachable = probe.cause
                ProbeResult.NotThisDevice -> Unit
            }
        }
        return lastUnreachable?.let { AddMinerResult.Unreachable(it) }
            ?: AddMinerResult.NotSupported("Host responded but no installed adapter recognizes it")
    }

    suspend fun upsertDiscovered(
        host: String,
        port: Int,
        adapterType: String,
        identity: MinerIdentity,
        isDemo: Boolean = false,
    ): AddMinerResult {
        val now = System.currentTimeMillis()
        val resolvedPort = if (port > 0) port else registry.byType(adapterType)?.defaultPort ?: port
        val stableKey = (if (isDemo) "demo:" else "") + identity.stableKey(host)
        val existing = minerDao.byStableKey(stableKey)
        if (existing != null) {
            // Same miner (possibly on a new IP): re-bind address, keep history.
            minerDao.updateHostAndSeen(existing.id, host, now)
            minerDao.insertAddress(MinerAddressEntity(minerId = existing.id, host = host, firstSeenEpochMs = now, lastSeenEpochMs = now))
            minerDao.touchAddress(existing.id, host, now)
            return AddMinerResult.AlreadyKnown(existing.id)
        }
        val id = minerDao.insert(
            MinerEntity(
                stableKey = stableKey,
                adapterType = adapterType,
                name = identity.hostname ?: identity.model ?: host,
                host = host,
                port = resolvedPort,
                macAddress = identity.macAddress,
                serialNumber = identity.serialNumber,
                hostname = identity.hostname,
                manufacturer = identity.manufacturer,
                model = identity.model,
                boardVersion = identity.boardVersion,
                asicModel = identity.asicModel,
                firmwareFamily = identity.firmwareFamily,
                firmwareVersion = identity.firmwareVersion,
                groupName = null,
                location = null,
                notes = null,
                tagsCsv = "",
                expectedHashrateGhs = null,
                isDemo = isDemo,
                createdAtEpochMs = now,
                lastSeenAtEpochMs = now,
            )
        )
        minerDao.insertAddress(MinerAddressEntity(minerId = id, host = host, firstSeenEpochMs = now, lastSeenEpochMs = now))
        return AddMinerResult.Added(id)
    }

    suspend fun deleteMiner(id: Long) = minerDao.delete(id)

    /** Update user-editable metadata (name, group, location, notes, tags, expected hashrate). */
    suspend fun updateMinerMeta(
        id: Long,
        name: String,
        group: String?,
        location: String?,
        notes: String?,
        tags: List<String>,
        expectedHashrateGhs: Double?,
        alertOverrides: hi3.hashkit.domain.alerts.AlertOverrides? = null,
    ) {
        val entity = minerDao.byId(id) ?: return
        var updated = entity.copy(
            name = name.ifBlank { entity.name },
            groupName = group?.takeIf { it.isNotBlank() },
            location = location?.takeIf { it.isNotBlank() },
            notes = notes?.takeIf { it.isNotBlank() },
            tagsCsv = tags.joinToString(","),
            expectedHashrateGhs = expectedHashrateGhs?.takeIf { it > 0 },
        )
        // A non-null overrides object replaces the stored overrides wholesale, so
        // blanking a field in the edit dialog clears that override back to global.
        if (alertOverrides != null) {
            updated = updated.copy(
                alertHashBelowPct = alertOverrides.hashrateBelowPercent,
                alertChipTempC = alertOverrides.chipTempC,
                alertVrTempC = alertOverrides.vrTempC,
                alertRejectPct = alertOverrides.rejectRatePercent,
                alertsMuted = alertOverrides.muted,
            )
        }
        minerDao.update(updated)
    }

    /** Poll one miner, persist the outcome (including honest OFFLINE samples). */
    suspend fun pollMiner(entity: MinerEntity): MinerTelemetry {
        val adapter = registry.byType(entity.adapterType)
            ?: return offlineSample("Adapter ${entity.adapterType} not installed").also {
                telemetryDao.insert(it.toEntity(entity.id))
            }
        return when (val result = adapter.getTelemetry(MinerHost(entity.host, entity.port))) {
            is TelemetryResult.Success -> {
                val now = System.currentTimeMillis()
                telemetryDao.insert(result.telemetry.toEntity(entity.id))
                if (!entity.isDemo) {
                    telemetryDao.insertRaw(
                        RawResponseEntity(
                            minerId = entity.id,
                            timestampEpochMs = now,
                            endpoint = "/api/system/info",
                            body = result.rawResponse,
                        )
                    )
                    // Keep only ~1h of raw bodies; they exist for diagnostics, not history.
                    telemetryDao.pruneRawBefore(now - 3_600_000)
                }
                minerDao.updateHostAndSeen(entity.id, entity.host, now)
                minerDao.touchAddress(entity.id, entity.host, now)
                result.telemetry
            }
            is TelemetryResult.Offline -> offlineSample(result.cause).also {
                telemetryDao.insert(it.toEntity(entity.id))
            }
            is TelemetryResult.ParseError -> offlineSample("Parse error: ${result.cause}").also {
                telemetryDao.insert(it.toEntity(entity.id))
            }
        }
    }

    private fun offlineSample(@Suppress("UNUSED_PARAMETER") cause: String): MinerTelemetry =
        MinerTelemetry(
            timestamp = Instant.now(),
            status = MinerStatus.OFFLINE,
            hashrateGhs = Sourced.unavailable(),
        )

    /**
     * Roll completed hours of raw telemetry into hourly aggregates (idempotent via a
     * per-miner high-water mark), then prune raw samples past retention and hourly
     * rows past two years. Called from the periodic maintenance tick.
     */
    suspend fun downsampleAndPrune(rawRetentionDays: Int) {
        val hourly = hourlyDao ?: return
        val currentHourStart = Downsampler.hourStartOf(System.currentTimeMillis())
        for (miner in minerDao.observeAll().first()) {
            if (miner.isDemo) continue
            val from = hourly.highWaterMark(miner.id)?.plus(Downsampler.HOUR_MS)
                ?: telemetryDao.oldestSampleTimestamp(miner.id)?.let { Downsampler.hourStartOf(it) }
                ?: continue
            if (from >= currentHourStart) continue
            val samples = telemetryDao.samplesBetween(miner.id, from, currentHourStart)
            if (samples.isEmpty()) continue
            hourly.upsertAll(Downsampler.aggregate(miner.id, samples))
        }
        val now = System.currentTimeMillis()
        telemetryDao.pruneBefore(now - rawRetentionDays * 86_400_000L)
        hourly.pruneBefore(now - 730L * 86_400_000L)
    }

    suspend fun pruneTelemetryBefore(beforeEpochMs: Long) {
        telemetryDao.pruneBefore(beforeEpochMs)
    }

    fun observeTelemetrySince(minerId: Long, sinceEpochMs: Long): Flow<List<MinerTelemetry>> =
        telemetryDao.observeSince(minerId, sinceEpochMs)
            .map { rows -> rows.map { it.toDomain() } }

    /** Fleet-total hashrate trend over [windowMs], bucketed for a compact chart. */
    fun observeFleetHashrateTrend(
        windowMs: Long,
        includeDemo: Boolean,
        buckets: Int = 60,
    ): Flow<List<FleetTrendPoint>> {
        val start = System.currentTimeMillis() - windowMs
        return telemetryDao.observeFleetSamplesSince(start, includeDemo)
            .map { pts -> FleetSeries.bucket(pts, start, System.currentTimeMillis(), buckets) }
    }

    /**
     * Chart history: raw samples where they exist, hourly aggregates for older spans.
     * Hourly points are placed mid-hour and sourced CALCULATED so the UI can tell
     * summaries from live readings.
     */
    fun observeHistoryMerged(minerId: Long, sinceEpochMs: Long): Flow<List<MinerTelemetry>> {
        val hourly = hourlyDao
            ?: return observeTelemetrySince(minerId, sinceEpochMs)
        return kotlinx.coroutines.flow.combine(
            telemetryDao.observeSince(minerId, sinceEpochMs),
            hourly.observeSince(minerId, sinceEpochMs),
        ) { raw, hours ->
            val oldestRaw = raw.firstOrNull()?.timestampEpochMs ?: Long.MAX_VALUE
            val fromHourly = hours
                .filter { it.hourStartEpochMs + Downsampler.HOUR_MS <= oldestRaw }
                .map { it.toDomainPoint() }
            fromHourly + raw.map { it.toDomain() }
        }
    }

    private fun hi3.hashkit.data.db.TelemetryHourlyEntity.toDomainPoint(): MinerTelemetry =
        MinerTelemetry(
            timestamp = Instant.ofEpochMilli(hourStartEpochMs + Downsampler.HOUR_MS / 2),
            status = if (onlineSamples > 0) MinerStatus.ONLINE else MinerStatus.OFFLINE,
            hashrateGhs = Sourced.calculated(avgHashrateGhs),
            powerW = Sourced.calculated(avgPowerW),
            chipTempC = Sourced.calculated(avgChipTempC),
            vrTempC = Sourced.calculated(maxVrTempC),
        )
}
