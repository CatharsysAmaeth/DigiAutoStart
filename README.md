# DigiAutoStart

Native Android application for automatically reopening DIGI TV after
the DIGI R1A Android TV box wakes from standby.

## Objectives

- Provide two launch modes:
  - Always launch DIGI TV after wake.
  - Launch DIGI TV only if it was the last app before standby.
- Allow configuration of launch delay and cooldown.
- Provide manual launch testing and diagnostic logs.
- Offer a TV remote-friendly interface.
- Preserve the original Android TV / Google TV launcher.

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

## Development environment

- macOS on Apple Silicon
- JDK 21
- Android SDK Platform 36
- Android Build Tools 36.0.0
- Proposed Android Gradle Plugin: 9.4.0
- Proposed Gradle Wrapper: 9.6.0

Build tool versions are provisional until a successful build is recorded.

## Current status

- [x] Public GitHub repository created.
- [x] MIT license selected.
- [x] Local Git repository initialized.
- [x] Android SDK installed.
- [ ] Initial documentation committed.
- [ ] Native Android project created.
- [ ] First APK built.
- [ ] APK installation tested on DIGI R1A.
- [ ] Standby / wake detection tested.
- [ ] Automatic launch behavior validated.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Technical decisions](docs/DECISIONS.md)
- [Test plan](docs/TEST-PLAN.md)
- [Changelog](CHANGELOG.md)
