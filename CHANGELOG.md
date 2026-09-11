# Changelog

## 0.68.0

- **Log Analyzer — persistent tier.** Viewing a miner's logs now **captures** them to a local
  per-miner ring buffer (up to ~10k lines / ~7 days, wallets redacted), so the analyzer can
  review **past** logs, not just the live stream. The Analyze panel gains a window selector
  (**Live / 1h / 24h / 7d**) that runs the analysis over stored history, plus **Export
  captured logs**. Retention is trimmed automatically (per-miner cap + 7-day prune).
- **More items moved into Advanced features:** **Schedules**, **Farms**, **Pool address
  book** and **Rack & site** now sit behind the advanced-features gate (like Live Bitcoin).
  They still show while advanced features ship free (`ADVANCED_FEATURES_FREE`).

## 0.67.0

- **Log Analyzer (advanced).** On a Bitaxe/AxeOS miner's live log stream, tap the new
  Analyze toggle (shown when advanced features are unlocked) to get an on-device,
  heuristic breakdown: line/error/warning counts, a per-category summary (pool, shares,
  ASIC, thermal, network, system), and **plain-language findings with suggested actions**
  — e.g. repeated restarts/resets, pool-disconnect storms, thermal warnings, ASIC errors,
  high rejects. No cloud, no LLM; wallets stay redacted. (AxeOS-family only — the sole
  firmware that exposes a log stream.)
- Settings sections are now sorted alphabetically (Data & Exports and Demo kept last).

## 0.66.0

- **Update Hashkit (in-app self-update).** Settings → Update Hashkit checks the official
  GitHub release, downloads the signed APK, and installs it over the top — so **all your
  miners, telemetry history, logs and settings are kept** (a same-key APK update, not a
  reinstall). Sideload builds only: it's compiled out of the Play build (`-PplayStore`),
  since Google Play forbids self-updating and handles updates itself.
- **Advanced-gated menu items.** "Pool address book" and "Rack & site" now sit behind the
  advanced-features gate (like Live Bitcoin); they still show while advanced features ship
  free.
- **Fleet card:** the daily-cost metric is now labeled **"Daily Energy Est."** and shows the
  value in **USD**.

## 0.65.0

- **Time-of-use power scheduling (tune presets).** Schedules gained an **Apply tune preset**
  action (frequency + core voltage): schedule a low-power preset at your peak-rate start and
  your normal preset at the end for genuine TOU power management. Only firmware that accepts
  those values (e.g. Bitaxe/AxeOS) runs it; others are skipped. Also available as a fleet
  bulk action.
- **Docs.** Documented two BTC-Tools-style batch operations — **mass firmware upgrade** and
  **batch static-IP / network config** — in the DEVICE_MATRIX "pending hardware verification"
  list (gated until each family's endpoints are verified on real hardware; no invented
  endpoints). Refreshed ARCHITECTURE for the current package layout (two modules, all
  subsystems, Room v14).

## 0.64.0

- **Local web dashboard (Advanced).** The local server (formerly just Prometheus /metrics)
  now also serves a **read-only HTML fleet dashboard** at `http://<device-ip>:<port>/` — a
  self-contained, auto-refreshing page (fleet total + per-miner table) any browser or TV on
  your LAN can open without the app. `/metrics` still serves the Prometheus format. Read-only,
  no auth, LAN/tailnet only; runs while the app is open and the toggle is on.

## 0.63.0

- **Pool & wallet address book.** Save the pools/wallets you use (name · stratum URL · port ·
  worker/payout address) and apply one across the whole fleet — or a group — in a tap, with
  a confirmation (Dashboard menu → Pool address book). No pool password is stored; the
  firmware keeps its own. Unsupported miners are skipped and the result is summarized.

## 0.62.0

- **Auto-tuner upgraded (ClockTune-style).** The Bitaxe auto-tuner now enforces a **chip-temp
  ceiling** (65/70/75 °C) — points that settle above it are excluded and the sweep stops
  climbing (higher frequencies only run hotter) — and you can **optimize for efficiency or
  hashrate**. Results show per-point temperature; it still only uses firmware-approved
  frequencies, restores your original setpoint, and applies the best point only when you tap.

## 0.61.0

- **Automation rules engine.** A new "if condition then action" builder (Dashboard menu →
  Automation rules): **when** chip/VR temp above X, hashrate below X% of expected, reject
  rate above X%, or miner offline — **then** pause / resume / reboot / plug off / plug on /
  notify, on all miners or a target group, at most once per the rule's interval. Control
  actions use the verified control path (skipped where unsupported); every fire is recorded.
  Unifies what used to take separate schedules + remediation + alert settings.

## 0.60.0

- **Sortable fleet table view.** A new dense, spreadsheet-style list (Dashboard menu →
  Fleet table): name · IP · model · hashrate · temp · fan · pool · uptime · J/TH, with
  tap-to-sort columns (IP sorts numerically) and a filter box. Tap a row to open the miner.

## 0.59.0

- **Real wall-power from metering smart plugs.** If a miner doesn't report its own power
  (e.g. stock Bitmain) but has a metering plug configured (Tasmota / Shelly / Kasa energy
  monitor), the app now reads the plug's measured watts over the LAN and uses it — turning
  estimated efficiency/cost into **measured** J/TH. Miners that report their own power are
  unaffected; non-metering plugs are ignored.

## 0.58.0

- **Wall / TV mode: size selector.** Pick **Small / Medium / Large** right on the wall page
  (top-right chips) — it scales the tiles and all fonts and is remembered. Default is now
  Medium (0.57.0's fixed size was Large).

## 0.57.0

- **Wall / TV mode: bigger, room-readable layout.** Larger tiles (320dp) with much larger
  fonts — fleet total 72sp, per-miner hashrate 44sp, name 28sp — plus a bigger exit target
  and roomier spacing, so it reads across a room on an Android TV or wall-mounted tablet.

## 0.56.0

- **Flow view: miners look like ASIC miners.** Each miner node is now a little chassis with
  two spinning intake fans (fans spin while hashing, static when offline), tinted by status.
- **Flow view: labels never overlap.** Miner name/hashrate labels are staggered across two
  rows and ellipsized to their slot, and the glyphs are sized to their slot, so nothing
  collides regardless of how many miners you have.

## 0.55.0

- **Maintenance-log photos.** Notes can now include a photo (via the system photo picker,
  no storage permission); it's copied into app-private storage and shown as a thumbnail,
  and removed when the note is deleted.
- **Wear tile follows the UI theme.** The phone now sends its selected accent color over
  the Wearable Data Layer, so the watch tile and companion app match the theme you picked
  (defaults to brand blue).

## 0.54.0

Wave D — fleet & fun:

- **Maintenance log.** Per-miner timestamped notes (repastes, fan swaps, cleanings) on the
  detail screen, stored locally and included in backups. (Photo attachment is a planned
  follow-up.)
- **mDNS discovery.** The network screen can now browse mDNS/DNS-SD for miner web UIs and
  probe whatever private addresses turn up — no subnet typing required.
- **Panic — whole fleet.** A guarded dashboard action to Pause-all or Reboot-all across
  every miner at once, routed through the normal plan → confirm → execute flow (shows
  supported/skipped first).
- **Bitcoin network card.** A dashboard card with the current block height, block subsidy,
  next difficulty-adjustment countdown (blocks/ETA/estimated change), and next-halving
  countdown — from the opt-in mempool.space feed.
- **Solo block-found celebration.** If a miner's best share meets the network target (a
  solved block!), the app raises a celebratory alert.

Pool payout tracking was dropped from the wishlist — it doesn't apply to a solo pool (Hi3 /
Public Pool), where a found block pays the full reward straight to your address.

## 0.53.0

Wave C (part 1) — integrations:

- **Home Assistant / MQTT publish.** Opt-in MQTT publishing of fleet + per-miner telemetry
  to a local broker (Settings → MQTT / Home Assistant), with optional Home Assistant
  MQTT-discovery so miners appear as HA devices automatically. One-way, LAN-only, best
  effort; broker password stored encrypted in the Keystore. Uses a tiny hand-rolled MQTT
  3.1.1 publisher (no heavy client dependency).
- **Prometheus /metrics endpoint (Advanced feature).** Opt-in local HTTP endpoint serving
  fleet + per-miner gauges in Prometheus format for Grafana/Prometheus. Read-only,
  GET /metrics only, no auth — private LAN/tailnet only. Gated behind the advanced-features
  flag.

Pool payout tracking is deferred pending a verified pool API sample (see notes below) — the
app won't guess payout fields.

## 0.52.0

Wave B — alerting:

- **Quiet hours.** Suppress alert notifications during a nightly window (Settings → Quiet
  hours & digest). Alerts are still recorded and show in the app and the digest — only the
  system/webhook notification is held. Window can wrap past midnight.
- **Daily digest.** One summary notification per day (at your chosen hour) of the last 24h
  of alerts, grouped by type, with an unresolved count.
- **New alert types:**
  - **Pool disconnected** — the miner fell back to a backup pool (primary stratum
    unreachable); clears on reconnect.
  - **Plug cutoff triggered** — the over-temp safety cutoff switched a miner's smart plug
    off (with the temp and limit).
  - **Firmware update available** — a newer AxeOS release exists for a Bitaxe-family miner
    (checked at most daily; uses the opt-in GitHub release feed).
  - **Performance anomaly** — the statistical detector from Wave A now raises an alert on
    sustained hashrate drift or a rising reject rate, scanned ~every 15 min in the
    background.

## 0.51.0

Wave A — telemetry insights:

- **Per-chip / per-chain health.** For Antminer-class miners (stock Bitmain, VNish) that
  report it, the miner detail screen now shows a per-board table: hashrate, working/total
  chips, failed-chip count (highlighted red), hardware errors, and hottest chip temp —
  flagging a dead or underperforming board before it drags the fleet total down. Parsed
  only from fields present in real captures (`chain_rate/acn/acs/hw`, `temp_chip`); miners
  that don't report per-chain data simply don't show the section.
- **Efficiency (J/TH) trend chart.** A new detail chart plots efficiency over the selected
  history window (min / now / max), computed from the power and hashrate already stored —
  fleet efficiency was already on the dashboard totals.
- **Statistical anomaly detection.** A pure detector flags *gradual* trends a fixed
  threshold misses — sustained hashrate drift (recent vs. baseline average) and a creeping
  reject rate — shown as an advisory on the detail screen. (Alerting on these lands in the
  next wave.)

## 0.50.0

- **First feature wired to the advanced-features gate.** The dashboard menu's **"Live
  Bitcoin"** link is now revealed only when advanced features are unlocked
  (Settings → Advanced features). It stays visible for everyone while
  `ADVANCED_FEATURES_FREE` is `true` (the shipping default), so there's no change in normal
  builds — this just proves the gate end-to-end and is the pattern future advanced features
  (secure pages, power tools) will follow.

## 0.49.0

- **UI Theme (accent color).** Settings → Display → **UI Theme** now offers six accent
  colors — **Blue (default), Green, Orange, Yellow, Red, Purple** — applied live across the
  whole app. Only the accent changes; the command-center greys and the online/offline
  status colors stay constant so status always reads the same. Works in both light and dark
  mode (each color is contrast-tuned per mode), persists across restarts, and defaults to
  the original blue so existing installs look unchanged.

## 0.48.0

- **Wear OS tile.** A new `:wear` module adds a glanceable watch **tile** (and a small
  companion app) showing the fleet's total hashrate, online/total count, and worst chip
  temp. The phone publishes a tiny summary — totals and counts only, no addresses,
  credentials, or per-worker data — over the local **Wearable Data Layer** (Bluetooth/Wi-Fi
  to the paired watch, nothing over the internet) on each poll; the tile refreshes when it
  arrives. *Built and compiled, but not yet verified on a physical watch.*
- **Wave 3 write paths are documented as hardware-gated.** VNish and Bitmain **pool
  changes** and **Bitaxe OTA** stay unsupported (shown as such) until each is verified on a
  real device — see docs/DEVICE_MATRIX.md → "Pending hardware verification". No invented
  endpoints.

## 0.47.0

- **Scheduled smart-plug on/off.** Schedules gained **Plug off** and **Plug on** actions
  that switch each target miner's configured plug over the LAN — pair a night-off with a
  morning-on for time-of-use power control. Miners without a plug are skipped; every run is
  recorded. (A scheduled Plug on is the one path by which the app turns a plug back on on
  its own — a deliberate opt-in; see docs/SECURITY.md.)
- **Rack & site layout view.** A new screen arranges miners by their **Location**
  (room / rack / shelf) with live per-tile status, hashrate and temperature, and a
  per-location up/total + hashrate rollup.
- **Wall / TV mode.** A full-screen, glanceable, read-only kiosk board for a spare
  phone, tablet, or **Android TV** left on the shelf — big fleet total and per-location
  tiles; keeps the screen awake while shown. The app now installs on Android TV
  (leanback launcher, no touchscreen required).
- **Advanced-features gate (scaffold).** Groundwork for a future licensing model: an
  offline unlock-code check (Settings → Advanced features). **All advanced features remain
  unlocked and free in this release** — nothing is locked; a later build can flip the
  default to require a code.

## 0.46.0

- **Push alerts to your own webhook.** Mirror active alerts to **ntfy, Gotify, Telegram,
  or a generic JSON POST** so they reach you when the app is closed — no cloud account of
  ours. Configure in Settings → Push (webhook). Off by default.
- **Notification quick-actions.** Active alert notifications now carry **Reboot** and
  **Acknowledge** buttons, so you can act without opening the app (reboot uses the miner's
  verified control path).

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
