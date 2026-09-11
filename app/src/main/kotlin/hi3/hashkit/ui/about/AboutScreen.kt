package hi3.hashkit.ui.about

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import hi3.hashkit.ui.theme.HiBrand

/** How-to steps shown on the About screen. */
private data class HowToStep(val title: String, val body: String)

/** A supported-miner family row: the family, the models it covers, and support level. */
private data class SupportedMiner(val family: String, val models: String, val support: String)

private val SUPPORTED_MINERS = listOf(
    SupportedMiner(
        "Bitaxe / ESP-Miner (AxeOS)",
        "Max (202), Ultra (204), Supra (400), Gamma (600), Gamma Turbo, Supra Hex",
        "Full monitoring + safe controls: reboot, pool, fan, firmware-bounded tuning + autotune.",
    ),
    SupportedMiner(
        "NerdQAxe (ESP-Miner fork)",
        "NerdQAxe++, NerdQAxe+, NerdQAxe+ Hydro, Lucky-Miner-style forks",
        "Full monitoring; controls stay off until verified on that firmware.",
    ),
    SupportedMiner(
        "Antminer — stock Bitmain (BMMiner)",
        "S21 Pro (verified); S21+, S21+ Hyd, S21 Hyd, S21 XP, S19 series, S17 series (compat-gated)",
        "Hashrate, chip temps, fans, frequency, expected, ASIC count, shares, uptime, pool. No controls; power isn't in this API.",
    ),
    SupportedMiner(
        "Antminer — VNish firmware",
        "S21 Pro (verified) and other VNish-flashed Antminers",
        "Full monitoring including wall power and efficiency (J/TH). No controls.",
    ),
    SupportedMiner(
        "Antminer — LuxOS firmware",
        "LuxOS-flashed Antminers",
        "Basic monitoring: hashrate, shares, uptime, pool.",
    ),
    SupportedMiner(
        "Canaan Avalon",
        "Nano 3 (verified); Nano 3S, Avalon Q, Avalon Mini 3 (compat-gated)",
        "Nano 3: monitoring + Pause/Resume/Reboot. Others: monitoring over the CGMiner API.",
    ),
    SupportedMiner(
        "Braiins OS (BOSer)",
        "Braiins Mini Miner BMM 100 (verified)",
        "Full monitoring over the CGMiner API. No power sensor on the unit → power unavailable.",
    ),
    SupportedMiner(
        "WhatsMiner (MicroBT)",
        "M2X / M3X / M5X / M6X families (compat-gated)",
        "Hashrate, shares, uptime, pool, chip temp, fans, and power where reported. No controls.",
    ),
    SupportedMiner(
        "Demo",
        "Synthetic miners",
        "Demo mode only — never mixed with real miners.",
    ),
)

private val HOW_TO = listOf(
    HowToStep(
        "1 · Get on the same network",
        "Connect this phone to the Wi-Fi/LAN your miners are on — or bring up Tailscale " +
            "and advertise the site's subnet route to reach them remotely.",
    ),
    HowToStep(
        "2 · Let it discover your miners",
        "Hi3 Hashkit scans your local subnet automatically at launch, so miners usually " +
            "appear on their own. Open the ⋮ menu → Network scan to see your IP, change the " +
            "range, or start/stop a scan. Nothing is added but devices that answer a known " +
            "miner API.",
    ),
    HowToStep(
        "3 · Or add one by hand",
        "Tap + on the dashboard to add a miner by IP/hostname, or run a scan of a CIDR. " +
            "Only private LAN and Tailscale addresses are allowed.",
    ),
    HowToStep(
        "4 · Organize into farms",
        "Use ⋮ → Farms to group miners by site, each with its own scan subnet. Mark one as " +
            "the default (it opens on launch) and switch the dashboard between farms with the " +
            "chip at the top. Adding a farm offers an immediate scan of its subnet.",
    ),
    HowToStep(
        "5 · Read the dashboard honestly",
        "Every value is tagged by source — measured, reported, calculated, estimated or " +
            "unavailable — so an estimate is never shown as fact. Tap a miner for live " +
            "telemetry, history charts and the raw response.",
    ),
    HowToStep(
        "6 · Control safely (where verified)",
        "On supported firmware you can reboot, change the pool, adjust fans and apply " +
            "firmware-bounded tuning — each with a confirmation and rollback. Long-press cards " +
            "to select several and run bulk actions; a preview shows which devices support each.",
    ),
    HowToStep(
        "7 · Turn on alerts when ready",
        "Alerts are off by default. Enable them in Setup → Alerts to get offline, temperature, " +
            "fan and reject-rate notifications with recovery notices, and set per-miner overrides.",
    ),
    HowToStep(
        "8 · Stay private",
        "Everything stays on this device. The Hi3 Pool and MMP views are optional, read-only, " +
            "and off by default.",
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
    fun open(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
    // Open the bundled user guide: copy the asset into the FileProvider's cache dir, then hand it
    // to whatever PDF viewer the user has. The PDF ships in the app — no network needed.
    fun openGuide() {
        runCatching {
            val outDir = java.io.File(context.cacheDir, "exports").apply { mkdirs() }
            val out = java.io.File(outDir, "USER_GUIDE.pdf")
            context.assets.open("USER_GUIDE.pdf").use { input ->
                out.outputStream().use { input.copyTo(it) }
            }
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context, "${context.packageName}.files", out,
            )
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/pdf")
                    addFlags(
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_ACTIVITY_NEW_TASK,
                    )
                },
            )
        }.onFailure {
            android.widget.Toast.makeText(
                context, "Couldn't open the guide — no PDF viewer installed?",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    fun shareApp() {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "${HiBrand.appName} — local-first Bitcoin miner dashboard")
            putExtra(
                android.content.Intent.EXTRA_TEXT,
                "Check out ${HiBrand.appName}, a local-first Android app for monitoring and " +
                    "safely controlling Bitcoin miners: https://mmp.hi3.cc/hashkit",
            )
        }
        runCatching {
            context.startActivity(
                android.content.Intent.createChooser(send, "Share ${HiBrand.appName}")
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = HiBrand.background),
            )
        },
        containerColor = HiBrand.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Section("ABOUT") {
                    Text(
                        "${HiBrand.appName}  ·  v$version",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Hi3 Hashkit is a local-first Android dashboard for Bitcoin miners: it " +
                            "discovers, monitors, and safely controls your fleet on your own " +
                            "network — no account, no cloud, your data stays on your device.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    LinkRow(
                        title = "User guide",
                        subtitle = "How to use Hi3 Hashkit (PDF, offline)",
                        linkLabel = "Open PDF",
                        highlight = true,
                    ) { openGuide() }
                    LinkRow(
                        title = "Hi3",
                        subtitle = "The Hi3 mining platform",
                        linkLabel = "hi3.cc",
                        highlight = false,
                    ) { open("https://www.hi3.cc") }
                    LinkRow(
                        title = "Bitcoin pool services",
                        subtitle = "Solo, PPLNS and TIDES payouts on Hi3 Pool",
                        linkLabel = "pool.hi3.cc",
                        highlight = true,
                    ) { open("https://pool.hi3.cc") }
                    LinkRow(
                        title = "Help & support",
                        subtitle = "Assistance with this app and fleet management",
                        linkLabel = "mmp.hi3.cc",
                        highlight = false,
                    ) { open("https://mmp.hi3.cc") }
                    LinkRow(
                        title = "Share app",
                        subtitle = "Send a friend the download link",
                        linkLabel = "Share",
                        highlight = false,
                    ) { shareApp() }
                    LinkRow(
                        title = "Feature request",
                        subtitle = "Suggest an improvement or report an issue",
                        linkLabel = "hi3.cc/contact",
                        highlight = false,
                    ) { open("https://hi3.cc/contact") }
                }
            }
            item {
                Section("SUPPORTED MINERS") {
                    Text(
                        "Support is by firmware/API family, so every model in a family works — " +
                            "you don't need each one. \"Verified\" = confirmed on real hardware; " +
                            "\"compat-gated\" = same firmware API, pending confirmation on that model " +
                            "(basic telemetry is safe). Unverified controls are never guessed.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HiBrand.textSecondary,
                    )
                    SUPPORTED_MINERS.forEach { m -> SupportedMinerRow(m) }
                }
            }
            item {
                Section("HOW TO USE") {
                    HOW_TO.forEach { step -> HowToRow(step) }
                }
            }
        }
    }
}

@Composable
private fun SupportedMinerRow(m: SupportedMiner) {
    Column(Modifier.fillMaxWidth()) {
        Text(m.family, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
        Text(m.models, style = MaterialTheme.typography.labelSmall, color = HiBrand.accent)
        Text(m.support, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
    }
}

@Composable
private fun HowToRow(step: HowToStep) {
    Column(Modifier.fillMaxWidth()) {
        Text(step.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = HiBrand.textPrimary)
        Text(step.body, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = HiBrand.surface),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(Modifier.padding(14.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
            content()
        }
    }
}

@Composable
private fun LinkRow(
    title: String,
    subtitle: String,
    linkLabel: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (highlight) FontWeight.SemiBold else FontWeight.Normal,
                color = if (highlight) HiBrand.accent else MaterialTheme.colorScheme.onSurface,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = HiBrand.textSecondary)
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "$linkLabel ↗",
            style = MaterialTheme.typography.labelLarge,
            color = HiBrand.accent,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
