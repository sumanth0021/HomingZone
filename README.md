# HomingZone Launcher

A lightweight, native Android TV launcher (Java, no third-party libraries).

## Design
Inspired by the simplicity of Apple TV, with its own look:
- Full-screen local wallpaper with a subtle dark overlay
- Small clock + decorative profile icon in the top-right corner, with a Settings button next to it
- One translucent glass dock near the bottom: all installed apps in a single horizontal row
- 16:9 app tiles with the app's TV banner (or its icon), the app name underneath
- Focused tile scales up, lifts with a shadow and gets a thin ring; the row scrolls to keep it centred
- D-pad / TV remote navigation: LEFT/RIGHT move, OK opens, UP reaches Settings, DOWN returns to the dock
- No analytics, ads, internet permission or background service
- Original launcher is not modified or disabled

`preview.html` is a browser mock-up of the layout (same sizes as the app). It shows the look only; it cannot test the real remote or the TV.

## Changing the wallpaper
The default is a minimal black cinematic background. Open the top-right **Settings** button,
choose **Choose wallpaper**, and select a JPG, JPEG, PNG, or WebP from TV storage or USB storage.
The preview is loaded at a memory-safe size; press **Apply** to save it, **Cancel** to leave the
current wallpaper unchanged, or **Restore default black background** to remove it. The selected
Storage Access Framework URI is persisted across launcher restarts.

## Build without Android Studio
1. Push the project to GitHub (or open **Actions** and run **Build HomingZone APK** manually).
2. Download the `HomingZone-debug` artifact from the completed workflow.

The workflow uses JDK 17 and the Gradle that is installed on the GitHub-hosted runner (`gradle assembleDebug --no-daemon`).

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

## Known limitations
- The dock is a translucent gradient, not a real blur (live background blur is too heavy for this TV class).
- The profile icon is decorative.
- Favourites editing is not implemented yet.
- Wallpaper settings include preview/apply/cancel, restore-default, installed application
  information, and HomingZone version/about information.
- Not yet verified on the real TV; see the test checklist in the change notes.
