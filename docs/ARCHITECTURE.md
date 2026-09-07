# Architecture

Single-module Android app (`app`), Kotlin + Jetpack Compose + Material 3, with
package boundaries that mirror a future multi-module split.

```
hi3.hashkit
├── core            Units & formatting (canonical units: GH/s, W, °C, mV, MHz, J/TH)
├── domain
│   ├── model       Miner, MinerIdentity, MinerTelemetry, MinerCapabilities,
│   │               Sourced<T> + ValueSource (MEASURED/REPORTED/CALCULATED/ESTIMATED/UNAVAILABLE)
│   └── adapter     MinerAdapter (read path) / MinerControlAdapter (write path),
│                   ProbeResult, TelemetryResult, ActionResult, AdapterRegistry
├── adapters
│   ├── espminer    Bitaxe/ESP-Miner/AxeOS + forks — tolerant JsonObject parser
│   ├── demo        Synthetic miners, demo mode only, isDemo-flagged and badged
│   └── canaan      (Phase 3, capture-driven)
├── discovery       MinerHostValidator (private/CGNAT-only boundary), SubnetUtils
│                   (CIDR expansion, /22 safety cap), MinerScanner (bounded concurrent
│                   probe with progress + cancellation), NetworkInspector
├── data
│   ├── db          Room: miners, miner_addresses, telemetry_samples, raw_responses
│   ├── repo        MinerRepository (identity re-binding, poll persistence), mappers
│   ├── poll        PollingEngine — foreground-only in v1, honest lastRefresh
│   └── prefs       DataStore settings (temp unit, demo mode, poll interval)
├── integrations/hi3  DISABLED placeholder interfaces for pool.hi3.cc / mmp.hi3.cc
├── ui              theme (central branding layer), dashboard, detail, discovery
└── di              Hilt modules (OkHttp, Room, adapter multibinding)
```

## Key decisions

- **Value provenance everywhere.** Every telemetry number is a `Sourced<T>`; the UI
  tags calculated/estimated values so estimated power can never masquerade as measured.
- **Read/write separation.** `MinerAdapter` is read-only; controls live on
  `MinerControlAdapter` and are additionally gated by per-device `MinerCapabilities`
  with human-readable reasons for anything unsupported.
- **Stable identity.** `stableKey` priority: MAC > serial > model+hostname fingerprint >
  IP fallback. Rediscovery on a new IP re-binds the address (kept in `miner_addresses`)
  instead of creating a duplicate; telemetry history survives IP changes.
- **Tolerant parsing.** ESP-Miner responses are read defensively from a `JsonObject`:
  missing/null/renamed fields, numbers-as-strings, and suffixed difficulties ("7.64G")
  all parse; unknown fields are preserved in `unrecognizedFields` for diagnostics.
  Verified against 5 recorded real-device fixtures spanning firmware v1.1.0–v2.15.2rc0,
  three ASIC families, and one fork firmware with a different field set.
- **Honest staleness.** Samples older than 120 s render as *Stale*; failed polls insert
  OFFLINE samples so charts show gaps, never interpolations.
- **Network boundary in code.** Cleartext HTTP is required by miner firmware; since
  Android can't scope cleartext by IP range, `MinerHostValidator` refuses any non-private
  destination at connect time and discovery refuses CIDRs wider than /22.

## Telemetry storage

High-resolution samples in `telemetry_samples` (indexed by miner + timestamp). Raw API
bodies are kept ~1 h for diagnostics only. Hourly/daily downsampling and configurable
retention land in Phase 4 (schema kept additive to avoid destructive migrations).
