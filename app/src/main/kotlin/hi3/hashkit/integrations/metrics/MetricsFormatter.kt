package hi3.hashkit.integrations.metrics

import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus

/**
 * Renders the fleet as Prometheus text exposition format (v0.0.4). Pure and testable.
 * Only totals and per-miner gauges are exposed — no addresses, credentials, or worker data.
 */
object MetricsFormatter {

    fun render(miners: List<Miner>): String {
        val real = miners.filter { !it.isDemo }
        val sb = StringBuilder()

        fun help(name: String, help: String, type: String) {
            sb.append("# HELP ").append(name).append(' ').append(help).append('\n')
            sb.append("# TYPE ").append(name).append(' ').append(type).append('\n')
        }

        // --- Fleet aggregates ---
        val live = real.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val totalHash = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val totalPower = live.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 }
        help("hi3_fleet_hashrate_ghs", "Total fleet hashrate in GH/s", "gauge")
        sb.append("hi3_fleet_hashrate_ghs ").append(num(totalHash)).append('\n')
        help("hi3_fleet_power_watts", "Total fleet power in watts", "gauge")
        sb.append("hi3_fleet_power_watts ").append(num(totalPower)).append('\n')
        help("hi3_fleet_miners_online", "Number of miners online", "gauge")
        sb.append("hi3_fleet_miners_online ").append(real.count { it.status == MinerStatus.ONLINE }).append('\n')
        help("hi3_fleet_miners_total", "Number of tracked miners", "gauge")
        sb.append("hi3_fleet_miners_total ").append(real.size).append('\n')

        // --- Per-miner gauges ---
        help("hi3_miner_hashrate_ghs", "Miner hashrate in GH/s", "gauge")
        real.forEach { m ->
            m.lastTelemetry?.hashrateGhs?.value?.let {
                sb.append("hi3_miner_hashrate_ghs").append(labels(m)).append(' ').append(num(it)).append('\n')
            }
        }
        help("hi3_miner_power_watts", "Miner power in watts", "gauge")
        real.forEach { m ->
            m.lastTelemetry?.powerW?.value?.let {
                sb.append("hi3_miner_power_watts").append(labels(m)).append(' ').append(num(it)).append('\n')
            }
        }
        help("hi3_miner_chip_temp_celsius", "Miner hottest chip temperature", "gauge")
        real.forEach { m ->
            m.lastTelemetry?.chipTempC?.value?.let {
                sb.append("hi3_miner_chip_temp_celsius").append(labels(m)).append(' ').append(num(it)).append('\n')
            }
        }
        help("hi3_miner_efficiency_jth", "Miner efficiency in J/TH", "gauge")
        real.forEach { m ->
            m.lastTelemetry?.efficiencyJTh?.value?.let {
                sb.append("hi3_miner_efficiency_jth").append(labels(m)).append(' ').append(num(it)).append('\n')
            }
        }
        help("hi3_miner_up", "1 if the miner is online, else 0", "gauge")
        real.forEach { m ->
            val up = if (m.status == MinerStatus.ONLINE || m.status == MinerStatus.DEGRADED) 1 else 0
            sb.append("hi3_miner_up").append(labels(m)).append(' ').append(up).append('\n')
        }

        return sb.toString()
    }

    private fun labels(m: Miner): String =
        "{id=\"${m.id}\",miner=\"${escape(m.name)}\"}"

    private fun escape(s: String): String =
        s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")

    /** Prometheus wants a dot-decimal number; avoid locale commas and scientific notation. */
    private fun num(v: Double): String = "%.3f".format(java.util.Locale.US, v)
}
