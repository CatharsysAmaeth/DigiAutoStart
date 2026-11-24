# DAdA — DIGI TV · Assistente de arranque

Native Android TV assistant for the DIGI R1A, designed to reopen DIGI TV after standby and optionally when the box starts.

## Version 0.5.3

- App name shown on Android TV: **DAdA**.
- Product description: **DIGI TV · Assistente de arranque**.
- Blue adaptive launcher icon.
- Simplified home screen with automatically refreshed application and automation status.
- One configuration screen groups standby automation, Mode A / Mode B, wake delay, launch-on-boot, permissions and target app selection.
- The default target remains `ro.digionline.tv`.
- If the default package is unavailable, DAdA searches Android TV launcher apps for a DIGI-labelled replacement. It selects automatically only when exactly one candidate is found; ambiguous cases require manual selection.
- The selected package is used for both standby and boot launch.

The automation prototype is currently Debug-only and still requires physical validation on the DIGI R1A. The normal Release APK deliberately excludes the Debug menu, diagnostics, private updater and experimental automation components. Android/vendor restrictions may block background launches or service recovery. A successful startActivity call is not proof that DIGI TV became visible.

## Device constraints

- DIGI R1A (Amlogic S905Y4 / B866V2F), Android 12 / Google TV.
- Preserve Widevine L1, Google/Android TV certification and the original launcher.
- No root, bootloader unlock, custom firmware or protected partition changes.

## APK locations

- Normal Release APK: [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)
- Private Debug and Release test APKs and metadata: [DigiAutoStart-Diagnostics](https://github.com/CatharsysAmaeth/DigiAutoStart-Diagnostics/tree/main/apk)
