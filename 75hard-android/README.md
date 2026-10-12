# 75 Hard tracker (Android)

A private, offline Android app for tracking the 75 Hard challenge.

## What it does

- **Daily checklist** for the six rules: two 45-minute workouts (one outdoors), diet, no alcohol, a gallon of water, 10 pages of reading, and a progress photo.
- **Water and page counters** with quick-add buttons (litres or ounces).
- **Progress photos** taken from the app and stored in its private storage, not your gallery. Includes a Day 1 vs. latest comparison.
- **Enforces the rules:** if a past day is left incomplete, the app tells you and asks you to restart at Day 1. If you did the tasks but forgot to log them, you can fill that day in instead.
- **75-day grid** with streaks, totals (water, pages, workouts), books read, and the history of every attempt.
- **Evening reminder** that only fires if today still has tasks left.
- **Optional fingerprint/PIN lock**, which also hides the app's screen in the recent-apps view.
- **Journal** note for each day.
- **"Already started" mode** if you're partway through when you install it.

## Privacy

- The app has no `INTERNET` permission, so it cannot send data anywhere.
- No account, no analytics.
- Data is excluded from Google cloud backups and device-to-device transfer.
- To move to a new phone, use **Settings → Backup → Export**. This saves a `.zip` with your logs and photos, which you restore on the new phone.

## Getting the APK

The GitHub Actions workflow `.github/workflows/75hard-android.yml` builds a signed APK on every push that touches this folder:

1. Open the repo's **Actions** tab → **75 Hard Android app** → the latest run → download the `75hard-apk` artifact (a zip containing `75hard.apk`).
2. Builds from the default branch also publish `75hard.apk` under **Releases → 75 Hard tracker (latest)**. This is the easiest place to download it straight onto your phone.
3. On the phone, open the APK and allow "Install unknown apps" for your browser or file manager when Android asks.

Every build is signed with the same key (`app/signing/tracker.jks`), so new versions install over the old one without losing data. Never uninstall the app unless you've exported a backup: uninstalling deletes its private data.

> The signing key is committed so builds are reproducible. That's fine for a personal, sideloaded app. If you want your own key, set `TRACKER_KEYSTORE`, `TRACKER_KEYSTORE_PASSWORD`, `TRACKER_KEY_ALIAS` and `TRACKER_KEY_PASSWORD` when building. Switching keys means one uninstall/reinstall, so export a backup first.

## Building locally

Requires JDK 17 and the Android SDK (or just open the folder in Android Studio).

```sh
cd 75hard-android
./gradlew testReleaseUnitTest assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

## Code map

| Path | What's there |
| --- | --- |
| `domain/Challenge.kt` | The rules: what counts as a complete day, day numbers, missed-day detection, streaks, totals. Pure Kotlin, unit-tested. |
| `data/` | Models (JSON), atomic file storage, private photo storage, zip backup and restore. |
| `ui/` | Jetpack Compose screens: Start, Today, Progress, Photos, Settings. |
| `reminders/` | Daily reminder alarm and notification. |
