# Supported miners

Complete model-by-model support for Hi3 Hashkit (as of **v0.39.0**).

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
| NerdQAxe++ | ESP‑Miner fork · HTTP | Full | — (unverified) | ✅ (monitoring) |
| NerdQAxe+ | ESP‑Miner fork · HTTP | Full | — | ✅ (monitoring) |
| NerdQAxe+ Hydro | ESP‑Miner fork · HTTP | Full | — | 🟡 |
| Antminer S21 Pro (stock) | BMMiner · cgminer 4028 | Hashrate, chip temps, fans, freq, expected, ASIC count, shares, uptime, pool (no power) | — | ✅ |
| Antminer S21+ | BMMiner · cgminer 4028 | Same as S21 Pro | — | 🟡 |
| Antminer S21+ Hyd | BMMiner · cgminer 4028 | Same as S21 Pro | — | 🟡 |
| Antminer S21 Hyd | BMMiner · cgminer 4028 | Same as S21 Pro | — | 🟡 |
| Antminer S21 XP | BMMiner · cgminer 4028 | Same as S21 Pro | — | 🟡 |
| Antminer S19 series | BMMiner · cgminer 4028 | Hashrate, shares, uptime, pool (temps/fans firmware‑dependent) | — | 🟡 |
| Antminer S17 series | BMMiner · cgminer 4028 | Hashrate, shares, uptime, pool (temps/fans firmware‑dependent) | — | 🟡 |
| Antminer (VNish firmware), e.g. S21 Pro | VNish · cgminer 4028 (read) + web API 80 (control) | Full incl. **wall power** & efficiency | **Reboot, Pause/Resume** (needs web password) | ✅ (S21 Pro) |
| Antminer (LuxOS firmware) | LuxOS · cgminer 4028 | Basic: hashrate, shares, uptime, pool | — | 🟡 |
| Canaan Avalon Nano 3 | Canaan · CGMiner 4028 | Full (temps, fan, wall watts) | **Pause/Resume, Reboot** | ✅ |
| Canaan Nano 3S | Canaan · CGMiner 4028 | Full (like Nano 3) | — | 🟡 |
| Canaan Avalon Q | Canaan · CGMiner 4028 | Full (like Nano 3) | — | 🟡 |
| Canaan Avalon Mini 3 | Canaan · CGMiner 4028 | Full (like Nano 3) | — | 🟡 |
| Braiins BMM 100 | Braiins OS / BOSer · CGMiner 4028 | Full (no power sensor → power unavailable) | **Pause/Resume** | ✅ |
| WhatsMiner M2X/M3X/M5X/M6X | MicroBT BTMiner · 4028 `{"cmd":…}` | Hashrate, shares, uptime, pool, chip temp, fans, power (where reported) | — | 🟡 |
| Demo | Synthetic | Full (demo mode only) | — | — |

## Notes

- **Antminers have no controls** in Hashkit — reboot/pool/tune need Bitmain's authenticated
  web API, which isn't verified. Monitoring only, regardless of firmware (stock/VNish/LuxOS).
- **Compat‑gated (🟡)** items graduate to ✅ with a single real API capture from that model —
  mainly the **Antminer S19/S17** and **Canaan Nano 3S / Avalon Q / Avalon Mini 3**.
- **Not supported** (no verified API captured): WhatsMiner *controls*, FutureBit, and
  anything not listed above. The adapter framework is ready; nothing is claimed until it's
  verified on hardware or against the vendor's documented API.
- Every miner API is verified against real hardware (or, for compat‑gated entries, the
  vendor's documented API) before it ships — endpoints are never invented.

See [`DEVICE_MATRIX.md`](DEVICE_MATRIX.md) for the endpoint-level detail and field notes, and
[`OVERVIEW.md`](OVERVIEW.md) for the feature summary.
