# Prayer Times

Android app that shows **Islamic prayer times** for your location using the [Aladhan API](https://aladhan.com/prayer-times-api). Built with **Kotlin** and **Jetpack Compose** (Material 3).

## Features

- Daily prayer list with optional Hijri date and a monthly calendar view  
- Prayer-end reminders and optional prayer-start alerts (normal or alarm-style playback)  
- Home screen widgets, Qibla direction, and settings (including AMOLED theme)
- Offline prayer times: current and next month saved on-device, with notifications and Ezan scheduled from cache.

## Offline behavior

In Settings, Fajr appears alongside the other prayers when **Use separate Fajr reminder**
is off. Enabling it moves Fajr's controls into **Fajr notifications**, preserving all
saved settings. The separate-reminder switch stays visible in either mode. It replaces Fajr's
shared before-end reminder. Choose from 5 minutes up to the upcoming Fajr window's
length before sunrise (default 15), Ezan,
alarm sound or notification sound, and normal or alarm-like delivery. Other prayers
keep their shared offset and style. Fajr's start alert is suppressed while this
override is enabled unless you also allow it; the global prayer-start switch must
be on for that start alert. The global and per-prayer notification toggles still apply.
The wake-up follows each day's saved sunrise, including the labelled offline fallback.
For unusually short Fajr windows, the wake-up is limited to Fajr start.

Fajr's offset supports precise entry (tap Edit), a slider and 15/30/60/90-minute
presets. The slider and number entry stop at the upcoming Fajr window's length;
presets outside that range are hidden. There is no fixed three-hour picker limit.
The displayed selection adapts to shorter windows without overwriting the saved
preference for future days; editing saves the new selection. Without saved times,
the picker is unavailable rather than guessing a limit. Alarm readiness is a
compact expandable check of notification access, exact
alarms, the channels used by enabled prayers, sound volume, Modes and battery status.
It refreshes while Settings is visible and after returning from Android settings;
it does not change device settings or guarantee delivery.

"Test my Fajr reminder" previews the current effective Fajr sound and style,
including shared settings when the separate reminder is off. The preview works
even when in-app alerts are paused, requires Android notification permission,
and stops after eight seconds or via Stop test. It does not schedule, cancel or
reschedule prayer alarms. Real alarm playback takes priority over a preview, and
stopping/dismissing a preview cannot stop real playback. Normal-style previews use
the actual notification channel, including the user's Android channel overrides.

The separate Fajr wake-up in **Alarm-like** style also has a minimal lock-screen
alarm screen: current time, minutes until sunrise and a large Stop button. It uses
Android's full-screen notification access (shown in Alarm readiness), not an
overlay or keyguard unlock. Other prayers, Fajr start alerts, shared Fajr reminders
and Normal style retain their existing behavior. Without full-screen access, the
alarm keeps playing with a notification Stop action. Android may show a banner
instead while the phone is unlocked; tapping it opens the active alarm screen.

For this mode, **Test my Fajr alarm (10 s)** schedules a separate one-shot test so
you can lock the phone before it rings for eight seconds. It requires exact-alarm
access; if full-screen access is missing, the test button first opens its Android
permission page. Return and tap Test again after allowing access. Stop test cancels
only that preview, and session-specific Stop actions cannot stop a newer alarm.
The test uses the current settings at delivery and never replaces a real prayer slot.

**Skip next Fajr wake-up** skips only the next before-sunrise reminder shown in
Settings. It saves that prayer date and timezone, so refreshes, offset changes and
reboots do not restore the skipped reminder. The following day's wake-up, other
prayers, Fajr-start alerts and tests are unaffected. **Undo** restores the reminder
if it is still in the future; it never replays an elapsed alarm. The dated skip
remains visible for that day and stops applying automatically afterward. Switching
between separate and shared Fajr settings does not clear the one-time skip.

Saved dates are used without an internet connection. When those dates run out, the
app keeps the last saved day's clock times visible and uses them for each new day's
reminders and Ezan (subject to your notification settings). A warning shows the source
date and timezone because these fallback times may differ from the actual times for
today or your new location. Widgets mark this fallback with ≈.

Today and tomorrow are scheduled in advance; midnight, reboot, app updates and clock/
timezone changes restore alarms locally. Network or location failures do not block
cached scheduling. Successful refreshes replace fallback times automatically, and
the calendar displays full saved months offline.

## Requirements

- **Android Studio** Koala (2024.1.1) or newer recommended  
- **JDK 11**  
- **minSdk / targetSdk 36** — runs on supported API 36+ devices/emulators only

## Open and run

1. Clone or open this folder in Android Studio.  
2. Let Gradle sync finish.  
3. Run the `app` configuration on an emulator or device.

No backend or API key is required for default Aladhan usage.

## Project notes

- Architecture and file roles: see **`PROJECT_CONTEXT.md`** in the repo root.  
- Canonical code lives under `com.tuttoposto.prayertimes` (`data.models`, `data.api`, `data.repository`, `ui`, `notifications`, `widget`, `workers`).
