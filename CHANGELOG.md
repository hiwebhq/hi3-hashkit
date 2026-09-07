# Changelog

## 0.29.0

- VNish (Antminer) monitoring now includes chip temperature, fans, frequency,
  expected hashrate, ASIC count and — unlike stock Bitmain — **wall power**
  (summed from per-chain `chain_consumption`), so efficiency in J/TH is computed.
  Verified against a real Antminer S21 Pro running Vnish 1.3.4. VNish's `version`
  command errors, so the firmware family is detected from the `stats` Type string.

## 0.28.0

- Stock Bitmain (Antminer) monitoring now includes chip temperature, fans,
  frequency, expected hashrate and ASIC count (verified against an Antminer S21
  Pro). Power is shown as unavailable — Bitmain does not expose it over the
  cgminer API.

## 0.26.0

- Polish batch: first-run onboarding, System/Dark/Light theme, pull-to-refresh
  and dashboard rescan, per-miner sparklines, a home-screen widget, per-category
  notification channels, accessibility descriptions, an in-app logo, and a UI
  smoke test.

## 0.22.0

- Added monitoring for Antminer-class cgminer firmware: stock Bitmain, VNish, and
  LuxOS. Shows hashrate, shares, uptime, and pool. Temperatures, fans, power, and
  controls for these firmwares are pending verification against real units and are not
  shown until confirmed.

## 0.21.0 — first public release

First release of Hi3 Hashkit for download, a local-first Android dashboard for
Bitcoin miners.

**Highlights**
- Live monitoring for Bitaxe/ESP-Miner, Canaan Avalon Nano 3, Braiins OS and
  NerdQAxe — hashrate, temps, power, efficiency, uptime, shares, best difficulty.
- Safe controls: reboot, pool change, fan, and firmware-bounded tuning with rollback
  (Bitaxe); pause/resume and reboot (Canaan Avalon). Every control is confirmed and
  audited; unsupported controls are hidden with a reason.
- Explainable 0–100 health score, alerts with per-miner thresholds and mute, and
  recovery notifications.
- Animated Blockchain → Stratum → Miners flow view with an uplink/latency check, and a
  per-miner live render (fan spins at reported RPM, chips glow by temperature).
- Fleet tools: groups, search, bulk actions, schedules, list/grid/compact layouts.
- Live firmware log streaming with wallet redaction.
- Solo-mining odds, electricity-cost estimates, CSV export, and passphrase-encrypted
  backups.
- App lock (biometric/PIN) and telemetry downsampling for long-term history.
- Optional, read-only Hi3 Pool and Hi3 MMP views (off by default).

**Fixed in 0.21.0**
- Pool comparison now understands stratum-proxy setups: miners that reach the pool
  through a proxy are matched per-rig by their LAN IP where available, and otherwise
  compared as fleet-total vs pool-total — instead of being wrongly reported as
  "not seen by this pool."

**Notes**
- Requires Android 8.0 (API 26) or newer.
- Local-first: talks only to the miners you configure; Hi3 Pool/MMP integrations are
  opt-in and read-only; no account, no ads, no analytics.
