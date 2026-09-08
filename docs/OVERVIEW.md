# Hi3 Hashkit — Overview

**Hi3 Hashkit** is a privacy-first, local-first Android app for monitoring and safely
controlling Bitcoin miners. It talks only to the miners on your own network — over LAN or
your Tailscale VPN — with **no account, no cloud, and no telemetry**. Every reading is
labeled by how it was obtained (measured, reported, calculated, estimated, or unavailable),
so an estimate is never dressed up as a fact.

Current release: **v0.44.0**.

## Key features

### Discovery & organization
- Auto-scans your local subnet at launch, plus manual IP/CIDR add and a dedicated Network
  Scan screen (current IP, editable range, start/stop/restart).
- **Farms/sites** — group miners by location, each with its own scan subnet and refresh
  interval; a default farm and a dashboard farm switcher.

### Monitoring
- Live fleet dashboard: hashrate, temps, power, efficiency, health per miner; fleet totals,
  trend charts, per-miner sparklines, search, and Large/Medium/Compact/Grid layouts.
- Dedicated **Fleet page**, a 3D flow view, a home-screen widget, and honest offline/stale
  states with a redacted raw-response viewer.
- Local history (Room) with configurable retention, downsampling, and CSV export / JSON
  backup-restore.
- Attainment % (actual vs expected hashrate) seeded from a built-in model spec registry
  (public spec-sheet nominals) when the device doesn't report an expected rate.

### Control (only where verified against real firmware)
- Reboot, pool change, fan control, and firmware-bounded tuning with confirmations and
  rollback; bulk actions across selected miners. Per-miner admin passwords/tokens are
  stored encrypted (Android Keystore) and used only in-memory to authenticate a control.
- **Efficiency autotuner** (Bitaxe): sweeps approved frequencies, measures J/TH, recommends
  the most efficient setpoint (apply on tap; restores the original).
- **Time-of-use scheduling**: auto-pause at peak-rate hours, resume off-peak.

### Advanced / operator
- **Efficiency leaderboard** — rank by J/TH, attainment, or hashrate.
- **Smart-plug over-temp safety cutoff** — per-miner, via Tasmota / Shelly / Kasa / generic
  webhook; cuts power when chip temp exceeds your limit (manual restore only). Backed by a
  20-second safety poll and an **opt-in always-on foreground service** that survives
  app-close and reboot.
- **Firmware-update awareness** (opt-in) — flags Bitaxes behind the latest AxeOS release.
- Alerts & health scoring (offline, temp, fan, reject-rate) with per-miner overrides;
  alerts are off by default.
- Biometric/PIN app lock, System/Dark/Light themes, and a step-by-step in-app how-to.

### Pool stats (opt-in, per-rig correlation)
Select your pool and the dashboard correlates pool-side workers with your local miners.
Each is verified against its real API; the payout address is QR-scannable.

| Pool | Identifier | Endpoint |
|---|---|---|
| Hi3 Pool | Payout address | `/api/client/{address}` (+ stratum-proxy sessions) |
| Public Pool | Payout address | `/api/client/{address}` |
| CKPool | Payout address | `raw.stats.ckpool.org/users/{address}` |
| OCEAN | Address / username | `api.ocean.xyz/v1/user_hashrate_full/{address}` |
| F2Pool | Mining account | `api.f2pool.com/bitcoin/{account}` |
| Braiins Pool | Access token | `pool.braiins.com/accounts/workers/json/btc` (`Pool-Auth-Token`) |
| Luxor, NiceHash | — | Coming soon (shown but contacts nothing) |

## Supported miners

Every miner API is verified against real hardware before it ships; unverified controls are
shown as unsupported, never guessed.

| Firmware / device | Monitoring | Controls |
|---|---|---|
| ESP-Miner / AxeOS (Bitaxe Ultra/Supra/Gamma/…) | Full | Reboot, pool, fan, bounded tuning, autotune |
| NerdQAxe, Lucky-Miner-style ESP-Miner forks | Full | Reboot (NerdQAxe) |
| Canaan Avalon Nano 3 | Full | Pause/Resume, Reboot |
| Canaan Nano 3S / Avalon Q | Monitoring (compat) | — |
| Braiins OS (BMM 100) | Full (no power sensor) | Pause/Resume |
| Stock Bitmain / BMMiner (Antminer S21 Pro, S-series) | Hashrate, expected, chip temps, fans, freq, ASIC count | Reboot (needs root password) |
| VNish (Antminer S21 Pro forks) | Full incl. wall power & efficiency | Reboot, Pause/Resume |
| LuxOS | Basic (hashrate, shares, uptime, pool) | Pause/Resume |
| WhatsMiner (MicroBT M2X–M6X) | Hashrate, shares, uptime, pool, chip temp, fans, power* | — (encrypted token API) |
| FutureBit Apollo BTC | Hashrate, shares, uptime, pool | — |
| Generic cgminer (older Antminers, ePIC, Hiveon, …) | Hashrate, shares, uptime, pool | — |
| Demo | Synthetic (demo mode only) | — |

\* WhatsMiner is implemented against MicroBT's documented BTMiner API and is compat-gated pending confirmation on a physical unit.

## Privacy & security

Local-first by design: the app contacts only your configured miners, plus a few strictly
opt-in, off-by-default integrations (network-difficulty fetch, pool stats keyed by your
address, Hi3 MMP fleet view, AxeOS update check). An enforced network boundary refuses any
non-private address; secrets use AES-256-GCM in the Android Keystore; there is no analytics,
advertising, or account. The camera is used only for on-demand QR scanning. See
[`SECURITY.md`](SECURITY.md) for the full disclosure of what leaves the device and when.

## Requirements

- Android 8.0+ (API 26).
- Miners reachable on your LAN, or over Tailscale (advertise the subnet route for remote
  sites).
- Sideload the ~3.5 MB signed APK from the GitHub Releases page.

See [`CHANGELOG.md`](../CHANGELOG.md) for release history and [`DEVICE_MATRIX.md`](DEVICE_MATRIX.md)
for the full device/API matrix.
