# HomingZone Launcher

A lightweight, native Android TV launcher prototype.

## Design
- Hybrid TV layout: Home, Apps, and Explore navigation with Assistant, profile, and Settings actions
- Backdrop colour follows the focused app's icon
- Home shows favourites, while Apps and Explore expose installed apps; Settings opens system settings
- D-pad / TV remote focus navigation
- Optional recommended-show hero (Netflix / Prime Video) via TMDB: paste a free API key into `TMDB_KEY` in LauncherActivity.java. Leave it empty and the launcher makes no network requests
- No analytics, ads, or background service
- Original launcher is not modified or disabled

## Build without Android Studio
1. Create a GitHub repository and upload this project.
2. Open Actions and run **Build HomingZone APK**.
3. Download the `SUM-HOME-debug` artifact from the completed workflow.

The workflow builds with JDK 17 and Gradle 8.9 on GitHub-hosted runners.

## Install with ADB
Connect the TV with USB debugging enabled, then run:

```cmd
adb install -r app-debug.apk
```

Open HomingZone from the TV's Apps list first. Do not set it as default until you have tested it.

## Set as default Home (only after testing)
```cmd
adb shell cmd package set-home-activity --user 0 com.homingzone.launcher/.LauncherActivity
```

## Roll back to Google TV
```cmd
adb shell pm enable --user 0 com.google.android.tvlauncher
adb shell cmd package set-home-activity --user 0 com.google.android.tvlauncher/.MainActivity
```

The Google TV launcher is never uninstalled or disabled by this project.

## Prototype limitations
This is a first build: app rows are generated from installed launcher apps; the first row shows common streaming apps if installed. Settings, custom wallpaper selection, favourites editing, and polished focus/DPAD behavior are planned for later iterations.

## Credits
This product uses the TMDB API but is not endorsed or certified by TMDB.
