# Prayer Times

Android app that shows **Islamic prayer times** for your location using the [Aladhan API](https://aladhan.com/prayer-times-api). Built with **Kotlin** and **Jetpack Compose** (Material 3).

## Features

- Daily prayer list with optional Hijri date and a monthly calendar view  
- Prayer-end reminders and optional prayer-start alerts (normal or alarm-style playback)  
- Home screen widgets, Qibla direction, and settings (including AMOLED theme)

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

## License

Specify your license here (e.g. MIT, Apache-2.0) if you publish publicly.
