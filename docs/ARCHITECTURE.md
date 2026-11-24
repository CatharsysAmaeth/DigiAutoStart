# Architecture

## Goal

DAdA reopens the selected DIGI TV app after standby and can optionally open it when the Android TV device starts. The original launcher and platform certification must remain intact.

## Implemented in Release 0.6.0

- Mode A: reopen the selected TV app after every wake.
- Mode B: reopen it only when it was the last foreground app before standby.
- A configurable delay of 0–30 seconds before reopening.
- A foreground service registers for screen-off and screen-on events while automation is enabled.
- Mode B reads Usage Access events transiently and excludes DAdA, the Home launcher and System UI when determining the last foreground app.
- The service can be restored after process reclamation, device restart and app replacement.
- Optional launch at device startup is independent of Mode A/B and uses a short startup delay.
- The app checks whether the selected TV app actually becomes foreground after an automated launch.

## Runtime requirements

- The user explicitly enables automation from the visible DAdA activity.
- Display over other apps access is required for background launch attempts.
- Mode B additionally requires Usage Access.
- A persistent low-importance notification is displayed while standby monitoring is enabled and provides a stop action.
- Startup launch is optional and disabled by default.
- Android and vendor background-execution policies can still restrict launches. A successful activity-start request is not proof that the TV app became visible.

## Release isolation

The normal Release contains the tested automation and its configuration screen. Debug menus, diagnostic collection, internal test-update tools and Debug-only network/package-install permissions are excluded.

## Certification constraints

Preserve Widevine L1, Google / Android TV certification and the original launcher. No root, bootloader unlock, custom firmware or protected system partition changes are used.
