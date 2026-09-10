# Hi3 Hashkit

### Your whole mining fleet, in your pocket. On your network. Nobody else's.

**Hi3 Hashkit** is a privacy-first, local-first Android app for monitoring and safely
controlling Bitcoin miners — from a single Bitaxe on your desk to a multi-site farm of
Antminers. No account. No cloud. No analytics. No ads. It talks only to the miners on
**your** network — over your LAN or your own Tailscale VPN — and every reading is honestly
labeled (measured, reported, calculated, or estimated), so an estimate is never dressed up
as a fact.

**Android 8.0+ · Optional Wear OS companion & Android TV · Sideload and go.**

---

## 🔍 Find every miner, instantly
- **Auto-discovery** scans your subnet at launch, plus **mDNS/DNS-SD** — or add by IP/CIDR.
- **Farms & sites**: group miners by location, each with its own subnet and refresh
  interval, and switch between them from the dashboard.

## 📊 Monitoring that tells you the truth
- **Live fleet dashboard**: hashrate, temps, power, efficiency and health per miner — plus
  fleet totals, trend charts, sparklines, search, and four layout densities.
- **Sortable fleet table** — a dense, spreadsheet-style view with tap-to-sort columns and a
  filter box.
- **Per-chip / per-board health** (Antminer-class): hashrate, working/dead chip counts,
  hardware errors and hottest-chip temp — catch a failing board before it drags the fleet
  down.
- **Efficiency (J/TH) trend** and **statistical anomaly detection** that flags gradual
  hashrate drift or a creeping reject rate a fixed threshold would miss.
- **Profitability & energy**: revenue/day, power cost, net, BTC/day, kWh/day and heat — plus
  a **Bitcoin network card** (block height, subsidy, next difficulty adjustment and halving
  countdown).
- A **rack/site layout view**, a **wall / TV kiosk mode** (Small/Medium/Large), a **3D flow
  view**, and a **home-screen widget**.

## 🎛️ Control — only where it's verified
Every control ships only after verification against real firmware. Unverified means
unsupported, never guessed.
- **Reboot, pool change, fan control, firmware-bounded tuning** — with confirmations, bounds
  and rollback — plus bulk actions and a fleet-wide **Panic** (pause/reboot all).
- **Automation rules engine**: *if* temp / hashrate / reject-rate / offline, *then* pause /
  resume / reboot / plug on-off / notify.
- **Bitaxe auto-tuner**: sweeps firmware-approved frequencies **under a chip-temp ceiling**
  and recommends the best point for **efficiency or hashrate**, with rollback.
- **Schedules** (including time-of-use power presets), a **pool & wallet address book**, and
  **auto-recover** for stuck miners.
- Admin passwords/tokens stored **encrypted in the Android Keystore**.

## 🔔 Alerts & integrations
- **Smart alerts** — offline, hot chips/VR, fan stall, reject rate, pool disconnect, plug
  cutoff, firmware-available, and a solo **block-found** celebration — with per-miner
  overrides, **quiet hours** and a **daily digest**.
- **Push to your own webhook** (ntfy / Gotify / Telegram / generic), with Reboot /
  Acknowledge quick-actions.
- **Home Assistant / MQTT** publish, a **local web dashboard + Prometheus `/metrics`**
  endpoint, and a **Wear OS tile** themed to your app accent.

## ⚡ Run it like an operator
- **Measured wall power from metering plugs** (Tasmota/Shelly/Kasa) — true watts and
  efficiency even for miners that don't report their own power.
- **Smart-plug over-temp safety cutoff** — cuts power if a miner overheats, with an optional
  always-on background monitor that survives reboot.
- **Efficiency leaderboard**, honest offline/stale states, local history with CSV export and
  JSON backup/restore, and a per-miner **maintenance log** (timestamped notes with photos).

## ⛏️ Supported miners
Support is by **firmware family**, verified on real hardware (✅) or against the vendor's
documented API (compat-gated):
- **Bitaxe / AxeOS** (Max, Ultra, Supra, Gamma, Gamma Turbo, Supra Hex) — full monitoring +
  reboot, pool, fan, tuning & auto-tune
- **NerdQAxe** and ESP-Miner forks — monitoring + reboot
- **Antminer** on **stock Bitmain** (reboot), **VNish** (reboot + pause/resume, incl. wall
  power), **LuxOS** (pause/resume)
- **Canaan Avalon** Nano 3 / 3S / Q / Mini 3 — monitoring + pause/resume + reboot
- **Braiins OS** (BMM 100) — monitoring + pause/resume
- **WhatsMiner**, **FutureBit**, and the **generic cgminer** long tail — monitoring

## 🔒 Private by design
An enforced network boundary refuses any non-private address, so miner traffic never leaves
your LAN/VPN. Secrets are AES-256-GCM in the Android Keystore; Android auto-backup is
disabled; the camera is used only for on-demand QR scanning. No analytics, no ads, no
account. Everything that can leave the device is opt-in and off by default — and fully
disclosed.

**Requires Android 8.0+. Miners reachable on your LAN or over Tailscale.**

*Hi3 Hashkit is not affiliated with any miner or pool vendor. Cryptocurrency mining involves
risk; controls are provided as-is.*
