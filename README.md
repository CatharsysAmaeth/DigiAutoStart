# DigiAutoStart

Native Android application for testing DIGI TV launch behavior on the DIGI R1A
Android TV box and, after device validation, automating reopening after standby.

## APK downloads

The latest successful build on `main` publishes the standalone APKs directly
into the repositories (not as Actions artifacts or GitHub Releases):

- **Normal APK (public):** [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)
- **Debug APK (private):** stored in
  [DigiAutoStart-Diagnostics/apk](https://github.com/CatharsysAmaeth/DigiAutoStart-Diagnostics/tree/main/apk)

The normal APK is committed automatically after tests and Release-isolation
checks pass. The Debug APK is published to the private repository when the
`DIAGNOSTICS_REPO_TOKEN` Actions secret is configured in this repository.

To configure private publication, create a fine-grained GitHub token for owner
`CatharsysAmaeth`, restrict repository access to **DigiAutoStart-Diagnostics**
only, grant **Contents: Read and write**, and add it in this repository under
Settings → Secrets and variables → Actions as `DIAGNOSTICS_REPO_TOKEN`.
The same narrowly scoped token can also be entered in the Debug app when
configuring encrypted diagnostic uploads. Never commit the token.

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
