# Architecture

## Goal

A native Android application that detects the relevant return from standby
and attempts to reopen the DIGI TV application under Android platform rules.

## Implemented in version 0.1.0

1. **TV interface**
   - Native Android Activity with remote-friendly focusable controls.
   - Saved Mode A / Mode B preference.
   - Saved intended launch delay (0–30 seconds).
   - Current DIGI TV launch-activity availability status.
   - Manual launch button.
2. **Launch policy**
   - Pure decision function for Mode A and Mode B.
   - Unit tests for the two decision paths.

The mode and delay controls currently store preferences only. They do not
trigger automation in this version.

## Planned components

1. **Foreground app tracking**
   - Investigate Usage Access for determining the last foreground app.
   - Request only permissions demonstrated to be necessary.
2. **Standby / wake detection**
   - Investigate lifecycle events and broadcasts available to ordinary apps.
   - Validate actual behavior on the DIGI R1A firmware.
3. **Launch controller**
   - Resolve the DIGI TV launch intent dynamically from package
     `ro.digionline.tv`.
   - Handle missing or disabled target applications gracefully.
   - Respect Android background activity launch restrictions.
4. **Diagnostics**
   - Record detection events, decisions, launch attempts and failures.
   - Avoid collecting unrelated personal data.

## Important limitation

Android may restrict background applications from starting activities.
The actual feasibility of automatic launch must be established on the
DIGI R1A firmware. No behavior is considered validated before device tests.

## Launcher and certification

The original Android TV / Google TV launcher remains unchanged. The app does
not request root, unlock the bootloader, modify firmware or touch protected
system partitions. Widevine L1 and Google TV certification remain mandatory
device acceptance checks.
