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

Implement DigiAutoStart as a native Kotlin Android application.

## D-003: Two automation modes

Status: Accepted

- Mode A: always launch DIGI TV after wake.
- Mode B: launch only when DIGI TV was the last foreground app.

Mode B is the preferred default, subject to feasibility testing.

## D-004: Original launcher preservation

Status: Accepted

Do not replace or disable the original Android TV launcher.

## D-005: Target package

Status: Accepted

DIGI TV package observed on the device: `ro.digionline.tv`.

Resolve the launch activity dynamically instead of assuming an activity name.

## D-006: Build environment

Status: Proposed

- JDK 21
- Android SDK Platform 36
- Build Tools 36.0.0
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0
- AGP built-in Kotlin support

Validate versions with the first clean build before treating this toolchain
as established.

## D-007: Permissions

Status: Open

Investigate Usage Access and battery optimization settings.
Do not request overlay, accessibility or other permissions without
a demonstrated technical need.

## D-008: Android background launch restrictions

Status: Open

Determine which wake events and background activity launches are possible
for a normal sideloaded application on the actual DIGI R1A firmware.
Do not assume that a boot receiver or background service can bypass
platform restrictions.
