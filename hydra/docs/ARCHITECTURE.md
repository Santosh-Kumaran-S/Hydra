# Hydra — Architecture decisions (Phase 1)
- Android, Kotlin, Compose + Material 3, MVVM + repositories, Room, DataStore. minSdk 26 (proposed), targetSdk current.
- `algorithm` is a pure-JVM module: no Android imports, unit-testable with `./gradlew :algorithm:test`.
- Watch: boAt Wave Style (proprietary BLE, synced via boAt Crest). No Health Services; no documented standard HR profile.
  Plan: `HeartRateDataSource` -> Health Connect reader, usable ONLY if Crest writes heart rate there (unverified; check on device).
  Otherwise the app runs as a tracker + baseline reminders. No fake/simulated data in production.
- Reminders: WorkManager is not exact enough; use AlarmManager (setExactAndAllowWhileIdle; USE_EXACT_ALARM/SCHEDULE_EXACT_ALARM only if justified),
  re-armed on BOOT_COMPLETED, TZ change and settings change. Full-screen intent only for the alarm screen, per Android policy.
- Fluid target: clinician-entered only. Weight/height are never used to derive a target.
## Not yet verified
Algorithm tests have not been run in this environment. Health Connect/Crest data flow needs her phone.

## Phase 2 status
Implemented (uncompiled): Room entities/DAOs/repository, validation, 5-tab Compose UI, water log (add/edit amount/delete, duplicate guard),
profile + settings, schedule preview using the algorithm module. Navigation is a simple state-based tab switch.
Not yet: notifications/alarms/boot receiver (Phase 3), Health Connect + weather (Phase 4), export, time editing of entries, UI tests.
No Gradle wrapper is included: open in Android Studio (it syncs with its bundled Gradle) or run `gradle wrapper`.

## Phase 3 status (uncompiled, untested on device)
Single-alarm design: one PendingIntent, always re-armed by `ReminderPlanner.recompute()` (on app open, after any log/edit/delete/profile change,
after boot/app-update/time/time-zone change, and right after each reminder fires). Rules enforced in `ReminderAlgorithm.scheduleAfterFiring`:
no re-fire within one interval of the last notification, never in the past, never in quiet hours (deferred to wake; missed alerts are not replayed).
Notification actions: "Log 250 mL" (explicit tap only), "Snooze 10 min" (max 3 chained, then normal schedule), swipe-away = DISMISSED, no water logged.
Opening the notification shows ReminderActivity; nothing is logged until "I've Drunk Water — Done".
Decisions: no USE_FULL_SCREEN_INTENT or foreground service. Exact alarms used only if allowed, else inexact fallback.
Snooze/lastFired state is in SharedPreferences (DataStore migration is a TODO). Room is still version 1 with no migrations (pre-release).
Device test checklist: foreground / background / swiped-away app / reboot / airplane / DND / battery-saver / time-zone change / sleep window.

## Phase 4 status (uncompiled, untested on device)
- Heart rate: `HealthConnectHeartRateSource` reads the newest HeartRateRecord sample (last 3 h) that another app wrote to Health Connect.
  Stored only when newer than the last stored reading; shown as "synced, not live"; stale after 30 min (ignored by the algorithm).
  UNVERIFIED whether boAt Crest writes heart rate to Health Connect — check on her phone. Dependency version 1.1.0-alpha10 may need bumping.
  Background reads (alarm time) are attempted but may be refused by Android; the app then uses stored data until it goes stale.
- Environment: Open-Meteo by typed city (no key, no location permission) or manual entry. Max one request per 15 min, observation reused 60 min,
  fetched at app open and reminder time only. Source, kind (OBSERVATION/MANUAL) and age are always shown. Stale after 2 h.
- Both feed `calculateNextReminderTime`; missing/stale inputs are ignored and listed, never invented. UI: Sensors card on Home +
  Smartwatch and Weather screens (opened from Home).
- Still open: activity input (none), reminder-acknowledgement insights, export-data, DataStore migration, UI tests, real-device verification.

## Phase 5 status
Reminder insights (InsightsMath, tested), data-completeness line, JSON export, first-launch welcome card, 3 UI tests, CI workflow, README and docs.
