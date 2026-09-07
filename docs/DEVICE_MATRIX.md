# Supported devices & API matrix

| Family | Devices | Local API | Monitoring | Controls | Notes |
|---|---|---|---|---|---|
| ESP-Miner / AxeOS official v2.x | Bitaxe (Ultra/Supra/Gamma/GammaHex…), dual-chip boards | `GET /api/system/info`, `GET /api/system/asic`, `PATCH /api/system`, `POST /api/system/restart` | ✅ | ✅ Reboot, primary-pool change, fan (auto/manual), tune limited to firmware-approved option lists, with audit + rollback | Endpoints verified against tagged firmware source (v2.14.2, v2.15.1) and real devices. v2.15+ pool edits echo the `"*****"` password mask (omitting it wipes the stored password — verified in source, locked by test) |
| NerdQAxe (fork) | NerdQAxe++ etc. | `GET /api/system/info`, `GET /api/system/asic` | ✅ | ❌ Monitoring-only (its `autofanspeed` is a mode enum, not a bool; control API unverified) | Real device fixture recorded |
| ESP-Miner forks | Lucky Miner-style firmware ("2.0.0" line) | `GET /api/system/info` only | ✅ | ❌ Monitoring-only until verified | Real fixture recorded; adapter defensively refuses control calls |
| Canaan Avalon Nano 3 | Nano 3 (verified live: fw 24071801, cgminer 4.11.1, API 3.7) | CGMiner TCP API on port 4028 — read commands `version`/`summary`/`estats`/`pools`/`coin`, and privileged `ascset` for controls | ✅ | ✅ **Pause/Resume** (`ascset 0,softoff`/`softon`, verified live, reversible) and **Reboot** (`ascset 0,reboot,0`; a wrong keyword is a harmless no-op). Fan/worklevel/frequency intentionally NOT exposed — arg ranges unverified. Pool changes need the web UI (unverified). | Firmware advertises its own ascset options via `ascset 0,help`; privileged API access confirmed from the LAN. `coin` supplies live network difficulty. `estats` MM fields: TMax/TAvg chip temps, Fan1 RPM/FanR%, PS[5] wall watts (REPORTED), WORKLEVEL mode, HDNA serial |
| Canaan Nano 3S / Avalon Q | Nano 3S, Avalon Q | Same CGMiner API family expected; accepted when `version` identifies as Avalon | Monitoring (compat-gated) | ❌ | Not reachable during Phase 3 (no Tailscale subnet route was advertised) — unverified; the tolerant parser treats them like the Nano 3 once reachable |
| Demo | Synthetic | — | ✅ (demo mode only) | — | Never mixed with real miners |
| Braiins OS (BOSer) | Braiins Mini Miner BMM 100 (verified live: boser-openwrt 0.1.0, API 3.7) | CGMiner-compatible TCP API on 4028: `version`, `summary`, `devs`, `temps`, `fans`, `devdetails`, `pools` — read-only | ✅ | ❌ Braiins gRPC/web control APIs unverified — monitoring only | `devs` "Nominal MHS" supplies firmware-declared expected hashrate (attainment works out of the box). No power sensor on the BMM 100 → power UNAVAILABLE; no MAC/serial over this API → identity falls back to model+IP. Braiins OS+ conversions identifying as BOSer are accepted but unverified |
| LuxOS, VNish, stock Bitmain, WhatsMiner, FutureBit | — | — | Not supported | Not supported | Adapter interface ready; no support claimed |

## ESP-Miner endpoint & field notes

- `GET /api/system/info` — sole endpoint used in v1. Response size observed 54–114 keys
  depending on firmware.
- Field variations handled: `bestDiff` numeric **or** suffixed string; `vrTemp`
  null/0/absent; `expectedHashrate` absent on older firmware and forks; `boardVersion`
  absent on some builds; forks add `DeviceModel` + `sn_str` (used for identity).
- Power on Bitaxe boards is measured by an onboard sensor → surfaced as MEASURED.
- Fixtures: `app/src/test/resources/fixtures/espminer/` (real captures are redacted:
  MAC, SSID, wallet/worker, pool host).
