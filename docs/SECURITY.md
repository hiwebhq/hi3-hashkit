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
3. **Optional, off by default — Pool stats:** while "Pool stats" is enabled in
   Settings, the app sends read-only GETs to the pool you select, about once a minute
   while the app is open. Verified endpoints per pool:
   - **Hi3 / Public Pool** (public-pool software): `/api/client/{address}`, `/api/network`
     (Hi3 also `/sproxy-api/api/v1/sessions` for per-rig correlation).
   - **CKPool**: `raw.stats.ckpool.org/users/{address}`.
   - **OCEAN**: `api.ocean.xyz/v1/user_hashrate_full/{address}`.
   The only user data transmitted is the configured address/subaccount (and an optional
   watcher token if you set one) in the request path. No miner telemetry, local IPs, or
   worker passwords are sent. Public pool hosts must be HTTPS; plain HTTP is accepted
   only toward private/Tailscale addresses (local stage instances). Disabling the
   toggle stops all pool requests immediately.

4. **Optional, off by default — Hi3 MMP:** while "Hi3 MMP fleet view" is enabled in
   Settings, the app sends read-only GETs (`/api/v1/fleet/summary`,
   `/api/v1/fleet/by-site`) to the configured MMP URL (default `https://mmp.hi3.cc`)
   about once a minute while the app is open, authenticated with the user's MMP API
   key in the `Authorization: Bearer` header. Nothing is uploaded — the phone never
   acts as an MMP agent. The API key is stored encrypted with a non-exportable
   AES-256 key in the Android Keystore (`KeystoreCrypto`); the same HTTPS-for-public
   -hosts rule applies. Disabling the toggle stops all MMP requests immediately.

There is no analytics SDK, no advertising, no account requirement, and no contact
with any other server.

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

## Canaan/Avalon controls

Avalon control uses the miner's own CGMiner `ascset` API on port 4028, which requires
privileged API access (granted to LAN clients by the miner's `api-allow` config). The
app only issues commands whose behavior was verified against a real device: pause
(`softoff`), resume (`softon`), and reboot (`reboot,0`). Fan speed, work level, and
frequency are deliberately withheld because their argument ranges are unverified and a
wrong value could change power or thermal state — exactly the guessing the project
forbids. The app never sends the miner's web-UI password.

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

## Phase 5 security & permission review (2026-09-06)

Reviewed and confirmed:

- **Permissions are minimal**: INTERNET, ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE,
  POST_NOTIFICATIONS (runtime, contextual). No location, camera, storage, contacts,
  or foreground-service permissions. `allowBackup` keeps Android's standard app
  backup; the DB contains miner telemetry and addresses but no passwords.
- **Network boundary**: every outbound connection (HTTP and raw TCP) passes
  `MinerHostValidator` — private/CGNAT/link-local/loopback only, re-checked after DNS
  resolution. Unit-tested edge cases include 172.32.0.1 and 100.128.0.1 (just outside
  the allowed ranges).
- **No credential storage**: the app stores no miner passwords, pool passwords, or
  API keys anywhere (v2.15 pool edits echo the firmware's mask instead of ever
  handling the real password). Android Keystore-backed encryption is therefore not
  yet needed and will be added with the first feature that stores a secret (Canaan
  web-CGI auth).
- **Exports**: share-sheet only; CSV redacts workers, diagnostics redact IPs by
  default; backup documents that it contains addresses/workers.
- **Release build**: R8-minified, resource-shrunk, signed with a locally-generated
  4096-bit RSA key (`hi3-release.jks` + `keystore.properties`, both gitignored and
  verified untracked). `apksigner verify` passes.
- **Dependencies**: pinned via the version catalog — AGP 8.9.2, Kotlin 2.1.10,
  Compose BOM 2025.04.01, OkHttp 4.12.0, Room 2.7.1, Hilt 2.55,
  kotlinx.serialization 1.8.0, DataStore 1.1.4, WorkManager 2.10.0. All are
  maintained mainstream releases with no known-critical CVEs at review time; this was
  a manual review — wiring an automated scanner (e.g. OWASP dependency-check or
  `gradle dependencyUpdates`) into CI is recommended when CI exists.

## CI & dependency scanning

`.github/workflows/ci.yml` runs the unit tests and a debug build on every push/PR and
submits the resolved Gradle dependency graph to GitHub, so Dependabot flags CVEs in
direct and transitive dependencies. `.github/dependabot.yml` opens weekly security and
version-update PRs. These activate automatically once the repository is pushed to
GitHub; until then, dependency review remains the manual pinned-version audit above.

## Known gaps (tracked)

- Backups are plaintext JSON unless a passphrase is set at export (then AES-256-GCM
  via PBKDF2 — see BackupCrypto).
