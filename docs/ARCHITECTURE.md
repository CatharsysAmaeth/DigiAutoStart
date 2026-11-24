# Architecture

## Goal

A native Android application that detects return from standby and attempts to reopen DIGI TV under Android platform rules, without replacing the original Google TV launcher or modifying firmware.

## Implemented

1. **TV interface**
   - Native Android Activity with remote-friendly focusable controls.
   - Saved Mode A / Mode B preference.
   - Saved launch delay (0–30 seconds).
   - DIGI TV launch-activity availability status and manual launch.
2. **Diagnostics**
   - Debug-only, opt-in bounded capture of app lifecycle and screen broadcasts.
   - AES-256 encrypted reports uploaded to the private diagnostics repository.
3. **Standby/wake prototype (Debug only, version 0.4.0)**
   - Dynamic receiver observes ACTION_SCREEN_OFF and ACTION_SCREEN_ON while the app process is alive.
   - Mode A schedules DIGI TV after wake.
   - Mode B uses Usage Access events to determine whether ro.digionline.tv was the most recently resumed app at screen-off.
   - Uses the user-granted SYSTEM_ALERT_WINDOW special permission as the Android background-activity-launch exemption. The app does not draw an overlay.
   - Records decisions and launch outcomes in the existing opt-in diagnostics.

## Experimental limitations

The receiver is registered dynamically and is lost if Android kills the app process. This prototype does not yet include a persistent foreground service or boot-time restart. It must be tested on the DIGI R1A before being considered reliable.

Android restricts background Activity launches. The overlay special access is requested only in the Debug test build; it is not present in Release. Usage Access is needed only for Mode B. These permissions must be explicitly granted by the user in Android settings.

A successful call to startActivity() is not proof that DIGI TV actually became visible. Device testing and diagnostic events must confirm the result.

## Launcher and certification

The original Android TV / Google TV launcher remains unchanged. No root, bootloader unlock, custom firmware or protected system partition changes are used. Widevine L1 and Google TV certification remain mandatory acceptance checks.
