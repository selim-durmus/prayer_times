# Prayer Times (PrayerTimesV2) — Project Context

This document summarizes the **Prayer Times** Android application (`com.tuttoposto.prayertimes`): purpose, stack, architecture, file layout, data/UI layers, design choices, constraints, and dependency versions. It is intended to onboard a reader (or an AI session) with **no prior exposure** to the repo.

---

## 1. Project summary

**Prayer Times** is an Android app that fetches **Islamic prayer times** for the device’s location from the **Aladhan** public API, caches them locally, and presents them in a **Jetpack Compose** UI (today’s times, optional **Hijri** date, and a **monthly calendar** view). It supports **notifications** before each enabled prayer **ends**, optional alerts when a prayer **starts** (including optional **bundled Adhan** from `res/raw`), **alarm-like** playback via a **foreground service** when the user chooses that style, **home screen widgets**, a **Qibla** screen using device sensors, and **Settings** (including **AMOLED** theme and separate notification styles for end vs start alerts).

---

## 2. Tech stack

| Area | Choice |
|------|--------|
| **Language** | Kotlin (**2.0.21**), JVM target **11** |
| **UI** | **Jetpack Compose** + **Material 3** (no XML fragments/activities beyond manifests/themes) |
| **Min SDK** | **36** |
| **Target SDK** | **36** |
| **Compile SDK** | **36** |
| **Build system** | **Gradle** with **Kotlin DSL** (`build.gradle.kts`), **version catalogs** (`gradle/libs.versions.toml`) |
| **Android Gradle Plugin** | **8.9.1** |
| **Serialization / HTTP** | **Retrofit 2.11.0**, **OkHttp 4.12.0**, **Moshi 1.15.1** (+ **KSP** codegen **2.0.21-1.0.28**) |
| **Local persistence** | **DataStore Preferences** (two separate files — see Data layer) |
| **Background work** | **WorkManager 2.10.0** (periodic sync fallback), **AlarmManager** (exact alarms for notifications + midnight sync) |
| **Location** | **Google Play services Location 21.3.0** (`FusedLocationProviderClient`) |
| **Async** | **Kotlin Coroutines 1.9.0** (`kotlinx-coroutines-core`, `-android`, `-play-services`) |
| **Navigation** | **Navigation Compose 2.8.5** |
| **Lifecycle** | **Lifecycle 2.8.7** (runtime, ViewModel Compose, runtime Compose) |
| **Compose BOM** | **2024.12.01** (pins Compose library versions) |

**Root Gradle**: `build.gradle.kts` applies plugins only at root (no submodules besides `:app`). **Settings**: `settings.gradle.kts` — `rootProject.name = "Prayer Times"`, single module `:app`.

---

## 3. Architecture overview

**Pattern:** Pragmatic **layered architecture** with **unidirectional data** from repositories → ViewModels (`StateFlow`) → Compose UI. There is **no** separate domain module; “domain” rules live in repositories, schedulers, and ViewModels.

**Layers (conceptual):**

1. **UI (`ui/`)** — Compose screens, `MainActivity`, theme. ViewModels expose `StateFlow` / `collectAsState()`.
2. **Presentation bridges** — `MainActivity` also orchestrates permission flows, initial fetch, and calls `NotificationScheduler` + `NotificationScheduleCacheRepository` after cache updates.
3. **Data (`data/`)** — API interfaces & DTOs (`data/api`, `data/remote`), repositories (`data/repository`), models (`data/models` — **canonical** types used by the app).
4. **Notifications (`notifications/`)** — `NotificationScheduler` (AlarmManager), `NotificationReceiver`, `NotificationHelper` (channels + `NotificationCompat`), `PrayerAlarmPlaybackService` (foreground alarm playback), small receivers for boot, midnight sync, dismiss.
5. **Background (`workers/`)** — `PrayerTimesSyncWorker` enqueued from `PrayerTimesApplication`.
6. **Widgets (`widget/`)** — `AppWidgetProvider` subclasses + `WidgetUpdateHelper` + minute tick receiver.

**Note:** A **legacy duplicate** tree exists under `data/model/` (singular) + `data/local/DataStoreManager.kt` — **not wired** into the main app flow (see Gotchas).

---

## 4. File / folder structure (key paths)

Root layout:

```
PrayerTimesV2/
├── app/                          # Single Android application module
│   ├── build.gradle.kts          # App module: SDK 36, Compose, dependencies
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml   # Permissions, activity, receivers, FGS service
│       ├── java/.../tuttoposto/prayertimes/
│       │   ├── PrayerTimesApplication.kt   # onCreate: channels, midnight alarm, WorkManager enqueue
│       │   ├── data/
│       │   │   ├── api/          # Retrofit Aladhan interface + JSON models (Moshi)
│       │   │   ├── remote/       # Legacy duplicate Retrofit stack (AladhanApiService) — **unused**; see §8
│       │   │   ├── models/       # **Canonical** settings, prayer, cache, logs, notification cache models
│       │   │   ├── model/        # **Legacy duplicate** — avoid for new code
│       │   │   ├── local/        # **Legacy** DataStoreManager (unused by main flow)
│       │   │   └── repository/   # SettingsRepository, PrayerTimesRepository, caches, logs
│       │   ├── notifications/    # Scheduler, receiver, helper, FGS, dismiss, boot, midnight
│       │   ├── ui/
│       │   │   ├── MainActivity.kt      # Single activity: NavHost + bottom bar + permissions + fetch
│       │   │   ├── screens/     # PrayerTimes, MonthlyCalendar, Qibla, Settings (Compose)
│       │   │   ├── viewmodels/  # PrayerTimes, Settings, MonthlyCalendar, Qibla
│       │   │   └── theme/       # PrayerTimesTheme, Typography, PrayerTimesColors
│       │   ├── widget/          # 2x1 + 1x1 widgets, update helper, alarm receiver for ticks
│       │   └── workers/         # PrayerTimesSyncWorker
│       └── res/                 # layouts (minimal), drawables, mipmap, values (themes, strings), raw (ezan), xml (widgets, backup)
├── gradle/libs.versions.toml      # Central dependency + plugin versions
├── settings.gradle.kts
├── build.gradle.kts               # Root plugin aliases (apply false)
└── PROJECT_CONTEXT.md             # This file
```

**Per-file / per-area roles (canonical `data.models` + main Kotlin):**

| Path | Role |
|------|------|
| `PrayerTimesApplication.kt` | Creates notification channels; schedules midnight sync alarm; enqueues periodic WorkManager sync. |
| `ui/MainActivity.kt` | Launcher activity: edge-to-edge Compose, location + notification + exact-alarm permission handling, fused location fetch, `PrayerTimesRepository.fetchAndCachePrayerTimes`, `NotificationScheduler.scheduleAllNotificationsSimple`, bottom nav (`prayer_times`, `qibla`, `settings`). |
| `ui/screens/PrayerTimesScreen.kt` | Main prayer list UI; integrates ViewModel state, pager sibling is calendar. |
| `ui/screens/MonthlyCalendarScreen.kt` | Monthly prayer-times calendar (Aladhan calendar API). |
| `ui/screens/QiblaScreen.kt` | Qibla direction UI using orientation/location. |
| `ui/screens/SettingsScreen.kt` | Settings UI: notifications, per-prayer toggles, dual notification styles, AMOLED, debug sections. |
| `ui/viewmodels/PrayerTimesViewModel.kt` | Prayer times tab state, cache consumption, sync triggers. |
| `ui/viewmodels/SettingsViewModel.kt` | Settings state, notification reschedule hooks, debug/test actions. |
| `ui/viewmodels/MonthlyCalendarViewModel.kt` | Month grid state and API-backed calendar data. |
| `ui/viewmodels/QiblaViewModel.kt` | Sensor / bearing logic for Qibla. |
| `ui/theme/Theme.kt` | `PrayerTimesTheme` (dark + optional AMOLED scheme), `PrayerTimesColors`. |
| `ui/theme/Type.kt` | Compose typography scale. |
| `data/api/AladhanApi.kt` | Retrofit interface: `getTimings`, `getCalendar`; Moshi data classes for responses, timings, Hijri fields. |
| `data/api/NetworkModule.kt` | **Active** Retrofit + Moshi singleton exposing `aladhanApi` (`AladhanApi`) — used by `PrayerTimesRepository`. |
| `data/remote/NetworkModule.kt`, `data/remote/AladhanApiService.kt`, `data/remote/AladhanModels.kt` | **Unused** duplicate of an older API layer; nothing in `app/src/main` references `aladhanApiService`. Safe to delete after confirmation. |
| `data/models/SettingsModels.kt` | `AppSettings`, `NotificationStyle`, `PrayerNotificationPreferences`. |
| `data/models/PrayerModels.kt` | `Prayer` enum, `PrayerTime`, `PrayerTimesCache` (includes optional `hijriDate`). |
| `data/models/NotificationModels.kt` | Schedule cache entries for debug UI / scheduler. |
| `data/models/NotificationLogModels.kt` | In-memory/logged notification events for debug. |
| `data/models/SyncLogModels.kt` | Sync history for debug. |
| `data/repository/SettingsRepository.kt` | DataStore `app_settings`: toggles, dual styles (`notification_style_end` / `_start`), legacy `notification_style` migration. |
| `data/repository/PrayerTimesRepository.kt` | DataStore `prayer_times_cache`: fetch from API, persist per-prayer start/end millis + Hijri string. |
| `data/repository/NotificationScheduleCacheRepository.kt` | Persists computed next alarm summary for Settings debug. |
| `data/repository/NotificationLogRepository.kt` | Notification event log for debug. |
| `data/repository/SyncLogRepository.kt` | Sync log persistence. |
| `notifications/NotificationScheduler.kt` | Builds `PendingIntent`s, schedules end reminders + prayer-start alarms, midnight sync, test alarms; uses `AlarmManager` exact APIs where appropriate. |
| `notifications/NotificationReceiver.kt` | Dispatches alarm intents → `NotificationHelper` + logs; handles test actions. |
| `notifications/NotificationHelper.kt` | Channel creation (incl. deleting **retired** channel IDs), `showPrayerNotification`, `showPrayerStartedNotification`, `showTestNotification`, channel ID selection for normal style. |
| `notifications/PrayerAlarmPlaybackService.kt` | Foreground `mediaPlayback` service: plays `Ringtone` on **USAGE_ALARM** until swipe dismiss (`deleteIntent` → `PrayerAlarmDismissReceiver`), timeout, or `requestStop()`. |
| `notifications/PrayerAlarmDismissReceiver.kt` | Broadcast receiver: calls `PrayerAlarmPlaybackService.requestStop` when notification dismissed. |
| `notifications/BootCompletedReceiver.kt` | Recreates channels / triggers reschedule path after reboot. |
| `notifications/MidnightSyncReceiver.kt` | Handles midnight sync intent; refresh + reschedule. |
| `workers/PrayerTimesSyncWorker.kt` | Periodic WorkManager job as backup for daily refresh. |
| `widget/PrayerWidgetProvider.kt` | 2×1 app widget host. |
| `widget/PrayerWidgetSmallProvider.kt` | 1×1 compact widget host. |
| `widget/WidgetUpdateHelper.kt` | Builds `RemoteViews` for widgets from cache. |
| `widget/WidgetUpdateReceiver.kt` | Receives periodic widget refresh intents. |

---

## 5. Data layer details

### 5.1 Persistence (no Room/SQLite)

Two **Jetpack DataStore Preferences** files:

| DataStore name (file key) | Defined in | Stores |
|---------------------------|------------|--------|
| **`app_settings`** | `SettingsRepository.kt` | Global notification on/off, per-prayer toggles, reminder offset (30–60), **separate** `notification_style_end` / `notification_style_start` (+ legacy `notification_style` migration), `notify_on_prayer_start`, `use_ezan_for_prayer_start`, debug flag, `use_amoled_theme`. |
| **`prayer_times_cache`** | `PrayerTimesRepository.kt` | Date, timezone, lat/lon, per-prayer **start/end epoch millis**, optional **Hijri date string** for display. |

**Legacy / unused:** `data/local/DataStoreManager.kt` uses **`prayer_times_prefs`** and `data.model.*` — **not referenced** by production repositories in the searched codebase; treat as dead/duplicate unless you explicitly revive it.

### 5.2 Remote API

- **Base URL:** `https://api.aladhan.com/v1/` (`AladhanApi.Companion.BASE_URL`).
- **Endpoints (in `data/api/AladhanApi.kt`):**
  - **`GET timings`** — daily timings for a timestamp + lat/lon; params `method` (default **2** = ISNA), `school` (default **1** = Hanafi Asr).
  - **`GET calendar/{year}/{month}`** — monthly grid for the calendar screen.
- **Client:** Retrofit via `data/api/NetworkModule.kt` (logging interceptor, Moshi converter). **KSP** generates Moshi adapters for annotated models.

### 5.3 Domain-style models (`data/models`)

- **`Prayer`**, **`PrayerTime`**, **`PrayerTimesCache`** — prayer windows for scheduling and UI.
- **`AppSettings`** — notification behavior; **two** `NotificationStyle` fields: `notificationStyleEndReminder`, `notificationStylePrayerStart`.
- **`NotificationStyle`** — `NORMAL` (channel + `NotificationCompat`) vs `ALARMY` (foreground `PrayerAlarmPlaybackService` + `Ringtone` on alarm stream).
- **Schedule / log models** — used for debug surfaces and scheduler cache persistence.

---

## 6. UI layer details

### 6.1 Activities

- **`MainActivity`** (only activity): Compose `setContent`, `enableEdgeToEdge`, hosts **`MainScreen`** with `NavHost` + bottom navigation.

### 6.2 Navigation

- **Bottom bar routes:** `prayer_times`, `qibla`, `settings` (`Screen` sealed hierarchy in `MainActivity.kt`).
- **Prayer Times tab:** **`PrayerTimesPager()`** — `HorizontalPager` with **two pages**: (0) `PrayerTimesScreen`, (1) `MonthlyCalendarScreen`; dot indicators at bottom.

### 6.3 Themes

- **`res/values/themes.xml`** — `Theme.PrayerTimes` extends **Material3 Dark NoActionBar**; defines gold/amber primary, dark surfaces (aligns with Compose `Theme.kt`).
- **`res/values-night/themes.xml`** — night qualifier if present (mirror dark branding).
- **Compose:** `PrayerTimesTheme(useAmoled: Boolean)` in `ui/theme/Theme.kt` switches between **dark** and **AMOLED** (`Color(0xFF000000)` background) `ColorScheme`s; **MainActivity** collects `useAmoledTheme` from `SettingsRepository` / ViewModel and passes into `PrayerTimesTheme`.

### 6.4 Fragments

**None.** 100% Compose inside `MainActivity`.

---

## 7. Key design decisions (and why)

1. **Dual notification styles (Option B)** — `notificationStyleEndReminder` vs `notificationStylePrayerStart` so “before end” and “at start” can differ (e.g. Normal vs Alarm-like independently). Persisted with migration from legacy single `notification_style` key.

2. **Alarm-like = foreground service + `Ringtone`** — Avoids relying on notification channel sounds for alarm behavior (heads-up / volume stream issues). Uses **`FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK`** and permission `FOREGROUND_SERVICE_MEDIA_PLAYBACK`.

3. **No separate “alarm” notification channels for content** — Older design created unused channels; removed. Alarm-like **does not** post to redundant high-importance content channels; only **`alarm_playback_control`** + runtime notification text.

4. **Prayer start copy is title-only** — User-facing prayer-start notifications use **`notification_prayer_started_title`** (`%1$s has begun`) only; **no** second-line marketing/ringtone text for production prayer start (normal or alarm-like).

5. **Stop button removed from alarm playback** — User dismisses by **swiping** the notification; `deleteIntent` → `PrayerAlarmDismissReceiver` → `requestStop()`. **`setOngoing(false)`** so swipe-dismiss is possible (ongoing often blocks dismissal).

6. **End-reminder alarm-like** still passes a **single** `notification_reminder_alarm_playback` string (minutes + swipe-to-stop **alarm** wording) as the **body** of the foreground notification — distinct from test strings in `strings.xml`.

7. **Exact alarms** — `SCHEDULE_EXACT_ALARM` declared; scheduling uses AlarmManager patterns suitable for prayer-time fidelity (see `NotificationScheduler`).

8. **Min SDK 36** — Extremely high floor; simplifies permission/API matrix but **excludes almost all physical devices** as of early 2026 unless they run very new Android. Treat as intentional prototype / policy or revisit for store release.

9. **Widgets + minute receiver** — Separate receiver for dense countdown updates; balance battery vs accuracy.

---

## 8. Workarounds, gotchas, constraints

1. **`minSdk = 36` / `targetSdk = 36`** — Effectively **Android 16+ only** in practice. Do not assume broad device coverage without lowering `minSdk`.

2. **Manifest vs code action names** — `AndroidManifest.xml` `<intent-filter>` for `NotificationReceiver` lists `com.tuttoposto.prayertimes.PRAYER_START` which matches `NotificationScheduler.ACTION_PRAYER_START`. Keep **manifest and `NotificationScheduler` / `NotificationReceiver` constants in sync**.

3. **Adhan asset** — Optional raw resource name **`ezan`** (`res/raw/ezan.*`). `NotificationHelper.hasBundledEzan()` gates Adhan channel selection and alarm URI for prayer-start ALARMY.

4. **Style read at fire time** — `NotificationReceiver` loads **current** `SettingsRepository.getSettings()` when an alarm fires, so style changes apply without necessarily rescheduling (still reschedule on toggle changes where implemented).

5. **Legacy package `data.model`** — Duplicate models + `DataStoreManager`; **risk of confusion** if someone imports the wrong `AppSettings`. Prefer **`com.tuttoposto.prayertimes.data.models`** everywhere.

6. **Two `NetworkModule` implementations** — Only **`com.tuttoposto.prayertimes.data.api.NetworkModule`** + **`AladhanApi`** are used (`PrayerTimesRepository`). The **`data.remote`** package is dead code unless reconnected.

7. **Foreground notification + swipe** — OEMs may still treat FGS notifications specially; `setOngoing(false)` is required for swipe in standard behavior but is **not a guarantee** on all skins.

8. **Release build** — `isMinifyEnabled = false` in `release` — no shrinking/obfuscation yet.

9. **Notification IDs** — Fixed ID ranges per prayer for end reminders and start notifications; test IDs `9999`, `9988` — avoid collisions when adding new notification types.

---

## 9. Current state of the app

### Complete / in good shape (as reflected in codebase)

- Location-based **daily** prayer fetch + **DataStore** cache with **Hijri** string support on cache model.
- **Aladhan calendar** API integration + **monthly calendar** UI in pager next to main times.
- **Notifications:** per-prayer toggles, global master, reminder offset 30–60 min, **separate styles** for end vs start, optional **prayer-start** notifications, optional **Ezan** for start (normal path), **ALARMY** path via **foreground playback**.
- **Settings** debug: schedule preview, logs, test notifications (when debug enabled via long-press title).
- **Widgets** (two sizes) + update pipeline.
- **Qibla** screen + ViewModel.
- **AMOLED** theme toggle persisted and applied from `MainActivity`.
- **Notification channels** trimmed (retired IDs deleted on startup); swipe-dismiss path for alarm playback.

### Partially done / product judgment

- **Release hardening** — ProGuard/R8 off; no Play feature modules; no analytics scaffold in tree.
- **i18n** — Primarily **English** `strings.xml` (single `values/` folder observed).

### Known not built / out of scope in repo

- Multi-language resources beyond current `values/`.
- User accounts / server backend.
- Lowering **minSdk** for wide distribution (must be an explicit product decision).

---

## 10. Dependencies (resolved versions)

Versions are taken from **`gradle/libs.versions.toml`** (and the Compose BOM for Compose artifacts without individual versions).

### Plugins

| Plugin | Version |
|--------|---------|
| Android Gradle Plugin (`com.android.application`) | **8.9.1** |
| Kotlin Android | **2.0.21** |
| Kotlin Compose compiler plugin | **2.0.21** |
| KSP | **2.0.21-1.0.28** |

### AndroidX / Google libraries

| Library | Version |
|---------|---------|
| `androidx.core:core-ktx` | **1.17.0** |
| `androidx.appcompat:appcompat` | **1.7.1** |
| `com.google.android.material:material` | **1.13.0** |
| Compose BOM (`androidx.compose:compose-bom`) | **2024.12.01** |
| `androidx.activity:activity-compose` | **1.9.3** |
| `androidx.lifecycle:*` (runtime-ktx, viewmodel-compose, runtime-compose) | **2.8.7** |
| `androidx.navigation:navigation-compose` | **2.8.5** |
| `androidx.datastore:datastore-preferences` | **1.1.1** |
| `androidx.work:work-runtime-ktx` | **2.10.0** |
| `com.google.android.gms:play-services-location` | **21.3.0** |

*Compose UI/Material3/Material icons*: versions **from BOM 2024.12.01** (not repeated per-artifact in `libs.versions.toml`).

### Networking / JSON

| Library | Version |
|---------|---------|
| `com.squareup.retrofit2:retrofit` | **2.11.0** |
| `com.squareup.retrofit2:converter-moshi` | **2.11.0** |
| `com.squareup.okhttp3:okhttp` | **4.12.0** |
| `com.squareup.okhttp3:logging-interceptor` | **4.12.0** |
| `com.squareup.moshi:moshi` / `moshi-kotlin` / `moshi-kotlin-codegen` | **1.15.1** |

### Kotlin

| Library | Version |
|---------|---------|
| `org.jetbrains.kotlinx:kotlinx-coroutines-core` | **1.9.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-android` | **1.9.0** |
| `org.jetbrains.kotlinx:kotlinx-coroutines-play-services` | **1.9.0** |

### Test (androidTest / test)

| Library | Version |
|---------|---------|
| `junit:junit` | **4.13.2** |
| `androidx.test.ext:junit` | **1.3.0** |
| `androidx.test.espresso:espresso-core` | **3.7.0** |

---

## Revision note

This file was authored to reflect the **repository state and features described across the development session** that introduced dual notification styles, notification channel cleanup, alarm playback UX iterations (foreground service, swipe dismiss, title-only prayer start), Hijri/monthly calendar-related work, and AMOLED theming. If any path or version drifts, treat **`gradle/libs.versions.toml`**, **`app/build.gradle.kts`**, and **`AndroidManifest.xml`** as the source of truth and update this document accordingly.
