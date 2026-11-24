# Changelog

All notable project changes are documented here.

## Unreleased

- Added an opt-in, bounded Debug-only diagnostics capture.
- Added app lifecycle, manual launch and screen broadcast observations.
- Added AES-256 encrypted ZIP export and Android share-sheet handoff.
- Kept diagnostics implementation and dependencies out of the Release source set.
- Added optional upload of encrypted ZIP reports to the dedicated private GitHub repository using a repository-scoped fine-grained token protected by Android Keystore.
- Automatic standby detection and automatic launching are not implemented yet.

## 0.2.0 — debug diagnostics

- Debug test build with opt-in local event capture and encrypted ZIP export.

## 0.1.0 — initial device-test build

- Scope: installation, interface, preferences and manual DIGI TV launch.
