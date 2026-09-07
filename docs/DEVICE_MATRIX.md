# Supported devices & API matrix

| Family | Devices | Local API | Monitoring | Controls | Notes |
|---|---|---|---|---|---|
| ESP-Miner / AxeOS | Bitaxe (Ultra/Supra/Gamma/GammaHex…), NerdAxe-style boards, dual-chip boards | HTTP `GET /api/system/info` | ✅ v1 | Phase 2 (`PATCH /api/system`, `POST /api/system/restart`) | Verified against real devices on fw v1.1.0, v2.14.0-x, v2.14.2, v2.15.1, v2.15.2rc0 |
| ESP-Miner forks | Lucky Miner-style firmware ("2.0.0" line) | Same endpoint, different field set (`DeviceModel`, `sn_str`, string `bestDiff`) | ✅ v1 | Not planned until verified | Real fixture recorded |
| Canaan Nano 3 / 3S | Nano 3, Nano 3S | To be captured from real devices | Phase 3 | After verification only | No endpoint is guessed |
| Canaan Avalon Q | Avalon Q | To be captured from real devices | Phase 3 | After verification only | Work modes/fan only if verifiable |
| Demo | Synthetic | — | ✅ (demo mode only) | — | Never mixed with real miners |
| Braiins OS, LuxOS, VNish, stock Bitmain, WhatsMiner, FutureBit | — | — | Not supported | Not supported | Adapter interface ready; no support claimed |

## ESP-Miner endpoint & field notes

- `GET /api/system/info` — sole endpoint used in v1. Response size observed 54–114 keys
  depending on firmware.
- Field variations handled: `bestDiff` numeric **or** suffixed string; `vrTemp`
  null/0/absent; `expectedHashrate` absent on older firmware and forks; `boardVersion`
  absent on some builds; forks add `DeviceModel` + `sn_str` (used for identity).
- Power on Bitaxe boards is measured by an onboard sensor → surfaced as MEASURED.
- Fixtures: `app/src/test/resources/fixtures/espminer/` (real captures are redacted:
  MAC, SSID, wallet/worker, pool host).
