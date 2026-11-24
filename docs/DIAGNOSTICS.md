# Debug diagnostics

## Scope

Diagnostics are isolated under src/debug. Release receives a no-op API shim and excludes the diagnostics UI, receiver, INTERNET permission, FileProvider, updater and ZIP encryption/upload dependencies.

The Debug build records app-owned lifecycle/launch events and ACTION_SCREEN_ON / ACTION_SCREEN_OFF observations while the app process is alive. These broadcasts are observations, not proof of a hardware standby state.

Version 0.4.0 also records the experimental automation receiver's decisions: last foreground package (Mode B), whether launch was scheduled, and launch success/exception/permission-blocked outcomes. It does not collect a general usage history or export unrelated app activity.

## Privacy and retention

- Capture is opt-in and bounded to approximately 1 MiB plus one previous segment.
- No serial number, MAC address, IP address, SSID, account information, credentials or DIGI TV content is collected.
- Device summary contains Android version, manufacturer/model and app version.
- Reports are AES-256 encrypted and uploaded to the private diagnostics repo.

## Automation test permissions

The Debug-only automation prototype may ask the user to grant:
- **Usage Access** — only required for Mode B, to identify the most recently resumed package at screen-off.
- **Display over other apps** — used as Android's documented exception for starting an Activity from the background. DigiAutoStart does not draw an overlay.

These permissions are not added to the Release manifest. They do not require root, bootloader changes or firmware modifications.

## Known limitation

The screen receiver is dynamically registered while the process is alive. If Android kills the process, it cannot observe the next wake until DigiAutoStart is opened again. A persistent service/restart strategy is a separate decision and has not yet been implemented.
