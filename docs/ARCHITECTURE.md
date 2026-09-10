# Architecture

Two Gradle modules — the phone app (`app`) and a Wear OS companion (`wear`) — Kotlin +
Jetpack Compose + Material 3, with package boundaries inside `app` that mirror a future
multi-module split.

```
hi3.hashkit
├── core            Units & formatting (canonical units: GH/s, W, °C, mV, MHz, J/TH),
│                   KeystoreCrypto (AES-256-GCM), LicenseValidator (advanced-features gate)
├── domain
│   ├── model       Miner, MinerIdentity, MinerTelemetry (incl. per-chain ChainReading),
│   │               MinerCapabilities, Sourced<T> + ValueSource, HalvingMath
│   ├── adapter     MinerAdapter (read) / MinerControlAdapter (write), *Result types, registry
│   ├── alerts      AlertEvaluator + AlertType, NotificationWindow (quiet hours/digest)
│   ├── analysis    AnomalyDetector (gradual drift / rising reject rate)
│   ├── rules       RuleEngine (if-condition-then-action evaluation)
│   └── solo        Solo-mining probability math
├── adapters        espminer (AxeOS + forks), cgminer (Antminer/VNish/LuxOS/WhatsMiner/
│                   generic), canaan, braiins, demo — all tolerant JsonObject parsers
├── discovery       MinerHostValidator (private/CGNAT-only boundary), SubnetUtils (/22 cap),
│                   MinerScanner, AutoScanManager, NsdDiscoverer (mDNS), NetworkInspector
├── data
│   ├── db          Room v14: miners, telemetry_samples (+perChainJson), raw_responses,
│   │               alerts, audit, schedules, rules, saved_pools, maintenance_notes, farms, hourly
│   ├── repo        MinerRepository (identity re-binding, plug-power augmentation),
│   │               ControlRepository, FleetControl (bulk plan/execute), DifficultyRepository
│   ├── alerts      AlertRepository, AlertNotifier, WebhookNotifier, AlertActionReceiver
│   ├── rules       RuleRunner        ├── schedule  ScheduleEngine
│   ├── remediation RemediationEngine ├── wear      WearSyncManager (Data Layer publish)
│   ├── poll        PollingEngine (foreground) + MonitorWorker (background), SafetyMonitorService
│   └── prefs       DataStore settings
├── integrations    hi3 (pool/MMP), plug (SmartPlugClient: switch + power read),
│                   mqtt (MqttClient + MqttPublisher), metrics (PrometheusServer +
│                   HtmlDashboard), update (FirmwareUpdateChecker)
├── ui              theme (branding + UI-theme accents), dashboard, detail, table, rack,
│                   wall (TV), flow, rules, schedules, addressbook, autotune, alerts,
│                   settings, network, farms, leaderboard, onboarding, widget
└── di              Hilt modules (OkHttp, Room + migrations, adapter multibinding)

wear                Wear OS companion: FleetTileService (ProtoLayout), FleetDataListenerService,
                    MainActivity (Wear Compose) — fed by the phone over the Wearable Data Layer
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
  destination at connect time and discovery refuses CIDRs wider than /22. The only
  deliberately-public traffic is opt-in and documented (mempool.space, GitHub release
  check, the alert webhook you configure).
- **Pure cores, thin runners.** Decision logic is pure and unit-tested (`AlertEvaluator`,
  `AnomalyDetector`, `RuleEngine`, `HalvingMath`, `MetricsFormatter`, `HtmlDashboard`,
  `LicenseValidator`); the repositories/runners wire them to Room, the network, and Android.
- **One poll tick drives everything.** `PollingEngine.pollAllOnce` (foreground) and
  `MonitorWorker` (background) both fan out to alerts, schedules, the rules engine,
  remediation, the widget, the Wear publish, MQTT, and the Prometheus/web server — so
  every subsystem sees the same fresh sample.
- **Secrets at rest.** Per-miner admin passwords/tokens and the MMP/MQTT keys are
  AES-256-GCM via a non-exportable Android Keystore key (`KeystoreCrypto`); Android
  auto-backup is disabled.

## Telemetry storage

High-resolution samples in `telemetry_samples` (indexed by miner + timestamp), each
carrying per-chain health JSON where the firmware reports it. Raw API bodies are kept ~1 h
for diagnostics only. Completed hours roll into `telemetry_hourly` aggregates with
configurable raw retention; the merged history feeds the detail charts. Room is at
schema **v14**, and every migration is additive (columns/tables only) to avoid destructive
upgrades.
