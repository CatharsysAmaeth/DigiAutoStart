# Changelog

All notable project changes are documented here.

## Unreleased

- Validate the Debug-only standby/wake automation prototype on the DIGI R1A.
- Determine whether a persistent process strategy is necessary.

## 0.4.0 — experimental standby automation

- Added Debug-only dynamic SCREEN_OFF / SCREEN_ON automation receiver.
- Added Mode A wake scheduling and Mode B last-foreground-app detection via Usage Access.
- Added explicit setup for Android background-launch overlay exemption.
- Added diagnostic decision and launch outcome events.
- Kept automation permissions and UI out of Release.

## 0.3.0 — private Debug updater

- Added private Debug APK update check, SHA-256 verification and Android installer handoff.
- Added automatic upload of AES-256 encrypted diagnostics reports.

## 0.2.0 — debug diagnostics

- Added opt-in bounded Debug-only diagnostics capture and encrypted ZIP export.

## 0.1.0 — initial device-test build

- Scope: installation, interface, preferences and manual DIGI TV launch.
