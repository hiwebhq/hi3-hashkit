# Hi3 Hashkit

**Current version: 0.64.0**

Hi3 Hashkit is a privacy-first, local-first Android app for monitoring and safely
controlling your Bitcoin miners. It talks only to the miners on your own network — over
LAN or your Tailscale VPN — with no account and no cloud. Every reading is labeled by how
it was obtained (measured, reported, calculated, estimated, or unavailable), so an
estimate is never dressed up as a fact.

See `docs/` for architecture, device support, build, and security details, and
`CHANGELOG.md` for release-by-release notes.

## Key features

**Monitoring & insight**
- **Auto-discovery** — scans your local subnet at launch and on demand (plus **mDNS/DNS-SD**
  discovery), so miners appear without manual entry. Add by IP or CIDR too.
- **Live fleet dashboard** — hashrate, temps, power, efficiency and health per miner, with
  fleet totals, trend charts, per-miner sparklines, search, and Large/Medium/Compact/Grid
  layouts.
- **Sortable fleet table** — a dense, spreadsheet-style view (name·IP·model·hashrate·temp·
  fan·pool·uptime·J/TH) with tap-to-sort columns and a filter box.
- **Per-chip / per-chain health** — for Antminer-class miners, a per-board table with
  hashrate, working/dead chip counts, hardware errors and hottest-chip temp.
- **Efficiency (J/TH) trend chart** and **statistical anomaly detection** (gradual hashrate
  drift / rising reject rate) that a fixed threshold misses.
- **Profitability & energy** — estimated revenue, power cost, net, BTC/day, kWh/day and
  heat, plus a **Bitcoin network card** (block height, subsidy, next difficulty adjustment
  and halving countdown).
- **Multiple farms/sites**, a **rack/site layout view**, a **wall / TV kiosk mode**
  (Small/Medium/Large), a 3D **Flow view**, and a home-screen widget.

**Control & automation (where verified)**
- **Safe controls** — reboot, pool change, fan and firmware-bounded tuning, each with
  confirmations, bounds and rollback; bulk actions; a fleet-wide **Panic** (pause/reboot all).
- **Automation rules engine** — "if condition then action" (temp/hashrate/reject/offline →
  pause/resume/reboot/plug on/off/notify), scoped to all miners or a group.
- **Schedules** — time-of-use pause/resume, pool/fan changes, and smart-plug on/off.
- **Auto-recover** — power-cycle a stuck miner's smart plug or reboot it, once, with audit.
- **Bitaxe auto-tuner** — sweeps firmware-approved frequencies under a chip-temp ceiling and
  recommends the best point for efficiency or hashrate, with rollback.
- **Pool & wallet address book** — save pools/wallets and apply one across the fleet in a tap.

**Alerts & integrations**
- **Alerts & health** — thresholds for low hashrate, hot chips/VR, reject rate, fan stall,
  offline, pool disconnect, plug cutoff, firmware-available and solo **block-found**, with
  per-miner overrides, **quiet hours** and a **daily digest**.
- **Push notifications** to your own webhook (ntfy / Gotify / Telegram / generic), plus
  notification quick-actions (Reboot / Acknowledge).
- **Home Assistant / MQTT** publish of fleet + per-miner telemetry to a local broker.
- **Local web dashboard + Prometheus `/metrics`** (Advanced) — a read-only HTML fleet page
  and a Grafana scrape endpoint on your LAN.
- **Wear OS tile** showing fleet hashrate/status, themed to your app accent.

**Platform & privacy**
- **Local-first & private** — nothing leaves the device except requests to your configured
  miners and strictly opt-in, off-by-default integrations (see Privacy below).
- **Encrypted at rest** — per-miner admin passwords/tokens are stored with the Android
  Keystore (AES-256-GCM); Android auto-backup is disabled.
- **Maintenance log** — timestamped per-miner notes with optional photos.
- **UI Theme** (six accent colors), System/Dark/Light modes, biometric/PIN app lock, and an
  Advanced-features gate for future licensing.
- **Android TV** support (leanback), Wear OS companion, and honest measured/estimated labeling.

## Supported miners

Every miner API is verified against real hardware or vendor documentation before it ships;
unverified controls are shown as unsupported, never guessed. Full matrix in
`docs/DEVICE_MATRIX.md`.

| Firmware / device | Monitoring | Controls |
|---|---|---|
| ESP-Miner / AxeOS (Bitaxe Ultra/Supra/Gamma/…) | Full | Reboot, pool, fan, bounded tuning + auto-tuner |
| NerdQAxe | Full | Reboot |
| Lucky-Miner-style ESP-Miner forks | Full | Monitoring-only (unverified) |
| Canaan Avalon Nano 3 | Full | Pause/Resume, Reboot |
| Canaan Nano 3S / Avalon Q / Mini 3 | Monitoring (compat) | Pause/Resume, Reboot (compat) |
| Braiins OS (BMM 100) | Full (no power sensor) | Pause/Resume |
| Stock Bitmain / BMMiner (Antminer S21 Pro, S-series) | Hashrate, expected, per-chain temps, fans, freq, ASIC count | Reboot (web CGI) |
| VNish (Antminer S21 Pro forks) | Full incl. **wall power** & efficiency | Reboot, Pause/Resume |
| LuxOS | Basic (hashrate, shares, uptime, pool) | Pause/Resume |
| WhatsMiner (MicroBT) | Hashrate, shares, temps, fans, power (compat) | — (encrypted token API unverified) |
| FutureBit / generic cgminer (long tail) | Basic (hashrate, shares, uptime, pool) | — (unverified) |
| Demo | Synthetic (demo mode only) | — |

Miners with no power sensor (e.g. stock Bitmain) get **measured wall power** automatically
when a metering smart plug (Tasmota/Shelly/Kasa) is configured for them.

## Requirements

- Android 8.0 (API 26) or newer; optional Wear OS companion and Android TV support.
- The miners reachable on your LAN, or over Tailscale (advertise the subnet route for
  remote sites).
- Sideload the APK (not on the Play Store).

## Quick start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
./gradlew test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install: `adb install app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to
the phone and open it (enable "install unknown apps" for your file manager).

## Privacy behavior

Nothing leaves your device except requests to the miner IPs you configure, plus a set of
**strictly opt-in, off-by-default** integrations (see `docs/SECURITY.md` for exactly what
each transmits):

- **Read-only:** network-difficulty & BTC-price fetch (mempool.space), Hi3 Pool stats keyed
  by your payout address, the Hi3 MMP fleet view (API key stored in the Android Keystore),
  and an AxeOS firmware-update check.
- **Outbound push you configure:** an alert webhook (ntfy/Gotify/Telegram/generic).
- **Local network only:** MQTT publish to a broker you choose, the local web dashboard /
  Prometheus endpoint (Advanced), a small fleet summary to a paired Wear OS watch, and
  smart-plug commands + power reads.

The phone never uploads miner data to us — MMP agent functionality remains a disabled
placeholder. Polling runs while the app is open; optional background monitoring (WorkManager,
~15-min Android minimum) is off by default and enabled in Settings.
