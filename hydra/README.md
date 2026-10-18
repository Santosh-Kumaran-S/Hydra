# Hydra: hydration reminder app (Android)

A water-intake tracker and reminder app. It is a **routine-reminder tool**: it does not measure hydration, diagnose anything, or
calculate a medical fluid target. Any daily target must come from her healthcare professional (she has kidney stones, so this matters).

## Status: read this first
All code was written without being compiled or run (no Android SDK or network in the authoring environment).
**Nothing has been tested on a device.** Expect a few compile fixes on the first Gradle sync.

| Area | State |
|---|---|
| Profile, validation, water log (add/edit amount/delete, duplicate guard), Room persistence | Written, untested |
| Reminder algorithm (pure Kotlin, 25 unit tests) | Written, tests not yet run |
| Alarm scheduling, notification actions, reminder screen, boot/time-zone re-arming | Written, needs real-device test |
| Weather (Open-Meteo by city) and manual temperature/humidity | Written, untested |
| Heart rate via Health Connect | Written; **depends on boAt Crest writing to Health Connect (unverified)** |
| Insights, JSON export, delete-all | Written, untested |
| UI tests (3) | Written, need a device/emulator |

## Build and install
**Option A, Android Studio:** File → Open → this folder → wait for Gradle sync → Build → Build APK(s) →
`app/build/outputs/apk/debug/app-debug.apk`. Copy to the phone, allow "install unknown apps", install.

**Option B, no PC setup:** push this folder to a GitHub repo. `.github/workflows/build-apk.yml` builds the APK in the cloud;
download it from the run's Artifacts.

Tests: `gradle :algorithm:test :app:testDebugUnitTest` (JVM) and `./gradlew :app:connectedDebugAndroidTest` (device).
Requirements: JDK 17, Android SDK 35, minSdk 26 (Android 8.0+). No Gradle wrapper is included.

## First use
1. Profile: height, weight, wake/sleep times, time zone; the clinician's fluid target only if she has one.
2. Settings → turn on Water reminders (allow notifications; allow exact alarms if offered).
3. Optional: Home → Weather (type a city) and Smartwatch (Health Connect).

## Permissions (only what is used)
| Permission | Why |
|---|---|
| POST_NOTIFICATIONS | show reminders |
| SCHEDULE_EXACT_ALARM | on-time reminders; falls back to inexact if not allowed |
| RECEIVE_BOOT_COMPLETED | re-arm the reminder after reboot |
| VIBRATE | reminder vibration |
| INTERNET | weather lookup for a typed city |
| READ_HEART_RATE (Health Connect) | optional heart-rate input |

Not requested: location, Bluetooth, full-screen intent, foreground service, background health read.

## Layout
`algorithm/` pure-Kotlin scheduler (no Android imports) and tests · `app/.../data` Room · `.../reminders` alarm, notifications, planner ·
`.../sensors` Health Connect and weather · `.../ui` Compose screens · `.../profile` validation · `docs/` design, compatibility, privacy, validation.

See `docs/COMPATIBILITY.md`, `docs/PRIVACY_AND_LIMITATIONS.md`, `docs/VALIDATION.md`, `docs/ARCHITECTURE.md`.
