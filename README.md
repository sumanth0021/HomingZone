# HomingZone Launcher

Cinematic Android TV launcher prototype inspired by premium streaming home screens.

- Full-screen navy-to-warm gradient
- Glass-style top navigation
- Featured welcome area and action buttons
- Page indicator dots
- Translucent bottom dock populated from installed apps
- TV remote focus states and app launching
- Discovers both regular Android launcher and Android TV Leanback apps
- No network permission, analytics, ads, or background services
- Original Google TV launcher is never modified or disabled

Open `preview.html` in a desktop browser to review the visual direction. The browser is a visual prototype; actual TV behaviour still needs occasional device testing.

Build APK using the GitHub Actions workflow. Artifact name: `HomingZone-debug`.

Install:
```cmd
adb install -r app-debug.apk
adb shell am start -n com.homingzone.launcher/.LauncherActivity
```

If the debug signing key changed, uninstall only the old HomingZone app before reinstalling:
```cmd
adb uninstall com.homingzone.launcher
adb install app-debug.apk
```
