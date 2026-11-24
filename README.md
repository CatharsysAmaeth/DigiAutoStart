# DAdA — DIGI TV · Assistente de arranque

DAdA is a lightweight Android TV assistant that can reopen the DIGI TV app after standby and, optionally, when the device starts.

## Current version: 0.6.0

- **Mode A:** reopen the selected TV app after every wake.
- **Mode B:** reopen it only when it was the last foreground app before standby.
- Configurable delay before reopening the app.
- Optional launch when the device starts, independently of the standby mode.
- Automatic recovery of enabled automation after a device restart or app update.
- A single “Configurar” screen for the automation, target app and required permissions.
- The official DIGI TV app remains the default target. If it is unavailable, compatible Android TV launcher apps can be discovered; ambiguous matches require manual selection.
- Normal Release includes the automation and configuration screen, but excludes Debug menus, diagnostic collection and the private test updater.

## Platform and privacy

- Designed for Android TV devices running Android 12 or later.
- Preserves the original launcher and platform certification.
- Does not require root access, bootloader unlocking, custom firmware or protected-partition changes.
- The public changelog and documentation do not publish device identifiers, private service locations, credentials, signing material or personal/network details.

Android and vendor background-execution policies can affect automatic launches. The app checks whether the selected TV app becomes foreground after an automated launch; a request to launch is not, by itself, proof that the app became visible.

## Download

- Normal Release APK: [apk/DigiAutoStart-normal.apk](apk/DigiAutoStart-normal.apk)

See [CHANGELOG.md](CHANGELOG.md) for the version history.
