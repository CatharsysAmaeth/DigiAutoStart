# DigiAutoStart

Native Android application for testing DIGI TV launch behavior on the DIGI R1A Android TV box and automating reopening after standby, subject to Android platform restrictions and physical device validation.

## APK downloads

The latest successful build on main publishes standalone APKs directly into the repositories:

- **Normal APK (public):** [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)
- **Debug APK (private):** stored in [DigiAutoStart-Diagnostics/apk](https://github.com/CatharsysAmaeth/DigiAutoStart-Diagnostics/tree/main/apk)

The private Debug APK supports encrypted diagnostic upload and in-app update checks. Keep it private: it contains credentials by explicit project decision. The normal Release APK excludes diagnostics, updater and experimental automation permissions.

## Current test build

Version: **0.4.0 (experimental standby automation prototype)**

The Debug test build adds:
- Dynamic observation of screen-off and screen-on while the app process is alive.
- Mode A: schedule DIGI TV after wake.
- Mode B: use Usage Access to check whether DIGI TV was the last foreground app.
- User-controlled setup of the special access required to launch an Activity from the background.
- Diagnostic events for decisions and launch outcomes.

**This is experimental and not yet validated on the R1A.** The receiver is process-bound; if Android kills DigiAutoStart, it will not receive the next screen broadcast until the app is opened again. A persistent service/restart strategy has not yet been implemented.

## Target device

- DIGI R1A (Amlogic S905Y4 / B866V2F)
- Android 12 / Google TV
- DIGI TV package: ro.digionline.tv

## Safety constraints

- Preserve Widevine L1 and Google / Android TV certification.
- No root, bootloader unlock, custom firmware or protected partition changes.
- Preserve the original launcher.
