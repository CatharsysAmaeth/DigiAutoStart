# DigiAutoStart

Native Android application for the DIGI R1A that aims to reopen DIGI TV automatically after standby.

## Current Debug test build

**Version 0.5.0 — persistent standby automation prototype**

- Mode A: always try to open DIGI TV after wake.
- Mode B: only try if DIGI TV was the last foreground user app.
- Configurable delay (0–30 seconds).
- Foreground service keeps screen monitoring active when the app's Activity is not visible.
- Sticky restart and boot receiver request recovery after process reclamation and device reboot.
- Persistent notification includes a stop action.
- Android special access is required to attempt a background activity launch.

**Not yet validated on the physical DIGI R1A.** Android/vendor restrictions may still block the launch or stop the service. The device test must confirm actual DIGI TV visibility after wake. Do not treat the prototype as production-ready until then.

## Permissions

- Mode A: Display over other apps.
- Mode B: additionally Usage Access.
- Foreground service and boot completed for persistence/recovery.

All automation features and permissions are Debug-only. Release remains free of these components until device validation passes.

## APK locations

- Normal Release APK: [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)
- Private Debug APK and version metadata: [DigiAutoStart-Diagnostics/apk](https://github.com/CatharsysAmaeth/DigiAutoStart-Diagnostics/tree/main/apk)

## Device constraints

- DIGI R1A (Amlogic S905Y4 / B866V2F), Android 12 / Google TV.
- DIGI TV package: `ro.digionline.tv`.
- Preserve Widevine L1, Google/Android TV certification and the original launcher.
- No root, bootloader unlock, custom firmware or protected partition changes.
