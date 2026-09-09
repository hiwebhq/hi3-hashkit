package hi3.hashkit.integrations.metrics

import hi3.hashkit.core.Units
import hi3.hashkit.domain.model.Miner
import hi3.hashkit.domain.model.MinerStatus

/**
 * Renders the fleet as a self-contained, read-only HTML page for the local web dashboard —
 * so any browser or TV on the LAN can view fleet status without the app. No external
 * resources (inline CSS, meta-refresh), no controls, and only the same aggregate/per-miner
 * data the app already shows. Pure and testable.
 */
object HtmlDashboard {

    fun render(miners: List<Miner>, refreshSeconds: Int = 10): String {
        val real = miners.filter { !it.isDemo }
        val live = real.filter { it.status == MinerStatus.ONLINE || it.status == MinerStatus.DEGRADED }
        val totalHash = live.sumOf { it.lastTelemetry?.hashrateGhs?.value ?: 0.0 }
        val totalPower = live.sumOf { it.lastTelemetry?.powerW?.value ?: 0.0 }
        val online = real.count { it.status == MinerStatus.ONLINE }

        val rows = real.sortedBy { it.name.lowercase() }.joinToString("\n") { m ->
            val t = m.lastTelemetry
            val color = statusColor(m.status)
            val hr = if (m.status == MinerStatus.OFFLINE) "offline" else Units.formatHashrate(t?.hashrateGhs?.value)
            """
            <tr>
              <td><span class="dot" style="background:$color"></span>${esc(m.name)}</td>
              <td>${esc(m.host)}</td>
              <td>${esc(m.identity.model ?: "—")}</td>
              <td class="num">${esc(hr)}</td>
              <td class="num">${Units.formatPower(t?.powerW?.value)}</td>
              <td class="num">${t?.chipTempC?.value?.let { "${it.toInt()}°C" } ?: "—"}</td>
              <td class="num">${t?.efficiencyJTh?.value?.let { "%.1f".format(it) } ?: "—"}</td>
              <td>${esc(t?.poolUrl?.substringAfter("//") ?: "—")}</td>
            </tr>
            """.trimIndent()
        }

        return """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1">
          <meta http-equiv="refresh" content="$refreshSeconds">
          <title>Hi3 Hashkit — Fleet</title>
          <style>
            :root { color-scheme: dark; }
            body { margin:0; background:#0B0F14; color:#E8EEF4;
                   font-family:-apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif; }
            .wrap { max-width:1100px; margin:0 auto; padding:24px; }
            h1 { font-size:44px; margin:0; color:#E8EEF4; }
            .sub { color:#93A3B4; margin:4px 0 20px; font-size:16px; }
            .accent { color:#3987E5; }
            table { width:100%; border-collapse:collapse; }
            th,td { text-align:left; padding:10px 12px; border-bottom:1px solid #1A222D; }
            th { color:#93A3B4; font-weight:600; font-size:13px; text-transform:uppercase; }
            td { font-size:15px; }
            td.num, th.num { text-align:right; font-variant-numeric:tabular-nums; }
            .dot { display:inline-block; width:10px; height:10px; border-radius:50%; margin-right:8px; vertical-align:middle; }
            .foot { color:#5A6B7B; font-size:12px; margin-top:16px; }
          </style>
        </head>
        <body>
          <div class="wrap">
            <h1 class="accent">${esc(Units.formatHashrate(totalHash))}</h1>
            <div class="sub">$online online · ${real.size} miners · ${Units.formatPower(totalPower)} total</div>
            <table>
              <thead><tr>
                <th>Miner</th><th>IP</th><th>Model</th>
                <th class="num">Hashrate</th><th class="num">Power</th>
                <th class="num">Temp</th><th class="num">J/TH</th><th>Pool</th>
              </tr></thead>
              <tbody>
              $rows
              </tbody>
            </table>
            <div class="foot">Hi3 Hashkit local dashboard · read-only · refreshes every ${refreshSeconds}s</div>
          </div>
        </body>
        </html>
        """.trimIndent().trim()
    }

    private fun statusColor(status: MinerStatus): String = when (status) {
        MinerStatus.ONLINE -> "#2BD97C"
        MinerStatus.DEGRADED -> "#F0B429"
        MinerStatus.OFFLINE -> "#EF5350"
        MinerStatus.UNKNOWN -> "#78909C"
    }

    private fun esc(s: String): String = s
        .replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;")
}
