# DigiAutoStart

Native Android application for testing DIGI TV launch behavior on the DIGI R1A
Android TV box and, after device validation, automating reopening after standby.

## Current test build

Version: **0.2.0 (debug diagnostics)**

This test build provides:
- TV-friendly interface navigable with a remote.
- Mode A / Mode B preference selection (saved locally, not yet automated).
- Configurable intended launch delay (saved locally, not yet applied).
- Detection of whether the DIGI TV launch activity is available.
- Manual DIGI TV launch test.
- Opt-in Debug diagnostics with bounded event capture and AES-256 encrypted ZIP export.
- Upload of encrypted ZIP reports to the dedicated private GitHub repository.

**Standby detection and automatic launching are not implemented.** Screen on/off
broadcasts are recorded only as observations and are not proof of standby/wake.

## Target device

- DIGI R1A (Amlogic S905Y4 / B866V2F)
- Android 12 / Google TV
- DIGI TV package: `ro.digionline.tv`

## Safety constraints

- Preserve Widevine L1 and Google / Android TV certification.
- No root, bootloader unlock, custom firmware or protected partition changes.
- Preserve the original launcher.

## Diagnostics

See [Debug diagnostics](docs/DIAGNOSTICS.md). Diagnostics, internet permission,
GitHub upload and ZIP encryption exist only in Debug; Release contains no
diagnostic UI, receiver, internet permission or upload/encryption dependencies.
