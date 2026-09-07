# Scale, performance & known limits

Version 1 targets home labs and small mining environments. These are the measured and
calculated limits — not marketing claims of unlimited scalability.

## Measured: poll pipeline (JVM loopback simulation, 2026-09-06)

`ScaleBenchmarkTest` spins up N lightweight HTTP servers each serving a real recorded
Bitaxe response, then polls them through the app's actual adapter + parser with the
production concurrency (32):

| Simulated miners | Full poll cycle |
|---|---|
| 1 | 2 ms |
| 10 | 3 ms |
| 50 | 13 ms |
| 100 | 19 ms |
| 250 | 37 ms |

Parser cost: **~23 µs per response** (99-key real fixture). The app's own pipeline is
therefore nowhere near the bottleneck at 250 miners; real-world limits come from
Wi-Fi round-trips, miner responsiveness (the Avalon Nano 3's API is single-threaded
and occasionally drops a query under load), Android scheduling, and battery. The
simulation does not measure those.

## Calculated: database growth

A telemetry row is ~350 bytes including SQLite and index overhead.

| Scenario | Rows/day | Growth/day |
|---|---|---|
| 8 miners, app open 24/7 at 15 s | 46k | ~16 MB |
| 8 miners, background monitoring only (15 min) | 768 | ~0.3 MB |
| 100 miners, 60 s polling 24/7 | 144k | ~50 MB |
| 250 miners, 60 s polling 24/7 | 360k | ~126 MB |

Defaults (15 s foreground poll, 30-day retention) are sized for intermittent use of a
small fleet. For a continuously-open dashboard on 50+ miners, raise the poll interval
(Settings → Monitoring) and/or lower retention. Raw API bodies are pruned after ~1 h
regardless. Downsampled hourly/daily aggregates are future work — until then, long
retention with many miners simply stores every sample.

## Discovery

A /24 scan probes 254 hosts × 2 adapter ports (HTTP 80, CGMiner 4028) at concurrency
32 with a 3 s connect timeout. Worst case (every host silently dropping packets)
≈ 45–50 s; typical home LANs answer or refuse quickly and finish in a few seconds.
Ranges wider than /22 are refused. Scans are cancelable at any time.

## Battery (qualitative — not yet measured on-device)

- Foreground polling runs only while the app is visible; closing the app stops it.
- Background monitoring is WorkManager-based (≥15 min, deferrable by Android) and is
  off by default.
- No wake locks, no foreground service, no GPS. The dominant cost while open is the
  poll interval — raise it if the app lives on a wall-mounted tablet.
- On-device battery measurement is an open item for a future release.

## Practical v1 guidance

- **Sweet spot: 1–50 miners.** Everything at defaults.
- **50–100 miners:** raise foreground polling to 30–60 s; consider 7–14 day retention.
- **100–250 miners:** works in simulation; UI list rendering and DB growth are the
  expected pressure points on older phones. Use 60–120 s polling, short retention,
  and expect discovery scans to take longer. Not yet validated on real hardware at
  this scale.
- Above 250 miners: untested and out of scope for v1.

## Other known limitations (v1)

- Canaan controls (work mode, fan, reboot) unverified → monitoring only.
- Remote Canaan units require a Tailscale subnet route advertised at their site.
- Per-miner alert threshold overrides, hourly/daily downsampling, PDF reports,
  drag-and-drop ordering, grid layout: not yet implemented.
- Miner log streaming (`/api/ws`) not yet implemented.
- Schedules fire at poll/background granularity, not exact times.
- Release build is R8-minified and signature-verified but has not yet had a full
  on-device regression pass (debug builds have).
