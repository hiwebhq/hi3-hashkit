# Hi3 Miner Watch

A privacy-first, local-first Android app for monitoring (and, in later phases, safely
controlling) Bitcoin miners in home labs and small mining environments. Works entirely
on your local network — no account, no cloud — and reaches miners over an existing
Tailscale VPN when your Android device is on the tailnet.

**Status: Phase 1 vertical slice.** Bitaxe / ESP-Miner monitoring is live; Canaan
support and controls arrive in later phases. See `docs/` for architecture, device
support, build, and security details.

## Quick start

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17 2>/dev/null || echo /opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home)
./gradlew test assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

Install: `adb install app/build/outputs/apk/debug/app-debug.apk`, or copy the APK to
the phone and open it (enable "install unknown apps" for your file manager).

## What v1 does not do

- No data ever leaves your device except HTTP requests to the miner IPs you configure.
- No connection to pool.hi3.cc or mmp.hi3.cc (future, opt-in; interfaces are stubbed and disabled).
- No background monitoring yet — polling runs while the app is open (Phase 2 adds an
  explicit foreground-service option).
