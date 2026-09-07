# Hi3 Miner Watch

A privacy-first, local-first Android app for monitoring (and, in later phases, safely
controlling) Bitcoin miners in home labs and small mining environments. Works entirely
on your local network — no account, no cloud — and reaches miners over an existing
Tailscale VPN when your Android device is on the tailnet.

**Status: v1 complete (Phase 5 hardening done).** Bitaxe / ESP-Miner monitoring and safe controls (reboot, pool
change, fan, firmware-bounded tuning with rollback), Canaan Avalon monitoring via the
CGMiner TCP API, explainable health scores, alerts with recovery notifications,
background monitoring, solo-mining odds, mDNS discovery — plus fleet functions:
groups/search/tags, bulk actions with per-device capability preview and honest
partial-failure reporting, local schedules, telemetry CSV export, JSON backup/restore,
a redacted diagnostics bundle, and user-defined scan subnets for Tailscale-routed
sites. See `docs/` for architecture, device support, build, and security details.

## Quick start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
./gradlew test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install: `adb install app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to
the phone and open it (enable "install unknown apps" for your file manager).

## Privacy behavior

- No data leaves your device except requests to the miner IPs you configure, plus two
  strictly opt-in integrations (both off by default): a network-difficulty fetch and
  read-only Hi3 Pool stats keyed by your payout address (see docs/SECURITY.md).
- No connection to mmp.hi3.cc (future, opt-in; interface stubbed and disabled).
- No background monitoring yet — polling runs while the app is open (Phase 2 adds an
  explicit foreground-service option).
