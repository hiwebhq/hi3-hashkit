# Supported miners

Complete model-by-model support for Hi3 Hashkit (as of **v0.64.0**).

**Status:** ✅ Verified on real hardware · 🟡 Compat‑gated (same firmware/API, basic
telemetry is safe, pending confirmation on that exact model).

Support is by **firmware/API family**, so any model on a listed firmware works — you don't
need the exact unit that was tested.

| Model | Firmware / API | Monitoring | Controls | Status |
|---|---|---|---|---|
| Bitaxe Max (202) | AxeOS / ESP‑Miner · HTTP | Full | Reboot, pool, fan, tuning, autotune | ✅ |
| Bitaxe Ultra (204) | AxeOS · HTTP | Full | Reboot, pool, fan, tuning, autotune | ✅ |
| Bitaxe Supra (400) | AxeOS · HTTP | Full | Reboot, pool, fan, tuning, autotune | ✅ |
| Bitaxe Gamma (600) | AxeOS · HTTP | Full | Reboot, pool, fan, tuning, autotune | ✅ |
| Bitaxe Gamma Turbo | AxeOS · HTTP | Full | Reboot, pool, fan, tuning, autotune | ✅ |
| Bitaxe Supra Hex | AxeOS (multi‑chip) · HTTP | Full | Reboot, pool, fan, tuning, autotune | ✅ |
| NerdQAxe++ | ESP‑Miner fork · HTTP | Full | **Reboot** | ✅ |
| NerdQAxe+ | ESP‑Miner fork · HTTP | Full | **Reboot** | ✅ |
| NerdQAxe+ Hydro | ESP‑Miner fork · HTTP | Full | **Reboot** | 🟡 |
| Antminer S21 Pro (stock) | BMMiner · cgminer 4028 (read) + web CGI (control) | Hashrate, chip temps, fans, freq, expected, ASIC count, shares, uptime, pool (no power) | **Reboot** (Digest auth, root password) | ✅ |
| Antminer S21+ | BMMiner · cgminer 4028 (read) + web CGI (control) | Same as S21 Pro | **Reboot** (Digest auth) | 🟡 |
| Antminer S21+ Hyd | BMMiner · cgminer 4028 (read) + web CGI (control) | Same as S21 Pro | **Reboot** (Digest auth) | 🟡 |
| Antminer S21 Hyd | BMMiner · cgminer 4028 (read) + web CGI (control) | Same as S21 Pro | **Reboot** (Digest auth) | 🟡 |
| Antminer S21 XP | BMMiner · cgminer 4028 (read) + web CGI (control) | Same as S21 Pro | **Reboot** (Digest auth) | 🟡 |
| Antminer S19 series | BMMiner · cgminer 4028 (read) + web CGI (control) | Hashrate, shares, uptime, pool (temps/fans firmware‑dependent) | **Reboot** (Digest auth) | 🟡 |
| Antminer S17 series | BMMiner · cgminer 4028 (read) + web CGI (control) | Hashrate, shares, uptime, pool (temps/fans firmware‑dependent) | **Reboot** (Digest auth) | 🟡 |
| Antminer (VNish firmware), e.g. S21 Pro | VNish · cgminer 4028 (read) + web API 80 (control) | Full incl. **wall power** & efficiency | **Reboot, Pause/Resume** (needs web password) | ✅ (S21 Pro) |
| Antminer (LuxOS firmware) | LuxOS · cgminer 4028 (+ session API) | Basic: hashrate, shares, uptime, pool | **Pause/Resume** (curtail; no password) | 🟡 |
| Canaan Avalon Nano 3 | Canaan · CGMiner 4028 | Full (temps, fan, wall watts) | **Pause/Resume, Reboot** | ✅ |
| Canaan Nano 3S | Canaan · CGMiner 4028 | Full (like Nano 3) | **Pause/Resume, Reboot** | 🟡 |
| Canaan Avalon Q | Canaan · CGMiner 4028 | Full (like Nano 3) | **Pause/Resume, Reboot** | 🟡 |
| Canaan Avalon Mini 3 | Canaan · CGMiner 4028 | Full (like Nano 3) | **Pause/Resume, Reboot** | 🟡 |
| Braiins BMM 100 | Braiins OS / BOSer · CGMiner 4028 | Full (no power sensor → power unavailable) | **Pause/Resume** | ✅ |
| WhatsMiner M2X/M3X/M5X/M6X | MicroBT BTMiner · 4028 `{"cmd":…}` | Hashrate, shares, uptime, pool, chip temp, fans, power (where reported) | — | 🟡 |
| FutureBit Apollo BTC (Gen1/Gen2) | cgminer 4028 (via generic path) | Hashrate, shares, uptime, pool | — (HTTP dashboard API unverified) | 🟡 |
| Generic cgminer (long tail: older Antminers, ePIC, Hiveon…) | cgminer 4028 | Hashrate, shares, uptime, pool | — | 🟡 |
| Demo | Synthetic | Full (demo mode only) | — | — |

## Notes

- **Antminer controls depend on firmware:** stock Bitmain gets **Reboot** (web CGI with
  Digest auth, root password), VNish gets **Reboot + Pause/Resume** (web API, stored
  password), LuxOS gets **Pause/Resume** (curtail, no password). Pool change and tuning
  remain unsupported on all Antminer firmware until the settings round-trips are verified.
- **Compat‑gated (🟡)** items graduate to ✅ with a single real API capture from that model —
  mainly the **Antminer S19/S17** and **Canaan Nano 3S / Avalon Q / Avalon Mini 3**.
- **Anything else that speaks the standard cgminer API on 4028** (older Antminers, ePIC,
  Hiveon, …) gets basic monitoring via the generic fallback, shown as
  "Generic ASIC (cgminer)". Controls stay absent for WhatsMiner (encrypted admin-token
  API) and FutureBit (HTTP dashboard API unverified) — nothing is claimed until it's
  verified on hardware or against the vendor's documented API.
- **Expected hashrate / attainment %** is seeded from a built-in model spec registry
  (public spec-sheet nominals) when neither the device nor the user provides one.
- **Per-chip / per-chain health** is surfaced for **Antminer-class** miners (stock Bitmain,
  VNish) that report it — per-board hashrate, working/dead chip counts, hardware errors and
  hottest-chip temp — parsed only from fields present in real captures (`chain_rate/acn/acs/
  hw`, `temp_chip`).
- **Measured wall power via metering plugs:** for a miner that doesn't report its own power
  (e.g. **stock Bitmain**, **Braiins BMM 100**), configuring a metering smart plug
  (Tasmota/Shelly/Kasa energy monitor) fills in **measured** watts and efficiency over the
  LAN.
- **Bitaxe auto-tuner** sweeps only firmware-approved frequencies, under a chip-temp ceiling,
  and optimizes for efficiency or hashrate — no invented tune values; original setpoint is
  restored and applying the winner is a separate tap.
- Every miner API is verified against real hardware (or, for compat‑gated entries, the
  vendor's documented API) before it ships — endpoints are never invented.

See [`DEVICE_MATRIX.md`](DEVICE_MATRIX.md) for the endpoint-level detail and field notes, and
[`OVERVIEW.md`](OVERVIEW.md) for the feature summary.
