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

## D-006: Android build toolchain

Status: Proposed — validate with first successful clean CI build

- JDK 21 on GitHub runner.
- Android SDK Platform 36 / Build Tools 36.0.0.
- Android Gradle Plugin 8.13.2.
- Gradle 8.13.
- Kotlin Gradle Plugin 2.2.21.
- minSdk 31, targetSdk 35.

## D-007: Permissions

Status: Open

Do not request overlay, accessibility, Usage Access or other permissions
without a demonstrated technical need.

## D-008: Android background launch restrictions

Status: Open

Determine which wake events and background activity launches are possible
for a normal sideloaded application on the actual DIGI R1A firmware.
Do not assume that a boot receiver or background service can bypass
platform restrictions.

## D-009: GitHub-hosted CI

Status: Accepted

Use GitHub Actions with GitHub-hosted Linux runners for repeatable Android
builds and unit tests. Upload debug APK artifacts for device testing. Keep
production signing material out of Git and defer production releases until
application identity and secure key management are established.

## D-010: Initial application identity

Status: Provisional

Initial application ID: `pt.catharsys.digiautostart`.

Changing the application ID after installation means Android treats the app
as a different application. Confirm this identity before a production release.

## D-011: First device-test APK scope

Status: Accepted

Version 0.1.0 validates installation, TV navigation, saved preferences and
manual DIGI TV launch. It does not claim standby detection or automatic launch.
