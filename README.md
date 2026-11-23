# DigiAutoStart

Native Android application for testing DIGI TV launch behavior on the DIGI R1A
Android TV box and, after device validation, automating reopening after standby.

## Current test build

Version: **0.1.0 (debug)**

This first APK is a device-installation and manual-launch test build. It provides:
- TV-friendly interface navigable with a remote.
- Mode A / Mode B preference selection (saved locally, not yet automated).
- Configurable intended launch delay (saved locally, not yet applied).
- Detection of whether the DIGI TV launch activity is available.
- Manual DIGI TV launch test.

**Standby detection and automatic launching are not implemented in this build.**
Do not interpret the stored mode/delay preferences as active automation.

## Target device

- Device: DIGI R1A
- SoC: Amlogic S905Y4
- Platform: Android 12 / Google TV
- DIGI TV package: `ro.digionline.tv`
- Observed DIGI TV version: 3.0.0

## Non-negotiable requirements

- Preserve Widevine L1.
- Preserve Google / Android TV certification.
- No root access.
- No bootloader unlocking.
- No custom firmware or ROM.
- No modifications to protected system partitions.
- Do not replace the original launcher.

## Android project

- Language: Kotlin
- Application ID: `pt.catharsys.digiautostart` (initial project ID)
- minSdk: 31
- compileSdk: 36
- targetSdk: 35
- Android Gradle Plugin: 8.13.2
- Gradle: 8.13
- Kotlin Gradle Plugin: 2.2.21
- CI runtime: Temurin JDK 21

The first successful runner build will establish whether this toolchain is valid.

## Continuous integration

GitHub Actions builds and tests the Android project on GitHub-hosted Ubuntu
runners. A successful build uploads the debug APK as a 14-day workflow artifact.

See [CI/CD documentation](docs/CI-CD.md).

## Current status

- [x] Public GitHub repository and MIT license.
- [x] GitHub Actions Android CI.
- [x] Initial native Kotlin Android project.
- [x] TV-friendly first test interface and manual launch action.
- [x] Unit tests for launch-mode decision logic.
- [ ] First successful remote APK build.
- [ ] APK installation tested on DIGI R1A.
- [ ] Standby / wake detection investigated on device.
- [ ] Automatic launch behavior implemented and validated.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Technical decisions](docs/DECISIONS.md)
- [Test plan](docs/TEST-PLAN.md)
- [CI/CD](docs/CI-CD.md)
- [Changelog](CHANGELOG.md)
