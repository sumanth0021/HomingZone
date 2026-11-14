# Local Security and Privacy Review

Date: 2026-11-14

## Scope

This review covers only the extracted local tvOS Launcher project in this
directory. No GitHub repository was accessed, fetched, cloned, uploaded to, or
modified. The separate original HomingZone source was found as the local ZIP
file:

`HomingZone_Launcher_AppleTV_Style (1).zip`

The original source was not modified. A local backup of the extracted project
was created before this report was added:

`HomingZone_security_audit_backup_2026-11-14`

This is a source review, not a proof of complete security. Runtime behavior
that depends on user-enabled Android permissions, ADB pairing, presentation
mode, or external services was classified as conditional.

## Findings

| # | Severity | File | Lines | Vulnerability | Confidence |
|---|----------|------|-------|---------------|------------|
| 1 | 🟠 HIGH | `src/com/techgenius/tvoslauncher/present/AdminServer.java` | 72-75, 185-196 | Presentation administration server uses plaintext HTTP on all interfaces; PINs and bearer tokens can be intercepted, including tokens accepted in URL query strings. Runtime risk requires Presentation/Admin mode and network reachability. | 9/10 |
| 2 | 🟠 HIGH | `src/com/techgenius/tvoslauncher/PowerService.java` | 1035-1127 | Enabled accessibility service can receive global key events, inspect window content, perform UI actions and capture screenshots. This is powerful device-wide access, though user-enabled and feature-related. | 9/10 |
| 3 | 🟠 HIGH | `src/com/techgenius/tvoslauncher/LocalAdb.java` | 404-444, 1367-1420 | Local ADB functionality can connect to localhost/LAN ADB, execute shell commands, alter secure settings, grant permissions and install APKs. Risk is conditional on ADB pairing/mode and user-triggered flows. | 9/10 |
| 4 | 🟡 MEDIUM | `src/com/techgenius/tvoslauncher/CustomMedia.java` | 2392-2452 | Settings backup exports shared-preference values to public Downloads, including persistent presentation bearer tokens from pairing preferences. | 9/10 |
| 5 | 🟡 MEDIUM | `AndroidManifest.xml` | 8-34 | Broad permissions include all-files storage, package visibility, microphone, fine location, notification policy, TV listings, package install/delete, secure settings, DUMP and volume-key control. Several are unnecessary for a minimal Classic launcher. | 9/10 |
| 6 | 🟡 MEDIUM | `src/com/techgenius/tvoslauncher/MediaWatchService.java` | 80-125, 628-719 | Notification listener/media-session access observes notifications, playback metadata, artwork, package names and viewing/Continue Watching data. | 9/10 |
| 7 | 🟡 MEDIUM | `src/com/techgenius/tvoslauncher/Weather.java` and `Suggestions.java` | Feature-dependent | Network requests can disclose location-derived data and contact weather, TMDB, Reddit, ESPN, JustWatch, Stremio, Nuvio, YouTube, news, Unsplash and GitHub services. No analytics, advertising SDK, Firebase or Crashlytics was found. | 8/10 |
| 8 | ⚪ LOW | `build.sh` | 25-40 | Development build falls back to a debug keystore with password `android`; unsafe for release distribution, but not a runtime vulnerability in a local debug build. | 9/10 |

## Verified capability and privacy risks

- The manifest requests broad storage, package discovery, location,
  microphone, TV listings, notification-policy, package-management, secure
  settings, debugging, and volume-control capabilities.
- The accessibility service can observe and interact with other applications
  when the user enables it.
- The notification listener and media-session code can process playback and
  notification metadata.
- External services are contacted by feature-specific weather, media,
  wallpaper, update, and content functions.
- Local ADB code can perform privileged device-management operations when
  pairing and permissions are available.
- Settings backups can expose private preferences and credentials.
- No advertising SDK, analytics SDK, Firebase, Crashlytics, or explicit
  telemetry library was identified in the inspected source.

## Recommended changes before integration

1. Replace or disable the plaintext presentation server, remove query-string
   bearer tokens, and require explicit activation.
2. Exclude pairing tokens, ADB state, credentials, and private identifiers from
   settings backups. Prefer an explicit allowlist of non-secret settings.
3. Remove permissions and features not required by the Classic launcher.
4. Keep notification listener, accessibility, media-history, location, and
   external-service features opt-in and clearly disclosed.
5. Keep persistent network ADB disabled by default and require confirmation for
   shell, package, and secure-setting operations.
6. Verify downloaded updates against a trusted signing certificate or digest.
7. Require a release keystore for release builds instead of allowing the debug
   signing fallback.

## Validation limits

- No APK runtime test was performed during this read-only audit.
- No network traffic capture or device permission-state inspection was
  performed.
- Findings do not claim 100% security.
- This report does not approve any remediation changes; those require a
  separate decision.
