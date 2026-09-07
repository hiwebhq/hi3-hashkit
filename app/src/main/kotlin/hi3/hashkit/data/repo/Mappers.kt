package hi3.hashkit.data.repo

import hi3.hashkit.data.db.MinerEntity
import hi3.hashkit.data.db.TelemetrySampleEntity
import hi3.hashkit.domain.model.FanReading
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerIdentity
import hi3.hashkit.domain.model.MinerStatus
import hi3.hashkit.domain.model.MinerTelemetry
import hi3.hashkit.domain.model.Sourced
import hi3.hashkit.domain.model.ValueSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.time.Instant

@Serializable
internal data class FanJson(val index: Int, val rpm: Int?, val percent: Int?)

internal val fanJson = Json { ignoreUnknownKeys = true }
internal val fanListSerializer = ListSerializer(FanJson.serializer())

fun MinerEntity.toDomain(lastTelemetry: MinerTelemetry?, staleAfterMs: Long, now: Instant): Miner {
    val status = when {
        lastTelemetry == null -> MinerStatus.UNKNOWN
        lastTelemetry.status == MinerStatus.OFFLINE -> MinerStatus.OFFLINE
        now.toEpochMilli() - lastTelemetry.timestamp.toEpochMilli() > staleAfterMs -> MinerStatus.UNKNOWN
        else -> lastTelemetry.status
    }
    return Miner(
        id = id,
        stableKey = stableKey,
        adapterType = adapterType,
        name = name,
        host = host,
        port = port,
        identity = MinerIdentity(
            macAddress = macAddress,
            serialNumber = serialNumber,
            hostname = hostname,
            manufacturer = manufacturer,
            model = model,
            boardVersion = boardVersion,
            asicModel = asicModel,
            firmwareFamily = firmwareFamily,
            firmwareVersion = firmwareVersion,
        ),
        group = groupName,
        location = location,
        notes = notes,
        tags = tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() },
        expectedHashrateGhs = expectedHashrateGhs,
        isDemo = isDemo,
        status = status,
        lastSeenAt = lastSeenAtEpochMs?.let(Instant::ofEpochMilli),
        lastTelemetry = lastTelemetry,
    )
}

fun MinerTelemetry.toEntity(minerId: Long): TelemetrySampleEntity = TelemetrySampleEntity(
    minerId = minerId,
    timestampEpochMs = timestamp.toEpochMilli(),
    status = status.name,
    hashrateGhs = hashrateGhs.value,
    hashrateSource = hashrateGhs.source.name,
    expectedHashrateGhs = expectedHashrateGhs.value,
    powerW = powerW.value,
    powerSource = powerW.source.name,
    efficiencyJTh = efficiencyJTh.value,
    chipTempC = chipTempC.value,
    vrTempC = vrTempC.value,
    fansJson = fanJson.encodeToString(
        fanListSerializer,
        fans.map { FanJson(it.index, it.rpm, it.percent) },
    ),
    frequencyMhz = frequencyMhz.value,
    coreVoltageMv = coreVoltageMv.value,
    inputVoltageMv = inputVoltageMv.value,
    asicCount = asicCount,
    sharesAccepted = sharesAccepted,
    sharesRejected = sharesRejected,
    bestDifficulty = bestDifficulty,
    bestSessionDifficulty = bestSessionDifficulty,
    uptimeSeconds = uptimeSeconds,
    networkDifficulty = networkDifficulty,
    poolUrl = poolUrl,
    poolPort = poolPort,
    workerName = workerName,
    usingFallbackPool = usingFallbackPool,
)

fun TelemetrySampleEntity.toDomain(): MinerTelemetry {
    fun <T> sourced(v: T?, sourceName: String): Sourced<T> =
        if (v == null) Sourced.unavailable()
        else Sourced(v, runCatching { ValueSource.valueOf(sourceName) }.getOrDefault(ValueSource.REPORTED))

    return MinerTelemetry(
        timestamp = Instant.ofEpochMilli(timestampEpochMs),
        status = runCatching { MinerStatus.valueOf(status) }.getOrDefault(MinerStatus.UNKNOWN),
        hashrateGhs = sourced(hashrateGhs, hashrateSource),
        expectedHashrateGhs = Sourced.reported(expectedHashrateGhs),
        powerW = sourced(powerW, powerSource),
        efficiencyJTh = Sourced.calculated(efficiencyJTh),
        chipTempC = Sourced.measured(chipTempC),
        vrTempC = Sourced.measured(vrTempC),
        fans = runCatching {
            fanJson.decodeFromString(fanListSerializer, fansJson).map {
                FanReading(it.index, it.rpm, it.percent)
            }
        }.getOrDefault(emptyList()),
        frequencyMhz = Sourced.reported(frequencyMhz),
        coreVoltageMv = Sourced.reported(coreVoltageMv),
        inputVoltageMv = Sourced.measured(inputVoltageMv),
        asicCount = asicCount,
        sharesAccepted = sharesAccepted,
        sharesRejected = sharesRejected,
        bestDifficulty = bestDifficulty,
        bestSessionDifficulty = bestSessionDifficulty,
        uptimeSeconds = uptimeSeconds,
        networkDifficulty = networkDifficulty,
        poolUrl = poolUrl,
        poolPort = poolPort,
        workerName = workerName,
        usingFallbackPool = usingFallbackPool,
    )
}
