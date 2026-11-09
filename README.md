# HomingZone — Streaming Discovery Prototype

This version automatically discovers a small set of known streaming/media apps installed on the TV and rotates a cinematic hero card between those apps every 12 seconds while the launcher is visible.

## Important distinction
Installed-app discovery does not provide access to Netflix, Prime Video, YouTube, or other services' private movie/series catalogues. This version recommends installed streaming destinations, not individual shows. A future show-recommendation feature needs a content metadata provider (for example TMDB) and supported availability data; it should not scrape private app data.

## Current features
- Automatic featured-app rotation from detected installed apps
- Gradient theme changes by featured platform
- Direct open-app button
- Bottom dock with detected launcher/Leanback apps
- Lightweight native Android UI, no network permission or background service
- Rotation pauses while the launcher is not in the foreground
- Original Google TV launcher is not modified or disabled

## Browser preview
Open `preview.html` in Chrome. It uses sample apps to demonstrate the layout; the actual Android build detects apps installed on the TV.

## Build and install
Push to GitHub and download the `HomingZone-debug` artifact from Actions.

```cmd
adb install -r app-debug.apk
adb shell am start -n com.homingzone.launcher/.LauncherActivity
```

If debug signing keys differ, uninstall only HomingZone before reinstalling:
```cmd
adb uninstall com.homingzone.launcher
adb install app-debug.apk
```
