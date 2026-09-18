package hi3.hashkit.adapters.futurebit

import hi3.hashkit.adapters.futurebit.ApolloGraphQlClient.Authed
import hi3.hashkit.adapters.futurebit.ApolloGraphQlClient.Response
import hi3.hashkit.domain.adapter.MinerAdapter
import hi3.hashkit.domain.adapter.MinerHost
import hi3.hashkit.domain.adapter.ProbeResult
import hi3.hashkit.domain.adapter.TelemetryResult
import hi3.hashkit.domain.model.MinerCapabilities
import hi3.hashkit.domain.model.MinerIdentity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitoring adapter for FutureBit Apollo miners running Apollo OS (Apollo II, and Apollo
 * BTC units upgraded to the GraphQL dashboard).
 *
 * Why not the cgminer path: Apollo OS does not expose the cgminer TCP API on the LAN
 * (port 4028 refuses connections unless the dashboard's "API allow" setting is turned
 * on), so the generic cgminer adapter never sees these units. Its own API is GraphQL on
 * port 5000 and requires the dashboard password for everything except `Auth.status`.
 *
 * Discovery therefore works without credentials (the probe fingerprints `Auth.status`),
 * but telemetry needs the password saved as the miner's credential. Until it is, polls
 * report the miner OFFLINE with a reason that says so.
 *
 * Controls (`Miner.restart/start/stop`, `Mcu.reboot`, `Settings.update`, `Pool.updateAll`)
 * exist in the schema but have not been exercised against real hardware, so this adapter
 * is read-only.
 */
@Singleton
class ApolloAdapter @Inject constructor(
    private val api: ApolloGraphQlClient,
) : MinerAdapter {

    override val adapterType: String = TYPE
    override val displayName: String = "FutureBit Apollo (Apollo OS)"
    override val defaultPort: Int = ApolloGraphQlClient.DEFAULT_PORT

    override suspend fun probe(host: MinerHost): ProbeResult = withContext(Dispatchers.IO) {
        val target = withPort(host)
        when (val r = api.query(target, STATUS_QUERY)) {
            is Response.Ok -> {
                val status = r.data.obj("Auth")?.obj("status")?.obj("result")?.str("status")
                if (status == null) return@withContext ProbeResult.NotThisDevice
                // With a saved password (re-probe of a known miner) prefer the full identity.
                val identity = if (!target.secret.isNullOrBlank()) {
                    fetchParsed(target)?.identity ?: ApolloParser.probeIdentity()
                } else {
                    ApolloParser.probeIdentity()
                }
                ProbeResult.Supported(TYPE, identity, r.body)
            }
            is Response.HttpError -> ProbeResult.NotThisDevice
            is Response.NetworkError -> ProbeResult.Unreachable(r.cause)
        }
    }

    override suspend fun getIdentity(host: MinerHost): MinerIdentity? =
        (probe(host) as? ProbeResult.Supported)?.identity

    override suspend fun getTelemetry(host: MinerHost): TelemetryResult = withContext(Dispatchers.IO) {
        val target = withPort(host)
        when (val r = api.authedQuery(target, STATS_QUERY)) {
            is Authed.Ok -> {
                val parsed = ApolloParser.parse(r.data, target.host)
                    ?: return@withContext TelemetryResult.ParseError("No miner data in Apollo response", r.body)
                if (parsed.telemetry.status == hi3.hashkit.domain.model.MinerStatus.OFFLINE) {
                    TelemetryResult.Offline("Apollo reports its miner process is stopped")
                } else {
                    TelemetryResult.Success(parsed.telemetry, r.body)
                }
            }
            Authed.NeedsPassword -> TelemetryResult.Offline(NEEDS_PASSWORD_REASON)
            is Authed.AuthFailed -> TelemetryResult.Offline("Apollo rejected the saved password: ${r.message}")
            is Authed.HttpError -> TelemetryResult.Offline("HTTP ${r.code}")
            is Authed.NetworkError -> TelemetryResult.Offline(r.cause)
        }
    }

    override fun getCapabilities(identity: MinerIdentity?): MinerCapabilities =
        MinerCapabilities.monitoringOnly(
            "Apollo OS controls (restart, pause, mode, pools) are not yet verified against " +
                "real hardware; monitoring shows hashrate, power, temps, fans, shares and pool.",
        )

    private suspend fun fetchParsed(host: MinerHost): ApolloParser.Parsed? =
        (api.authedQuery(host, STATS_QUERY) as? Authed.Ok)?.let { ApolloParser.parse(it.data, host.host) }

    /** Callers that stored port 0 / a non-Apollo port get the dashboard API port. */
    private fun withPort(host: MinerHost): MinerHost =
        if (host.port > 0) host else host.copy(port = defaultPort)

    companion object {
        const val TYPE = "futurebit-apollo"

        const val NEEDS_PASSWORD_REASON =
            "Apollo OS needs its dashboard password to read stats — save it under Miner login."

        /** Open without a token; a non-Apollo host answers 404 or a body without `Auth`. */
        const val STATUS_QUERY = "{ Auth { status { result { status } error { type message } } } }"

        /** Everything the parser reads, in one round trip (all roots need the token). */
        val STATS_QUERY: String = """
            {
              Miner {
                online { result { online { timestamp status } } error { type message } }
                stats {
                  result {
                    stats {
                      uuid version date comport statVersion
                      versions { miner minerDate mspVer }
                      master {
                        upTime diff boards errorSpi osc hwAddr boardsI boardsW wattPerGHs
                        intervals {
                          int_0 { name interval bySol byDiff byPool byJobs solutions errors errorRate chipSpeed chipRestarts }
                          int_30 { name interval bySol byDiff byPool byJobs solutions errors errorRate chipSpeed chipRestarts }
                          int_300 { name interval bySol byDiff byPool byJobs solutions errors errorRate chipSpeed chipRestarts }
                          int_900 { name interval bySol byDiff byPool byJobs solutions errors errorRate chipSpeed chipRestarts }
                          int_3600 { name interval bySol byDiff byPool byJobs solutions errors errorRate chipSpeed chipRestarts }
                        }
                      }
                      pool {
                        host port userName diff
                        intervals { int_0 { name interval jobs sharesSent sharesAccepted sharesRejected solutionsAccepted avgRespTime shareLoss inService reconnections staleJobShares duplicateShares lowDifficultyShares } }
                      }
                      fans { int_0 { rpm } }
                      temperature { count min avr max }
                      slots {
                        int_0 {
                          revision spiNum spiLen pwrNum pwrLen btcNum specVoltage chips pwrOn pwrOnTarget
                          temperature temperature1 ocp heaterErr overheats overheatsTime lowCurrRst currents
                          brokenPwc solutions errors ghs errorRate chipRestarts wattPerGHs osc oscStopChip
                        }
                      }
                      slaves { id uid ver rx err time ping }
                    }
                  }
                  error { type message }
                }
              }
              Mcu {
                version { result error { type message } }
                stats {
                  result {
                    stats {
                      timestamp hostname operatingSystem uptime loadAverage architecture
                      temperature minerTemperature minerFanSpeed
                      network { name address mac }
                      memory { total available used }
                      cpu { threads usedPercent }
                    }
                  }
                  error { type message }
                }
              }
              Pool { list { result { pools { id enabled donation url username index } } error { type message } } }
              Settings { read { result { settings { minerMode voltage frequency fan fan_low fan_high apiAllow } } error { type message } } }
            }
        """.trimIndent()
    }
}
