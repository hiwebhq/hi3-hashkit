# Build instructions

## Prerequisites (macOS, as set up on this machine)

- JDK 17: `brew install openjdk@17`
- Android SDK: `brew install android-commandlinetools`, then
  `sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"`
- `local.properties` must contain `sdk.dir=/opt/homebrew/share/android-commandlinetools`
  (already present locally; never committed).

## Commands

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
./gradlew test            # unit tests (parser fixtures, discovery bounds, units)
./gradlew assembleDebug   # -> app/build/outputs/apk/debug/app-debug.apk
```

## Installing on a phone

- With USB debugging: `adb install app/build/outputs/apk/debug/app-debug.apk`
- Without: copy the APK to the phone (AirDrop-equivalent, USB, or local file share) and
  open it; allow "install unknown apps" when prompted. The debug build is signed with
  the standard debug key.

## Release builds

`./gradlew assembleRelease` produces an unsigned release APK with R8 enabled. Release
signing uses a local `keystore.properties` (gitignored); generating and wiring the
signing config is part of Phase 5. Never commit keystores or passwords.
