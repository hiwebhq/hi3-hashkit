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

`./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
(R8-minified + resource-shrunk, ~1.7 MB, signed).

Signing reads `keystore.properties` (storeFile/storePassword/keyAlias/keyPassword) from,
in order: `$HI3_KEYSTORE_PROPERTIES`, `~/.config/hi3/keystore.properties`, or (legacy) the
repo root. `storeFile` is resolved relative to the properties file, so keep
`hi3-release.jks` beside it. The canonical location is **`~/.config/hi3/`, directory mode
700, both files mode 600** — outside the project tree so a Time Machine / cloud sync of the
repo folder never carries signing material. **Back both up offline somewhere safe; losing
them means future releases can't update existing installs.** Without the properties file
the release build is simply unsigned. Never commit keystores or passwords.

`./gradlew releaseChecksum` builds the release and writes
`app/build/outputs/apk/release/app-release.apk.sha256`; upload that file as a second
release asset so users (and the audit trail) can verify the APK.

Note: a debug install cannot be upgraded in place by a release build (different
signatures) — installing the release APK over the debug app requires an uninstall,
which deletes local history. Pick one signature per phone.
