# Security & permissions

## What leaves the Android device

In this build:

1. **Requests to the miner addresses you add or scan** — HTTP on port 80 for
   ESP-Miner devices (GET for telemetry; PATCH/POST for controls you explicitly
   confirm) and read-only CGMiner TCP API queries on port 4028 for Canaan/Avalon
   devices. All destinations must be private (RFC 1918), link-local, or
   CGNAT/Tailscale (100.64/10) addresses; discovery probes only these two ports.
2. **Optional, off by default:** one HTTPS GET to `mempool.space` to fetch network
   difficulty for solo-mining odds. It carries no miner data, no identifiers beyond
   the connection itself, and runs only while "Fetch network difficulty" is enabled in
   Settings (manual difficulty entry works without it).

There is no analytics SDK, no advertising, no account, and no contact with
pool.hi3.cc, mmp.hi3.cc, or any other Hi3 server.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | HTTP polling of miners on the LAN/tailnet |
| `ACCESS_NETWORK_STATE` | Detect the active network before scanning |
| `ACCESS_WIFI_STATE` | Read the Wi-Fi interface address to derive the default scan /24 |
| `POST_NOTIFICATIONS` | Miner alert notifications (requested contextually when alerts/background monitoring are enabled) |

No location, no camera, no contacts, no background-location, no foreground service (yet).

## Exports & backups

All exports are generated locally and leave the device only through the Android share
sheet to a destination the user picks. CSV telemetry redacts worker names; the
diagnostics bundle redacts IP addresses (opt-in to include) and never contains
passwords, wallets, or Tailscale material. The JSON backup contains miner addresses
and worker names (it exists for the user's own restore) and the UI says so.

## Control-action safety

Every control (reboot, pool change, fan, tune) requires an in-app confirmation showing
current → proposed values and risks; is capability-gated per device and re-verified
against the live firmware immediately before sending; and is recorded in a local audit
table with the previous values. Tuning offers only the frequency/voltage values the
device's own firmware publishes as valid (`GET /api/system/asic`) and supports one-tap
rollback to the audited previous values. Pool edits on v2.15+ echo the firmware's
password mask so stored pool passwords are never read, displayed, or overwritten by
the app (locked in by a request-payload test).

## Cleartext HTTP tradeoff

Miner firmware (ESP-Miner et al.) serves plain HTTP with no TLS option, so cleartext
must be permitted. Android's network security config cannot whitelist IP ranges, so the
enforceable boundary is in code: `MinerHostValidator` refuses any connection to a
non-private address (checked again at request time, including after DNS resolution of
hostnames), and discovery refuses public CIDRs and ranges wider than /22. HTTPS with
system trust anchors remains the default for any future non-miner traffic.

## Data handling

- Telemetry history is stored locally in Room; deleting a miner deletes its data;
  uninstalling the app deletes everything.
- Raw firmware responses are retained ~1 hour for diagnostics only.
- The raw-response viewer redacts credential-bearing fields (worker/wallet, Wi-Fi SSID)
  before display.
- Committed test fixtures are redacted (MACs, SSIDs, wallets, pool hosts replaced).
- No secrets are committed: signing keys, `local.properties`, and keystores are
  gitignored. Keystore-backed credential encryption ships with the first feature that
  stores a credential (Canaan auth, Phase 3).

## Known gaps (tracked for the hardening phase)

- App lock (biometric/PIN) not yet implemented.
- Backup/restore encryption not yet implemented (no sensitive data stored yet).
- Dependency vulnerability review scheduled for Phase 5.
