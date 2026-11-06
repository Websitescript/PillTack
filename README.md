# PillTack (Android)

Kotlin · Jetpack Compose · Room · DataStore · AlarmManager. Fully offline.

## Build the APK
**GitHub Actions (no local setup):** push this folder to a repo -> Actions -> "Build APK" -> download `PillTack-debug-apk`.
**Android Studio:** open the folder, let it sync, Run. (Gradle wrapper is not included; Studio creates it.)

## Layout
- `data/` Room entities/DAOs, `ScheduleLogic` (all schedule rules), `Repo` (storage + alarm refresh)
- `notify/` `Reminders` (alarms, notifications) and `ReminderReceiver` (fire, Taken/Snooze/Skip, boot/time/zone changes)
- `ui/` Compose screens in the PillTack style

## Design tokens (from the Behance palette)
#164F7A primary, #242424 ink, #999999 grey, #F3F3F3 surface, white. Font: Satoshi (falls back to system sans until you add the .ttf files in res/font and set them in `ui/Theme.kt`).
