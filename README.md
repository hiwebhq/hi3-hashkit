# Hi3 Hashkit

**Current version: 0.30.0**

Hi3 Hashkit is a privacy-first, local-first Android app for monitoring and safely
controlling your Bitcoin miners. It talks only to the miners on your own network — over
LAN or your Tailscale VPN — with no account and no cloud. Every reading is labeled by how
it was obtained (measured, reported, calculated, estimated, or unavailable), so an
estimate is never dressed up as a fact.

See `docs/` for architecture, device support, build, and security details, and
`CHANGELOG.md` for release-by-release notes.

## Key features

- **Auto-discovery** — scans your local subnet automatically at launch (and on demand),
  so miners appear without manual entry. Add by IP or CIDR too.
- **Multiple farms/sites** — group miners into farms, each with its own scan subnet; mark
  one as default and switch the dashboard between sites.
- **Live fleet dashboard** — hashrate, temps, power, efficiency and health per miner, with
  fleet totals, trend charts, per-miner sparklines, search, and Large/Medium/Compact/Grid
  layouts.
- **Safe controls where verified** — reboot, pool change, fan and firmware-bounded tuning
  with confirmations, bounds and rollback; bulk actions across selected miners; scheduling.
- **Honest history** — telemetry stored locally (Room), configurable retention, with a
  raw-response viewer (redacted) for diagnostics.
- **Alerts & health** — thresholds for low hashrate, hot chips/VR and reject rate, with
  per-miner overrides and notification channels.
- **3D Flow view**, home-screen widget, biometric/PIN app lock, System/Dark/Light themes,
  and an optional, off-by-default read-only view of your own pool.hi3.cc / mmp.hi3.cc.
- **Local-first & private** — nothing leaves the device except requests to your configured
  miners (and opt-in Hi3 Pool/MMP if you enable them).

## Supported miners

Every miner API is verified against real hardware before it ships; unverified controls are
shown as unsupported, never guessed. Full matrix in `docs/DEVICE_MATRIX.md`.

| Firmware / device | Monitoring | Controls |
|---|---|---|
| ESP-Miner / AxeOS (Bitaxe Ultra/Supra/Gamma/…) | Full | Reboot, pool, fan, bounded tuning |
| NerdQAxe, Lucky-Miner-style ESP-Miner forks | Full | Monitoring-only (unverified) |
| Canaan Avalon Nano 3 | Full | Pause/Resume, Reboot |
| Canaan Nano 3S / Avalon Q | Monitoring (compat) | — |
| Braiins OS (BMM 100) | Full (no power sensor) | — |
| Stock Bitmain / BMMiner (Antminer S21 Pro, S-series) | Hashrate, expected, chip temps, fans, freq, ASIC count | — (power not in API) |
| VNish (Antminer S21 Pro forks) | Full incl. **wall power** & efficiency | — |
| LuxOS | Basic (hashrate, shares, uptime, pool) | — |
| Demo | Synthetic (demo mode only) | — |

## Requirements

- Android 8.0 (API 26) or newer.
- The miners reachable on your LAN, or over Tailscale (advertise the subnet route for
  remote sites).
- Sideload the APK (not on the Play Store); ~3.3 MB signed release.

## Quick start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
./gradlew test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install: `adb install app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to
the phone and open it (enable "install unknown apps" for your file manager).

## Privacy behavior

- No data leaves your device except requests to the miner IPs you configure, plus
  three strictly opt-in, read-only integrations (all off by default): a
  network-difficulty fetch, Hi3 Pool stats keyed by your payout address, and the
  Hi3 MMP fleet view authenticated by an API key stored via the Android Keystore
  (see docs/SECURITY.md for exactly what each transmits).
- The phone never uploads miner data anywhere — MMP agent functionality remains a
  disabled placeholder.
- Polling runs while the app is open; optional background monitoring (WorkManager, ~15-min
  Android minimum) is off by default and enabled in Settings.
