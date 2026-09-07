# Security & permissions

## What leaves the Android device

In this build: **only HTTP GET requests to the miner addresses you add or scan**, all
of which must be private (RFC 1918), link-local, or CGNAT/Tailscale (100.64/10)
addresses. There is no analytics SDK, no advertising, no account, and no contact with
pool.hi3.cc, mmp.hi3.cc, or any other Hi3 or third-party server. Future external
fetches (BTC price, network difficulty) will be individually documented and
disableable before they ship.

## Permissions

| Permission | Why |
|---|---|
| `INTERNET` | HTTP polling of miners on the LAN/tailnet |
| `ACCESS_NETWORK_STATE` | Detect the active network before scanning |
| `ACCESS_WIFI_STATE` | Read the Wi-Fi interface address to derive the default scan /24 |

No location, no camera, no contacts, no background-location, no foreground service (yet).

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
