# DigiAutoStart

Native Android application for testing DIGI TV launch behavior on the DIGI R1A
Android TV box and, after device validation, automating reopening after standby.

## APK downloads

The latest successful build on `main` publishes the standalone APKs directly
into the repositories (not as Actions artifacts or GitHub Releases):

- **Normal APK (public):** [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)
- **Debug APK (private):** stored in
  [DigiAutoStart-Diagnostics/apk](https://github.com/CatharsysAmaeth/DigiAutoStart-Diagnostics/tree/main/apk)

The Debug APK contains private-repository access and the agreed ZIP encryption
password at build time. It can check the private repository for newer Debug
builds, download the APK, verify its SHA-256 checksum, and hand it to Android's
package installer. Android still requires confirmation of the installation.
The Debug APK is signed with a stable key held in the private diagnostics
repository so subsequent updates retain the same signing identity.

Diagnostic reports are AES-256 encrypted automatically and uploaded directly
to the private repository. No password or GitHub token needs to be typed on
the TV. The credentials are supplied to the Debug build through GitHub Actions
Secrets named `DIAGNOSTICS_REPO_TOKEN` and `DIAGNOSTICS_ZIP_PASSWORD`.

**Security note:** the Debug APK contains these credentials by explicit
project decision. Anyone who obtains the APK may be able to extract them and
access the private diagnostics repository. Keep the Debug APK private and
revoke/replace the fine-grained token if it is ever shared outside the intended
environment. The normal Release APK contains neither credentials nor
diagnostics/updater code.

## Current test build

Version: **0.3.0 (private Debug update and automatic report upload)**

This test build provides:
- TV-friendly interface navigable with a remote.
- Mode A / Mode B preference selection (saved locally, not yet automated).
- Configurable intended launch delay (saved locally, not yet applied).
- Detection of whether the DIGI TV launch activity is available.
- Manual DIGI TV launch test.
- Debug-only private update check and APK download.
- Opt-in bounded diagnostic capture and automatic AES-256 ZIP upload.

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

Diagnostics, internet permission, package-install permission, private updater,
GitHub upload and ZIP encryption exist only in Debug. Release contains no
diagnostic UI, receiver, internet permission, updater or upload/encryption
dependencies.
