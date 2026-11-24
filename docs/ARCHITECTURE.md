# Architecture

## Goal

Automatically reopen DIGI TV after the DIGI R1A wakes from standby, while preserving the original Google TV launcher, Widevine L1 and device certification.

## Implemented in Debug 0.5.0

- TV-friendly controls for Mode A (always) and Mode B (only if DIGI TV was the last user app), plus a 0–30 second delay.
- Dynamic SCREEN_OFF / SCREEN_ON receiver owned by a foreground service.
- Mode B reads Usage Access events and ignores DigiAutoStart, the Home launcher and System UI when determining the last foreground app.
- Foreground service with a persistent low-importance notification keeps the receiver registered when the Activity is stopped.
- START_STICKY allows Android to recreate the service after process reclamation.
- BOOT_COMPLETED receiver requests service restart after device reboot when the user previously enabled automation.
- Background Activity launch is attempted only with the user's special Display over other apps access.
- Opt-in diagnostics record decisions and launch outcomes.

## Runtime requirements

- User must enable the feature explicitly from DigiAutoStart while its Activity is visible.
- Mode A requires Display over other apps access.
- Mode B additionally requires Usage Access.
- A persistent service notification remains visible while automation is enabled; it includes a stop action.
- Android may still terminate or restrict services on vendor firmware. The foreground service and boot restart improve continuity but do not prove survival on every firmware.
- The Android 12 DIGI R1A must confirm whether its firmware permits the background launch exemption. A successful startActivity call is not proof that DIGI TV became visible.

## Release isolation

All automation code, special permissions, service, boot receiver, diagnostics and updater are Debug-only. Release retains only the manual launch and settings UI until the prototype passes physical-device testing.

## Certification constraints

No root, bootloader unlock, custom firmware, launcher replacement or protected system partition changes are used. Widevine L1 and Google TV certification remain mandatory acceptance checks.


## Prototype 0.5.2 — boot behavior and launch verification

- The boot receiver and foreground monitor are separate from opening DIGI TV. When automation is enabled, the monitor restarts after `BOOT_COMPLETED`.
- New preference `launch_digi_on_boot` is independent of Mode A/B and defaults to `false`. If enabled, the service attempts to open DIGI TV 15 seconds after boot.
- Mode B remains unchanged: it decides whether to launch on SCREEN_ON based on the last foreground user application before SCREEN_OFF.
- Each automated launch now gets a follow-up Usage Access check. Diagnostics distinguish `automation_launch_start_activity_returned` from `automation_launch_foreground_observed` and `automation_launch_foreground_not_observed`. This avoids treating a non-throwing `startActivity()` call as proof that DIGI TV appeared.
- Analysis of the 2026-11-24 20:05:43Z report confirmed `BOOT_COMPLETED`, service restart and receiver registration. The previous build did not have a boot-launch preference, so remaining on the launcher immediately after reboot was expected. See the private diagnostics repository for the detailed timeline.
