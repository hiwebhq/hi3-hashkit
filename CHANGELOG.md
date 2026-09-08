# Changelog

## 0.45.0

- **Profitability & energy dashboard.** A new card estimates revenue/day, power cost/day,
  net/day, BTC/day, energy (kWh/day) and heat (BTU/hr) for the fleet. Opt-in BTC price
  fetch from mempool.space (or enter a price manually); uses the block subsidy and
  excludes tx/pool fees, and is clearly labelled an estimate.
- **Automated remediation: auto-recover offline miners.** Opt-in — when a miner stays
  offline past a threshold, the app power-cycles its smart plug (if configured) or reboots
  it, once, with a cooldown, and audit-logs every attempt.
- **Security hardening.** Android auto-backup disabled (`allowBackup=false`) so local data
  is never silently cloud-backed-up; pool identifiers are now validated before they're
  placed in a request path.

## 0.44.0

- **Broader model coverage via a generic cgminer fallback.** The cgminer adapter now
  accepts any miner exposing the standard cgminer API on 4028 — including unrecognized
  firmware (shown as "Generic ASIC (cgminer)") — for basic monitoring, instead of
  declining it. It still declines Avalon/BOSer (owned by their own adapters) and
  non-cgminer hosts.
- **FutureBit Apollo** is now recognized and monitored (via that cgminer path).
- **Model spec registry.** Nominal hashrate/power per model (public spec sheets) now seed
  the expected hashrate when neither the device nor the user provides one, so attainment %
  works for more models. Static reference data only — no endpoints. Covers the Antminer
  S17/S19/S21/S23 + T-series, WhatsMiner M30/M31/M50/M60, Canaan Avalon A12xx–A16 + Nano/Q,
  Bitaxe, FutureBit Apollo, Braiins BMM, and Fluminer (anchors cross-checked against public
  spec sheets).

## 0.43.0

- **NerdQAxe: Reboot.** Enabled the inherited `POST /api/system/restart` (identical to
  ESP-Miner); pool/fan/tune stay off on the fork until verified.
- **LuxOS: Pause/Resume.** Via `logon`→SessionID then `curtail <session>,sleep`/`wakeup`
  on 4028 — no password. Reboot (board-indexed) and pool changes deferred until verified.
- **Canaan Nano 3S / Avalon Q / Avalon Mini 3** now expose Pause/Resume + Reboot through the
  same verified `ascset` path as the Nano 3 (the Canaan adapter offers controls to any
  Avalon device) — unverified on those specific models until one is reachable.
- Controls are still absent only for **WhatsMiner**, whose write API needs MicroBT's
  encrypted token handshake — not shipped blind (see notes/DEVICE_MATRIX).

## 0.42.0

- **Stock Bitmain (Antminer) Reboot.** Reboot via the authenticated web CGI
  (`GET /cgi-bin/reboot.cgi`) with HTTP Digest auth (`root` + the miner's web password,
  stored encrypted) — the 4028 API is read-only on stock firmware, so control goes through
  the web interface. Digest auth is implemented in-app (no new dependency). Pool change
  (set_miner_conf.cgi config round-trip) and pause/resume (not present on stock) remain
  unsupported. Controls route by firmware family, so VNish keeps its own reboot + pause/resume.

## 0.41.0

- **Per-miner credentials (encrypted).** Miners can now store an admin password / API token,
  encrypted with the Android Keystore (like the MMP key), entered on the miner's detail
  screen. Decrypted only in-memory to authenticate a control; never logged or exported.
- **VNish controls: Reboot + Pause/Resume.** Via the VNish web API (`/api/v1/unlock` →
  Bearer token, then `/api/v1/system/reboot` and `/api/v1/mining/pause`/`resume`), using the
  stored web password. Pool/preset/fan changes (which need the settings-object round-trip)
  remain unsupported until verified. (DB v10, additive.)

## 0.40.0

- **Braiins OS controls: Pause/Resume.** The Braiins adapter now supports reversible
  Pause/Resume via BOSminer's documented `{"command":"pause"}`/`{"command":"resume"}` on
  port 4028 — no credentials needed. Pool/reboot/fan/tuning remain unsupported (they live
  in the gRPC/web API, or Braiins documents them as not fully implemented). Confirm on your
  BMM 100 the first time.

## 0.39.0

- **WhatsMiner (MicroBT) monitoring.** New adapter for the BTMiner API on port 4028
  (JSON `{"cmd":"summary"}`/`pools`/`get_miner_info`, all open read commands): hashrate,
  shares, uptime, pool, chip temperature, fans (in/out), and power where the firmware
  reports it. Detected via the `btminer` status description. Controls need MicroBT's
  encrypted admin-token API and are intentionally unsupported. Implemented against
  MicroBT's documented API — **compat-gated pending confirmation on a physical unit.**

## 0.38.0

- **Always-on safety monitor (opt-in).** A foreground service (with a persistent, low-
  priority notification) now runs the smart-plug over-temp cutoff even when the app is
  closed, checking armed miners every 20s, and re-arms itself after a reboot. Enable it in
  Settings → Monitoring. It only acts on miners with a plug cutoff set and never turns
  power back on.

## 0.37.1

- **Dedicated fast safety poll for the smart-plug cutoff.** A separate 20-second loop now
  checks miners with an armed plug cutoff, independent of the (possibly slow) per-farm
  dashboard interval — so over-temp protection fires promptly. It only polls plug-armed
  miners (cheap when none are configured). Background monitoring, if enabled, also
  evaluates the cutoff on its ~15-minute cycle.

## 0.37.0 — advanced operator features

- **Efficiency autotuner** (Bitaxe): sweeps firmware-approved frequencies at your current
  voltage, settles each, measures J/TH, restores your original setpoint, and recommends
  the most efficient one — apply on tap. Reachable from a miner's Controls.
- **Time-of-use scheduling**: schedules now support Pause/Resume, so you can auto-pause at
  peak-rate hours and resume off-peak.
- **Efficiency leaderboard** (⋮ menu): ranks miners by J/TH, expected-vs-actual attainment,
  or hashrate.
- **Smart-plug safety cutoff**: per-miner, cut power via a local Tasmota / Shelly / Kasa
  plug or a generic on/off webhook when chip temp exceeds your limit. Turning power back on
  is always manual; local addresses only; each cut is audit-logged. (DB v9, additive.)
- **Firmware-update awareness** (opt-in): checks AxeOS releases on GitHub and flags Bitaxes
  that are behind, with a dashboard banner linking to the release notes. No flashing.

## 0.36.0

- **F2Pool** and **Braiins Pool** added to the pool selector with per-rig correlation.
  F2Pool uses your mining-account name (`api.f2pool.com/bitcoin/{account}`, no token);
  Braiins uses a read-only access token (`Pool-Auth-Token` header). Both endpoints
  verified against their real APIs.
- **NiceHash** appears as "coming soon" (its signed API needs a key+secret; contacts
  nothing yet), alongside Luxor.

## 0.35.0

- **Luxor** appears in the pool selector as "coming soon" — its API isn't verified yet,
  so selecting it contacts nothing and sends no address or key; the card and settings
  say support is on the way. Pick another pool for live stats meanwhile.

## 0.34.0

- **Choose your pool.** The Hi3 Pool card/settings are now a generic "Pool" integration
  with a selector for **Hi3 Pool, Public Pool (web.public-pool.io), CKPool
  (raw.stats.ckpool.org) and OCEAN (api.ocean.xyz)**. Pick your pool, enter your
  address/subaccount (QR-scannable), and the dashboard correlates pool-side workers with
  your local miners per rig. An optional API-key/watcher-token field is provided for
  pools that need one. Each endpoint was verified against the pool's real API — no
  invented routes. (Luxor's GraphQL/API-key integration is planned next.)

## 0.33.0

- **Dedicated Fleet page.** Tap the Fleet card on the dashboard to open a focused view:
  the aggregate summary and trend chart, then every miner's card — without the
  pool/MMP/solo/search sections.
- **About → Supported miners.** New section listing each supported firmware/device and
  exactly what the app does with it.
- **Store** — new ⋮ menu item (above About) linking to the Hi3 merch store.
- Tapping the logo seven times opens a Bitcoin payment page (test link for now).

## 0.32.1

- Payout-address QR scan now accepts only a bare address; a `bitcoin:` URI or one with
  query params is rejected with a prompt to scan the plain address.

## 0.32.0

- **Per-farm refresh interval (5s–1d).** Settings → Monitoring now has a preset picker
  for a global default plus one per farm; the foreground poll cadence follows whichever
  farm you're viewing. (DB migrated to v8, additive.)
- **QR scan for the pool payout address.** A scan icon on the payout field opens an
  offline scanner (ZXing — no Google dependency, nothing leaves the device) and strips
  any `bitcoin:` prefix/params. Adds an optional camera permission (feature not required).
- **About page:** added **Share app** (opens the Android share sheet with
  https://mmp.hi3.cc/hashkit) and **Feature request** (opens https://hi3.cc/contact).
- **Privacy Policy** — new in-app page, the last item in the ⋮ menu, covering local-first
  storage, the opt-in connections, camera-for-QR, and notifications.

## 0.31.0

- **About moved to the ⋮ menu**, and now includes a step-by-step "How to use" guide
  (get on-network → discover → add → farms → read values → control safely → alerts →
  privacy). Removed from Settings.
- **Live Bitcoin** — new ⋮ menu item that opens https://hi3.cc/bitcoin in a fresh
  external browser session.
- **Alerts & notifications are now off by default** — enable them in Setup → Alerts
  when you're ready. Existing installs keep their current setting.

## 0.30.0

- **Auto-scan at launch.** The app now scans your local subnet in the background as
  soon as it opens, so miners on the same network are already on the list by the time
  you reach the dashboard or Add screen. Runs off-screen and survives navigation.
- **Network scan controls** (overflow menu → Network scan): shows this device's current
  LAN IP, lets you change the scan range (CIDR), and start / stop / restart the scan
  with live progress. A toggle turns auto-scan-at-launch on or off.
- **Multiple farms / sites.** Group miners into farms, each with its own scan subnet,
  and mark one as the default (opens on launch). A farm switcher on the dashboard filters
  the view to one site or "All farms". Adding a farm offers an immediate scan of its
  subnet, and discovered miners are tagged to that farm. Deleting a farm keeps its
  miners (they become unassigned) and never loses history. (DB migrated to v7, additive.)
- **Header cleanup.** The top bar now carries only Notifications, Setup and Exit;
  Refresh, Flow view, Farms, Network scan and Schedules moved into an overflow (⋮) menu.

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
