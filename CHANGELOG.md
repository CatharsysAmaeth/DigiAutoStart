# Changelog

All notable project changes are documented here.

## Unreleased

- Added the initial native Kotlin Android application module.
- Added a TV-friendly interface with saved Mode A / Mode B preferences,
  configurable intended delay, DIGI TV availability status and manual launch.
- Added unit tests for the launch policy.
- Updated GitHub Actions to build and test on a hosted Ubuntu runner and
  upload the debug APK artifact.
- Automatic standby detection and automatic launching are not implemented yet.
- Production signing and public releases remain deferred.

## 0.1.0 — initial device-test build

- First installable test milestone, subject to successful CI build.
- Scope: installation, interface, preferences and manual DIGI TV launch.
