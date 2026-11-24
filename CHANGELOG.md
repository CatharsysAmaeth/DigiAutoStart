# Changelog

All notable user-facing changes are documented here. Internal diagnostics, private service locations, device identifiers, credentials, signing material and personal/network details are intentionally excluded.

## 0.6.0 — First normal Release with automatic startup

- Promote the device-tested standby and startup automation from the prototype build to the normal Release.
- Add Mode A (reopen the selected TV app after every wake) and Mode B (reopen it only when it was the last foreground app before standby).
- Keep the wake delay configurable.
- Add optional launch of the selected TV app when the device starts, independently of the standby mode.
- Restore enabled automation after a device restart or app update.
- Keep the configuration screen in Release while excluding Debug menus, diagnostic collection and the private test updater.
- Preserve the original Android TV launcher and platform certification; no root access, bootloader unlocking or firmware modification is required.

## 0.5.4 — DAdA visual identity

- Complete the DAdA name and “DIGI TV · Assistente de arranque” description.
- Add the launcher icon variants and Android TV banner.
- Refine the simplified home screen and consolidated configuration experience.

## 0.5.3 — Unified configuration and target discovery

- Simplify the home screen and refresh the detected app and automation status automatically.
- Consolidate standby mode, wake delay, launch-on-boot, permissions and target selection under “Configurar”.
- Keep the official DIGI TV app as the default target.
- Discover compatible Android TV launcher apps as a fallback; select automatically only when there is one unambiguous DIGI-labelled candidate, otherwise require user selection.
- Use the selected target consistently for standby and startup launches.

## 0.5.2 — Startup launch and foreground verification

- Add optional launch of DIGI TV when the device starts, disabled by default and independent of Mode B.
- Restore the enabled foreground monitor after startup.
- Check whether DIGI TV actually becomes foreground after an automated launch attempt.

## 0.5.1 — Status and test usability

- Display the installed app version and identify prototype builds clearly.
- Improve the presentation of automation actions and their status.

## 0.5.0 — Persistent standby automation prototype

- Keep the screen-state monitor running in a foreground service, independently of the app's main screen.
- Implement Mode A and Mode B decisions with a configurable wake delay.
- Restore the monitor after device restart and app replacement.
- Add bounded, opt-in diagnostics for development and device testing.

## 0.4.0 — Standby automation prototype

- Introduce the first automatic relaunch prototype.
- Add Mode A (always reopen) and Mode B (reopen only if the TV app was last in use).
- Add a configurable delay and initial device-test controls.

## 0.1.0 — Initial device-test build

- Add the Android TV-friendly interface and basic preferences.
