# Hi3 Hashkit — Your Whole Mining Fleet, In Your Pocket. On Your Network. Nobody Else's.

**Hi3 Hashkit** is the privacy-first Android app for monitoring and safely controlling
Bitcoin miners — from a single Bitaxe on your desk to a multi-site farm of Antminers.
No account. No cloud. No telemetry. It talks only to the miners on **your** network
(LAN or Tailscale), and every reading is honestly labeled — measured, reported,
calculated, or estimated — so an estimate is never dressed up as a fact.

**~3.5 MB. Android 8.0+. Sideload and go.**

Current release: **v0.45.0**.

## 🔍 Find Every Miner, Instantly

- **Auto-discovery** scans your subnet at launch — or add by IP/CIDR, or drive the
  dedicated Network Scan screen yourself (current IP, editable range, start/stop/restart).
- **Farms & sites**: group miners by location, each with its own subnet and refresh
  interval, and flip between them with the dashboard farm switcher.

## 📊 Monitoring That Tells You the Truth

- **Live fleet dashboard**: hashrate, temps, power, efficiency, and health per miner —
  plus fleet totals, trend charts, per-miner sparklines, search, and four layout
  densities from Large to Grid.
- A dedicated **Fleet page**, a slick **3D flow view**, and a **home-screen widget** so
  your hashrate is never more than a glance away.
- **Honest offline/stale states** with a redacted raw-response viewer — never a stale
  number pretending to be live.
- **Local history** with configurable retention, downsampling, **CSV export**, and full
  JSON backup/restore.
- **Attainment %** (actual vs. expected hashrate), auto-seeded from a built-in spec
  registry covering dozens of models — even when the miner doesn't report an expected
  rate.

## 🎛️ Control — Only Where It's Verified

Every control ships only after verification against real firmware. Unverified means
unsupported, never guessed.

- **Reboot, pool change, fan control, and firmware-bounded tuning** with confirmations
  and rollback — plus bulk actions across selected miners.
- **Efficiency autotuner (Bitaxe)**: sweeps approved frequencies, measures J/TH, and
  recommends your most efficient setpoint — apply with one tap, restore just as easily.
- **Time-of-use scheduling**: auto-pause at peak electricity rates, resume off-peak.
- Admin passwords/tokens stored **encrypted in the Android Keystore**, decrypted only
  in-memory to authenticate a control.

## ⚡ Run It Like an Operator

- **Profitability & energy dashboard**: revenue/day, power cost/day, net/day, BTC/day,
  kWh/day — even heat output in BTU/hr. BTC price via opt-in mempool.space fetch or
  manual entry, clearly labeled an estimate.
- **Auto-recover offline miners** (opt-in): a miner goes dark past your threshold,
  Hashkit power-cycles its smart plug or reboots it — once, with a cooldown, and every
  attempt audit-logged.
- **Smart-plug over-temp safety cutoff**: per-miner via Tasmota, Shelly, Kasa, or
  generic webhook — cuts power when chip temp exceeds your limit (manual restore only),
  backed by a 20-second safety poll and an opt-in always-on foreground service that
  survives app-close and reboot.
- **Efficiency leaderboard**: rank your fleet by J/TH, attainment, or raw hashrate.
- **Alerts & health scoring** (offline, temp, fan, reject-rate) with per-miner
  overrides — off by default, on your terms.
- **Firmware-update awareness** (opt-in): know when a Bitaxe falls behind the latest
  AxeOS release.
- **Biometric/PIN app lock**, System/Dark/Light themes, and a step-by-step in-app
  how-to.

## 🌊 Pool Stats, Correlated Per Rig (Opt-In)

Pick your pool and the dashboard matches pool-side workers to your local miners — the
payout address is QR-scannable. Each integration is verified against its real API.

| Pool | Identifier | Endpoint |
|---|---|---|
| Hi3 Pool | Payout address | `/api/client/{address}` (+ stratum-proxy sessions) |
| Public Pool | Payout address | `/api/client/{address}` |
| CKPool | Payout address | `raw.stats.ckpool.org/users/{address}` |
| OCEAN | Address / username | `api.ocean.xyz/v1/user_hashrate_full/{address}` |
| F2Pool | Mining account | `api.f2pool.com/bitcoin/{account}` |
| Braiins Pool | Access token | `pool.braiins.com/accounts/workers/json/btc` (`Pool-Auth-Token`) |
| Luxor, NiceHash | — | Coming soon (shown but contacts nothing) |

## ⛏️ Supported Miners

Support is by **firmware family** — any model on a supported firmware works. ✅ verified
on real hardware; 🟡 same firmware/API, pending confirmation on that exact model.

| Device / Firmware | Monitoring | Controls |
|---|---|---|
| **Bitaxe** (Max, Ultra, Supra, Gamma, Gamma Turbo, Supra Hex) — AxeOS | Full ✅ | Reboot, pool, fan, tuning, **autotune** ✅ |
| **NerdQAxe++ / NerdQAxe+** (+ Hydro 🟡) & ESP-Miner forks | Full ✅ | Reboot ✅ |
| **Antminer S21 Pro** (stock BMMiner) ✅ — S21+/S21 Hyd/S21 XP, S19, S17 🟡 | Hashrate, chip temps, fans, freq, expected, shares, pool | Reboot (root password) |
| **Antminer on VNish** (e.g. S21 Pro) ✅ | Full, incl. **wall power & efficiency** | Reboot, Pause/Resume |
| **Antminer on LuxOS** 🟡 | Basic (hashrate, shares, uptime, pool) | Pause/Resume |
| **Canaan Avalon Nano 3** ✅ — Nano 3S, Avalon Q, Mini 3 🟡 | Full (temps, fan, wall watts) | Pause/Resume, Reboot |
| **Braiins BMM 100** (Braiins OS) ✅ | Full (no power sensor) | Pause/Resume |
| **WhatsMiner M2X–M6X** (BTMiner) 🟡 | Hashrate, shares, pool, chip temp, fans, power\* | — (encrypted token API) |
| **FutureBit Apollo BTC** (Gen1/Gen2) 🟡 | Hashrate, shares, uptime, pool | — |
| **Generic cgminer** (older Antminers, ePIC, Hiveon, and the long tail) 🟡 | Basic monitoring | — |

\* WhatsMiner is implemented against MicroBT's documented BTMiner API and is compat-gated
pending confirmation on a physical unit.

See [`SUPPORTED_MINERS.md`](SUPPORTED_MINERS.md) for the model-by-model breakdown and
[`DEVICE_MATRIX.md`](DEVICE_MATRIX.md) for endpoint-level detail.

## 🔒 Private by Design

An **enforced network boundary** refuses any non-private address. Secrets are AES-256-GCM
in the Android Keystore. Android auto-backup is disabled, so your data never silently
leaves the device. No analytics, no ads, no account — the camera is used only for
on-demand QR scanning. Everything that can leave the device is opt-in, off by default
(network-difficulty fetch, pool stats keyed by your address, Hi3 MMP fleet view, AxeOS
update check), and fully disclosed in [`SECURITY.md`](SECURITY.md).

## Requirements

- Android 8.0+ (API 26).
- Miners reachable on your LAN, or over Tailscale (advertise the subnet route for remote
  sites).
- Sideload the ~3.5 MB signed APK from the GitHub Releases page.

See [`CHANGELOG.md`](../CHANGELOG.md) for release history.

---

**Hi3 Hashkit. Every sat accounted for. Every byte stays home.**
