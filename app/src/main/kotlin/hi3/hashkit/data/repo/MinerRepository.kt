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
    private val smartPlugClient: hi3.hashkit.integrations.plug.SmartPlugClient? = null,
    private val maintenanceDao: hi3.hashkit.data.db.MaintenanceDao? = null,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context? = null,
) {
    /** Telemetry older than this renders as stale/UNKNOWN rather than pretending freshness. */
    val staleAfterMs: Long = 120_000

    fun observeMinerEntities(): Flow<List<MinerEntity>> = minerDao.observeAll()

    /** Store (or clear, with blank) a miner's admin password/API token, encrypted at rest. */
    suspend fun setCredential(minerId: Long, plaintext: String) {
        val trimmed = plaintext.trim()
        minerDao.updateCredential(minerId, if (trimmed.isEmpty()) null else hi3.hashkit.core.KeystoreCrypto.encrypt(trimmed))
    }
    /** Configure (or clear, with a null type) the per-miner smart-plug safety cutoff. */
    suspend fun setSmartPlug(
        minerId: Long, type: String?, host: String?, onUrl: String?, offUrl: String?, cutoffC: Double?,
    ) = minerDao.updatePlug(minerId, type, host?.trim()?.ifBlank { null }, onUrl?.trim()?.ifBlank { null },
        offUrl?.trim()?.ifBlank { null }, cutoffC)

    fun observeMinerEntity(id: Long): Flow<MinerEntity?> = minerDao.observeById(id)

    suspend fun latestTelemetry(minerId: Long): MinerTelemetry? =
        telemetryDao.latest(minerId)?.toDomain()

    suspend fun latestRawResponse(minerId: Long): String? =
        telemetryDao.latestRaw(minerId)?.body

    suspend fun toDomain(entity: MinerEntity, now: Instant = Instant.now()): Miner =
        entity.toDomain(latestTelemetry(entity.id), staleAfterMs, now)

    /** Build the domain miner from telemetry already in hand — no per-miner DB re-read. */
    fun toDomain(entity: MinerEntity, telemetry: MinerTelemetry?, now: Instant): Miner =
        entity.toDomain(telemetry, staleAfterMs, now)

    private val lastRawPrune = java.util.concurrent.atomic.AtomicLong(0)

    private companion object {
        const val RAW_PRUNE_EVERY_MS = 10L * 60_000L
        const val RAW_RETENTION_MS = 3_600_000L
    }

    /** Keep only ~1h of raw API bodies (diagnostics, not history); throttled to every 10 min
     *  — this was previously a full-scan DELETE per miner per poll. */
    suspend fun pruneRawIfDue() {
        val now = System.currentTimeMillis()
        if (now - lastRawPrune.get() < RAW_PRUNE_EVERY_MS) return
        if (!lastRawPrune.compareAndSet(lastRawPrune.get(), now)) return
        telemetryDao.pruneRawBefore(now - RAW_RETENTION_MS)
    }

    /** Sample-weighted average hashrate (GH/s) from the hourly rollups since [sinceEpochMs]. */
    suspend fun avgHashrateSince(minerId: Long, sinceEpochMs: Long): Double? {
        val rows = hourlyDao?.listSince(minerId, sinceEpochMs) ?: return null
        val weight = rows.filter { it.avgHashrateGhs != null }.sumOf { it.samples }
        if (weight <= 0) return null
        return rows.sumOf { (it.avgHashrateGhs ?: 0.0) * it.samples } / weight
    }

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

    suspend fun deleteMiner(id: Long) {
        // Maintenance notes go with the miner — photo files first, then their rows.
        maintenanceDao?.let { dao ->
            dao.listForMiner(id).forEach { note ->
                note.photoPath?.let { runCatching { java.io.File(it).delete() } }
            }
            dao.deleteForMiner(id)
        }
        minerDao.delete(id)
    }

    /**
     * One-time-per-launch cleanup: drop maintenance notes whose miner was deleted before
     * notes were cleaned up with the miner, and photo files no remaining note references.
     */
    suspend fun sweepOrphanMaintenance() {
        val dao = maintenanceDao ?: return
        val filesDir = context?.filesDir ?: return
        runCatching {
            dao.deleteOrphans()
            val referenced = dao.allPhotoPaths().toSet()
            java.io.File(filesDir, "maintenance").listFiles()?.forEach { f ->
                if (f.absolutePath !in referenced) f.delete()
            }
        }
    }

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
                val telemetry = augmentWithPlugPower(entity, result.telemetry)
                telemetryDao.insert(telemetry.toEntity(entity.id))
                if (!entity.isDemo) {
                    telemetryDao.insertRaw(
                        RawResponseEntity(
                            minerId = entity.id,
                            timestampEpochMs = now,
                            endpoint = "/api/system/info",
                            body = result.rawResponse,
                        )
                    )
                }
                minerDao.updateHostAndSeen(entity.id, entity.host, now)
                minerDao.touchAddress(entity.id, entity.host, now)
                telemetry
            }
            is TelemetryResult.Offline -> offlineSample(result.cause).also {
                telemetryDao.insert(it.toEntity(entity.id))
            }
            is TelemetryResult.ParseError -> offlineSample("Parse error: ${result.cause}").also {
                telemetryDao.insert(it.toEntity(entity.id))
            }
        }
    }

    /**
     * When a miner doesn't report its own power (e.g. stock Bitmain) but has a *metering*
     * smart plug configured, fill power from the plug's measured wall watts — turning
     * estimated efficiency/cost into measured. Miners that report their own power are left
     * untouched, and plugs that don't meter simply return null (no change).
     */
    private suspend fun augmentWithPlugPower(entity: MinerEntity, telemetry: MinerTelemetry): MinerTelemetry {
        if (telemetry.powerW.value != null) return telemetry
        val client = smartPlugClient ?: return telemetry
        val type = hi3.hashkit.integrations.plug.PlugType.fromName(entity.plugType) ?: return telemetry
        val watts = client.readPowerW(
            hi3.hashkit.integrations.plug.SmartPlugClient.Plug(
                type, entity.plugHost, entity.plugOnUrl, entity.plugOffUrl,
            )
        ) ?: return telemetry
        if (watts <= 0.0) return telemetry
        return telemetry.copy(
            powerW = Sourced.measured(watts),
            efficiencyJTh = Sourced.calculated(
                hi3.hashkit.core.Units.efficiencyJTh(watts, telemetry.hashrateGhs.value)
            ),
        )
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

    fun observeTelemetrySince(minerId: Long, sinceEpochMs: Long): Flow<List<MinerTelemetry>> =
        telemetryDao.observeSince(minerId, sinceEpochMs)
            .map { rows -> rows.map { it.toDomain() } }

    /** Recent hashrate series per miner (normalized point lists) for card sparklines. */
    suspend fun minerSparklines(
        windowMs: Long,
        includeDemo: Boolean,
        points: Int = 24,
    ): Map<Long, List<Double>> {
        val now = System.currentTimeMillis()
        val start = now - windowMs
        val bucketMs = (windowMs / points).coerceAtLeast(1)
        return telemetryDao.fleetSamplesSince(start, includeDemo)
            .groupBy { it.minerId }
            .mapValues { (_, rows) ->
                rows.filter { it.hashrateGhs != null }
                    .groupBy { ((it.timestampEpochMs - start) / bucketMs).toInt().coerceIn(0, points - 1) }
                    .toSortedMap()
                    .map { (_, b) -> b.mapNotNull { it.hashrateGhs }.average() }
            }
            .filterValues { it.size >= 2 }
    }

    /** Fleet-total hashrate trend over [windowMs], bucketed for a compact chart. */
    suspend fun fleetHashrateTrend(
        windowMs: Long,
        includeDemo: Boolean,
        buckets: Int = 60,
    ): List<FleetTrendPoint> {
        val now = System.currentTimeMillis()
        val start = now - windowMs
        return FleetSeries.bucket(telemetryDao.fleetSamplesSince(start, includeDemo), start, now, buckets)
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
