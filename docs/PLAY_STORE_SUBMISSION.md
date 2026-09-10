# Play Store submission prep

Working checklist + paste-ready text for publishing Hi3 Hashkit to Google Play.

> These are drafts based on the app's actual behavior. You are responsible for the final
> declarations in the Play Console; Google may ask follow-up questions.

Current build target: **versionName 0.65.0 / versionCode 70**, `targetSdk 35`.

---

## 0. Status / blockers (account-level — not code)

- [ ] **Developer identity verification** — was in Google's queue last we checked; the
      "verify phone number → account details" button stays greyed until this clears.
- [ ] **New personal accounts:** closed test with **≥20 testers for ≥14 days** before you
      can request production access. Plan for ~2+ weeks after verification.

## 1. Technical build (mostly done)

- [x] Signed release **AAB**: `./gradlew bundleRelease` →
      `app/build/outputs/bundle/release/app-release.aab` (R8-minified, resource-shrunk,
      signed with `hi3-release.jks`; `jarsigner -verify` passes).
- [x] Hidden dev link (7 logo taps) compiled out of release via `BuildConfig.EASTER_EGG=false`.
- [x] `targetSdk 35`; minimal permissions.
- [ ] Enroll in **Play App Signing** on first upload.
- [ ] Upload the AAB to a track (internal → closed → production).

## 2. Store listing checklist

- [ ] App title, short description, full description (drafts in §5).
- [ ] App icon (512×512), feature graphic (1024×500).
- [ ] Phone screenshots (min 2; use **Demo mode** so no real miners are shown), optional
      7"/10" tablet screenshots.
- [ ] Category: Tools (or Productivity). Contact email + website.
- [ ] **Privacy policy URL** — host `PRIVACY_POLICY.txt` (or an HTML version) at a public
      URL and paste the link.
- [ ] Content rating questionnaire.
- [ ] Data Safety form (§3).
- [ ] Ads: **No ads**. Target audience: adults (mining hardware tool, not for children).
- [ ] Special-Use FGS declaration (§4).

---

## 3. Data Safety form (paste-ready)

The app is genuinely local-first, so the cleanest honest answer is **"no data collected or
shared."**

### Recommended answers

**"Does your app collect or share any of the required user data types?"** → **No**

Rationale (keep for your records / if Google asks):

> The developer operates no server and receives no user data. All data (miners, telemetry,
> settings, notes, photos, any miner credentials) is stored only on the user's device;
> credentials are encrypted with the Android Keystore and are never transmitted to the
> developer. The app's only off-device transmissions are (a) to the user's own miners and
> smart plugs on their local network/VPN, and (b) to third-party services the user
> explicitly enables and configures (their mining pool, mempool.space, GitHub, their own
> push endpoint, their own MQTT broker). These are user-initiated transfers to destinations
> the user chooses, and the developer neither receives nor processes them.

**Security practices:**

- **Is your data encrypted in transit?** → **Yes** — connections to public endpoints
  (mempool.space, GitHub, pools, push providers) use HTTPS. (For your records: connections
  to local miner firmware use cleartext on the private LAN only, because miner firmware
  doesn't offer TLS; this is enforced to private addresses.)
- **Way to request data deletion?** → Data is stored only on the device; there is no account
  or server. Uninstalling deletes all data; deleting a miner deletes its data. (With "data
  isn't collected," this may not apply.)
- **Directed at children / Families policy?** → **No.**

### Fallback (only if you prefer to disclose the opt-in integrations)

Declare these as **optional, user-initiated, not collected by the developer**:

| Data type | Collected/Shared | Purpose | Notes |
|---|---|---|---|
| Financial info → "other financial info" (BTC payout address) | Shared (optional) | App functionality (pool stats) | Only to the mining pool the user configures; user-initiated |
| App info & performance (miner telemetry) | Shared (optional) | App functionality | Only to the user's own MQTT broker / push endpoint |

Everything else (photos, notes, credentials, device IDs) → **not collected, not shared**
(on-device only).

---

## 4. Special-Use Foreground Service justification (paste-ready)

For the `FOREGROUND_SERVICE_SPECIAL_USE` declaration (manifest subtype:
`miner_thermal_safety_cutoff`):

> Hi3 Hashkit uses a special-use foreground service solely for an optional, user-enabled
> thermal safety cutoff for Bitcoin-mining hardware. When the user configures a smart plug
> for a miner and turns on the always-on safety monitor, the service periodically reads the
> miner's chip temperature and, if it exceeds the user's configured limit, switches that
> miner's smart plug OFF to prevent overheating and fire risk. To function as a safety
> mechanism it must run continuously in the background — even when the app is closed and
> after a device restart. No standard foreground-service type accurately describes
> continuous hardware thermal-protection polling: it is not media playback, location, data
> sync, phone call, camera, microphone, or health. The service displays a persistent
> notification while active, performs only this over-temperature cutoff, does nothing else,
> and is disabled by default.

**Note:** Google scrutinizes special-use FGS hardest. If rejected, a reasonable alternative
framing is `connectedDevice` (the service continuously interacts with external LAN devices —
the miner and its smart plug). Submit as `specialUse` first; keep `connectedDevice` as the
fallback.

---

## 5. Store listing copy (draft)

**App name (≤30 chars):**

```
Hi3 Hashkit
```

**Short description (≤80 chars):**

```
Private, local-first Bitcoin miner monitoring & control. No account. No cloud.
```

**Full description (≤4000 chars):**

```
Hi3 Hashkit is a privacy-first, local-first Android app for monitoring and safely
controlling your Bitcoin miners — from a single Bitaxe on your desk to a multi-site farm of
Antminers. No account. No cloud. No analytics. No ads. It talks only to the miners on YOUR
network — over your LAN or your own Tailscale VPN — and every reading is honestly labeled
(measured, reported, calculated, or estimated), so an estimate is never dressed up as a fact.

FIND EVERY MINER
• Auto-discovery scans your subnet at launch, plus mDNS/DNS-SD — or add by IP/CIDR.
• Group miners into farms/sites, each with its own subnet and refresh interval.

MONITOR THE TRUTH
• Live dashboard: hashrate, temps, power, efficiency and health per miner, with fleet
  totals, trend charts and sparklines.
• Sortable fleet table, per-chip/per-board health (Antminer-class), efficiency (J/TH) trend,
  and statistical anomaly detection that catches gradual drift a threshold misses.
• Profitability & energy estimates, a Bitcoin network + halving countdown card, a rack/site
  layout, a wall/TV kiosk mode, a 3D flow view, and a home-screen widget.

CONTROL — ONLY WHERE VERIFIED
• Reboot, pool change, fan control and firmware-bounded tuning, with confirmations and
  rollback — plus bulk actions and a fleet-wide Panic (pause/reboot all). Unverified controls
  are shown as unsupported, never guessed.
• Automation rules engine: if temp/hashrate/reject/offline, then pause/resume/reboot/plug
  on-off/notify.
• Bitaxe auto-tuner: sweeps firmware-approved frequencies under a temperature ceiling and
  recommends the best point for efficiency or hashrate.
• Schedules (incl. time-of-use power presets), a pool/wallet address book, and auto-recover.

ALERTS & INTEGRATIONS
• Alerts for offline, hot chips/VR, fan stall, reject rate, pool disconnect, plug cutoff,
  firmware-available and a solo block-found — with per-miner overrides, quiet hours and a
  daily digest.
• Push to your own webhook (ntfy/Gotify/Telegram/generic), publish to Home Assistant/MQTT,
  a local web dashboard + Prometheus endpoint, and a Wear OS tile.

PRIVATE BY DESIGN
• Nothing leaves your device except requests to your own miners and strictly opt-in,
  off-by-default integrations you configure yourself.
• Per-miner passwords/tokens are encrypted with the Android Keystore; Android auto-backup is
  disabled; the camera is used only for on-demand QR scanning.

Requires Android 8.0+. Miners reachable on your LAN or over Tailscale.

Hi3 Hashkit is not affiliated with any miner or pool vendor. Cryptocurrency mining involves
risk; controls are provided as-is — you are responsible for your hardware.
```

---

## 6. Privacy policy

Source text: `PRIVACY_POLICY.txt` (repo root). Play requires a **public URL** — host it
(GitHub Pages, your site, etc.) and paste the link into the listing. Fill in the two
placeholders first: developer name and contact email.

## 7. Screenshots

Turn on **Settings → Demo mode** to populate a realistic fleet with clearly-labeled
synthetic miners, then capture the dashboard, a miner detail (per-chip health), the fleet
table, and the wall/TV mode. No real miner addresses are shown in demo mode.
