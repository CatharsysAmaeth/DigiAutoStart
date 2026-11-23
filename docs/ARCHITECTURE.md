# Architecture

## Goal

A native Android application that detects the relevant return from standby
and attempts to reopen the DIGI TV application under Android platform rules.

## Planned components

1. **Configuration**
   - Enable / disable automation.
   - Mode A: always launch DIGI TV after wake.
   - Mode B: launch only if DIGI TV was the last foreground app.
   - Configurable delay and cooldown.

2. **Foreground app tracking**
   - Investigate Usage Access for determining the last foreground app.
   - Request only permissions demonstrated to be necessary.

3. **Standby / wake detection**
   - Investigate Android lifecycle events and broadcasts available to
     ordinary applications on the target firmware.
   - Validate actual behavior on the DIGI R1A.

4. **Launch controller**
   - Resolve the DIGI TV launch intent dynamically from package
     `ro.digionline.tv`.
   - Handle missing or disabled target applications gracefully.
   - Respect Android background activity launch restrictions.

5. **Diagnostics**
   - Record detection events, decisions, launch attempts and failures.
   - Avoid collecting unrelated personal data.

6. **TV interface**
   - D-pad and remote-control navigation.
   - Clear status, settings and manual launch test.

## Important limitation

Android may restrict background applications from starting activities.
The actual feasibility of automatic launch must be established on the
DIGI R1A firmware. No behavior is considered validated before device tests.

## Launcher

The original Android TV / Google TV launcher remains unchanged.
