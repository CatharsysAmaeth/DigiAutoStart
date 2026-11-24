# Changelog

## 0.5.3 — DAdA branding, unified configuration and target discovery

- Rename the app shown on Android TV to DAdA and add a blue adaptive launcher icon.
- Simplify the home screen; refresh the detected app and automation state automatically.
- Remove the manual DIGI TV launch test and the redundant manual status refresh.
- Consolidate standby mode, delay, boot launch, permissions and target selection under “Configurar”.
- Keep ro.digionline.tv as the default target and discover replacement Android TV launcher apps when it is unavailable.
- Automatically select a replacement only when exactly one unambiguous DIGI-labelled candidate exists; otherwise require user selection.
- Use the selected package consistently for standby and boot launch.

## 0.5.2 — boot behavior and launch verification

- Add optional DIGI TV launch at box startup, disabled by default and independent of Mode B.
- Keep the foreground monitor restart at boot whenever automation is enabled.
- Verify whether DIGI TV actually becomes foreground after automated launch attempts; record observed/not-observed outcomes.
- Keep visible app version and DEBUG marker.

## 0.5.0 — persistent standby automation prototype

- Added Debug foreground service to keep SCREEN_OFF / SCREEN_ON receiver registered outside the Activity.
- Added Mode A / Mode B decisions and configurable delay.
- Added bounded, opt-in encrypted diagnostics for device testing.

## 0.1.0 — initial device-test build

- Added TV-friendly interface and preferences.
