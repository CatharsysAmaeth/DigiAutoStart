# Technical decisions

## D-001: Preserve device certification

Status: Accepted

Widevine L1 and Google / Android TV certification are absolute requirements.

Consequences:
- No root.
- No bootloader unlock.
- No custom ROM or modified firmware.
- No protected system partition changes.

## D-002: Native Android application

Status: Accepted

Implement DAdA as a native Kotlin Android application.

## D-003: Two automation modes

Status: Accepted

- Mode A: always reopen the selected TV app after wake.
- Mode B: reopen only when the selected TV app was the last foreground app.

Mode B is the preferred default.

## D-004: Original launcher preservation

Status: Accepted

Do not replace or disable the original Android TV launcher.

## D-005: Target app resolution

Status: Accepted

Keep the official DIGI TV app as the default target. Resolve its launch activity dynamically. If it is unavailable, discover compatible Android TV launcher apps and select a fallback automatically only when there is one unambiguous DIGI-labelled candidate; otherwise require manual selection.

## D-006: Android build toolchain

Status: Accepted

- JDK 21 on GitHub runners.
- Android SDK Platform 36 / Build Tools 36.0.0.
- Gradle 8.13.
- Java 17 bytecode target.
- minSdk 31, targetSdk 35.

## D-007: Permissions

Status: Accepted

- Display over other apps: required for background launch attempts.
- Usage Access: required only for Mode B.
- Foreground service and boot completed: required to maintain and restore the standby monitor.
- No accessibility service, root access or firmware modification.
- Normal Release does not request internet or package-install permissions.

## D-008: Android background launch restrictions

Status: Validated on target hardware

The foreground service, screen-state receiver, wake delay and foreground verification are implemented and have been tested on the target Android TV device. Android/vendor restrictions may still vary across firmware versions.

## D-009: GitHub-hosted CI

Status: Accepted

Use GitHub Actions with hosted Linux runners for repeatable Android builds, unit tests and APK-content checks.

## D-010: Initial application identity

Status: Accepted

The Android application identity is kept stable across Debug and Release so an existing installation can be updated without losing its preferences.

## D-011: Initial device-test build

Status: Historical

Version 0.1.0 validated installation, TV navigation and saved preferences before standby automation was introduced.

## D-012: Debug-only diagnostics

Status: Accepted

Diagnostic collection and the private test updater remain confined to Debug builds. The normal Release contains no diagnostic upload implementation or Debug test-updater code.

## D-013: Promote tested automation to Release

Status: Accepted

After physical testing, Mode A/B, wake delay, optional launch at startup and service recovery are included in normal Release 0.6.0.
