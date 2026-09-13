package hi3.hashkit.data.export

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Builds the fleet operations report as a self-contained HTML document (openable
 * anywhere, printable to PDF). Pure string-building over pre-aggregated inputs so the
 * numbers are unit-testable; all aggregation happens in [Exporter.opsReport].
 */
object OpsReport {

    data class MinerRow(
        val name: String,
        val model: String?,
        /** Share of monitored samples that were online, 0..100; null = no data. */
        val uptimePct: Double?,
        val avgHashrateGhs: Double?,
        val expectedHashrateGhs: Double?,
        val maxChipTempC: Double?,
        val energyKwh: Double?,
        val alertCount: Int,
    )

    data class Inputs(
        val periodDays: Int,
        val generatedAtEpochMs: Long,
        val miners: List<MinerRow>,
        /** Alert counts by type over the period, most frequent first. */
        val alertsByType: List<Pair<String, Int>>,
        val electricityRatePerKwh: Double,
        val currencyCode: String,
    )

    private val esc = mapOf('<' to "&lt;", '>' to "&gt;", '&' to "&amp;")
    private fun e(s: String) = s.map { esc[it] ?: it }.joinToString("")

    private const val GHS_PER_THS = 1000.0
    private const val PERCENT = 100.0

    private fun pct(v: Double?) = v?.let { "%.1f%%".format(it) } ?: "—"
    private fun ths(ghs: Double?) = ghs?.let { "%.2f TH/s".format(it / GHS_PER_THS) } ?: "—"
    private fun temp(c: Double?) = c?.let { "%.0f°C".format(it) } ?: "—"
    private fun kwh(v: Double?) = v?.let { "%.1f kWh".format(it) } ?: "—"

    fun html(inputs: Inputs): String {
        val date = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault())
            .format(Instant.ofEpochMilli(inputs.generatedAtEpochMs))
        val fleetKwh = inputs.miners.mapNotNull { it.energyKwh }.takeIf { it.isNotEmpty() }?.sum()
        val fleetCost = fleetKwh?.let { it * inputs.electricityRatePerKwh }
        val fleetAvgGhs = inputs.miners.mapNotNull { it.avgHashrateGhs }.takeIf { it.isNotEmpty() }?.sum()
        val uptimes = inputs.miners.mapNotNull { it.uptimePct }
        val fleetUptime = uptimes.takeIf { it.isNotEmpty() }?.average()
        val totalAlerts = inputs.miners.sumOf { it.alertCount }

        val rows = inputs.miners.joinToString("\n") { m ->
            val attain = if (m.avgHashrateGhs != null && m.expectedHashrateGhs != null && m.expectedHashrateGhs > 0)
                "%.0f%%".format(m.avgHashrateGhs / m.expectedHashrateGhs * PERCENT) else "—"
            "<tr><td>${e(m.name)}</td><td>${e(m.model ?: "—")}</td><td>${pct(m.uptimePct)}</td>" +
                "<td>${ths(m.avgHashrateGhs)}</td><td>$attain</td><td>${temp(m.maxChipTempC)}</td>" +
                "<td>${kwh(m.energyKwh)}</td><td>${m.alertCount}</td></tr>"
        }
        val alertRows = if (inputs.alertsByType.isEmpty()) "<li>No alerts in this period 🎉</li>"
        else inputs.alertsByType.joinToString("\n") { (type, n) -> "<li>${e(type)}: $n</li>" }

        return """
<title>Hi3 Hashkit — Fleet Report</title>
<style>
  body { font-family: -apple-system, Roboto, sans-serif; margin: 24px; color: #1a2733; }
  h1 { color: #2a78d6; margin-bottom: 0; } .sub { color: #667; margin-top: 4px; }
  table { border-collapse: collapse; width: 100%; margin-top: 12px; }
  th, td { text-align: left; padding: 6px 10px; border-bottom: 1px solid #dde; font-size: 14px; }
  th { color: #667; font-weight: 600; font-size: 12px; text-transform: uppercase; }
  .tiles { display: flex; gap: 16px; flex-wrap: wrap; margin: 16px 0; }
  .tile { border: 1px solid #dde; border-radius: 10px; padding: 10px 16px; }
  .tile b { display: block; font-size: 20px; } .tile span { color: #667; font-size: 12px; }
</style>
<h1>Hi3 Hashkit — Fleet Report</h1>
<p class="sub">Last ${inputs.periodDays} days · generated $date · ${inputs.miners.size} miners</p>
<div class="tiles">
  <div class="tile"><b>${pct(fleetUptime)}</b><span>avg monitored uptime</span></div>
  <div class="tile"><b>${ths(fleetAvgGhs)}</b><span>avg fleet hashrate</span></div>
  <div class="tile"><b>${kwh(fleetKwh)}</b><span>energy used</span></div>
  <div class="tile"><b>${fleetCost?.let { "%,.2f %s".format(it, inputs.currencyCode) } ?: "—"}</b><span>energy cost @ ${"%.4f".format(inputs.electricityRatePerKwh)}/kWh</span></div>
  <div class="tile"><b>$totalAlerts</b><span>alerts raised</span></div>
</div>
<table>
  <tr><th>Miner</th><th>Model</th><th>Uptime</th><th>Avg rate</th><th>vs expected</th><th>Max temp</th><th>Energy</th><th>Alerts</th></tr>
  $rows
</table>
<h3>Alerts by type</h3>
<ul>$alertRows</ul>
<p class="sub">Uptime is measured while the app was monitoring; energy integrates reported power draw.
Generated on-device by Hi3 Hashkit — no data leaves your phone.</p>
"""
    }
}
