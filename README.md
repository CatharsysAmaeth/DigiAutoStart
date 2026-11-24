# DAdA — DIGI TV · Assistente de arranque

Native Android TV assistant for the DIGI R1A, designed to reopen DIGI TV after standby and optionally when the box starts.

## Current Debug test build

**Version 0.5.3 — branding and clearer startup controls**

- App name shown on Android TV: **DAdA**.
- Product description: **DIGI TV · Assistente de arranque**.
- Blue adaptive launcher icon.
- Main-screen entry point: “Arranque automático e standby”.
- Mode A: try to open DIGI TV after every wake.
- Mode B: only try if DIGI TV was the last foreground user app.
- Independent option to open DIGI TV when the box starts (off by default).
- Configurable wake delay (0–30 seconds).

The prototype still requires physical validation on the DIGI R1A. Android/vendor restrictions may block background launches or service recovery. A successful startActivity call is not proof that DIGI TV became visible.

## Device constraints

- DIGI R1A (Amlogic S905Y4 / B866V2F), Android 12 / Google TV.
- DIGI TV package: `ro.digionline.tv`.
- Preserve Widevine L1, Google/Android TV certification and the original launcher.
- No root, bootloader unlock, custom firmware or protected partition changes.

## APK locations

- Normal Release APK: [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)
- Private Debug APK and version metadata: [DigiAutoStart-Diagnostics/apk](https://github.com/CatharsysAmaeth/DigiAutoStart-Diagnostics/tree/main/apk)
