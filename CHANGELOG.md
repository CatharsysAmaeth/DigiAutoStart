# Changelog

## 0.5.0 — persistent standby automation prototype

- Added Debug foreground service to keep SCREEN_OFF / SCREEN_ON receiver registered outside the Activity.
- Added sticky service restart and BOOT_COMPLETED recovery when automation is enabled.
- Added notification with explicit stop action.
- Implemented Mode A and Mode B last-foreground-app decision, with Home/System UI excluded.
- Restored Debug package-install permission required by the private APK updater.
- Kept automation components and permissions out of Release.

## 0.4.0 — initial standby automation prototype

- Added Debug-only dynamic screen receiver, launch decision and diagnostics.
- Added Mode A wake scheduling and Mode B Usage Access lookup.
- Added setup for Android background-launch exemption.

## 0.3.0 — private Debug updater

- Added private Debug APK update check, SHA-256 verification and Android installer handoff.
- Added automatic upload of AES-256 encrypted diagnostics reports.

## 0.2.0 — debug diagnostics

- Added opt-in bounded Debug-only diagnostics capture and encrypted ZIP export.

## 0.1.0 — initial device-test build

- Added TV-friendly interface, preferences and manual DIGI TV launch.


## 0.5.2 — in progress

- Add optional DIGI TV launch at box startup, disabled by default and independent of Mode B.
- Keep the foreground monitor restart at boot whenever automation is enabled.
- Verify whether DIGI TV actually becomes foreground after automated launch attempts; record observed/not-observed outcomes.
- Keep visible app version and DEBUG marker.
