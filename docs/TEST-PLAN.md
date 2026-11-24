# DAdA 0.6.0 acceptance test plan

## T1 — Installation and upgrade

- Install the normal Release APK.
- Confirm DAdA appears in the Android TV app list with the correct icon and banner.
- Confirm an existing signed installation can be updated without uninstalling it.
- Confirm saved preferences remain available after the update.
- Confirm the original launcher and DIGI TV remain functional.
- Confirm Widevine L1 and platform certification remain intact.

## T2 — Configuration and permissions

- Open the “Configurar” screen using TV remote navigation.
- Confirm Mode A and Mode B can be selected and saved.
- Confirm the wake delay can be set from 0 to 30 seconds.
- Confirm launch-at-startup can be enabled independently.
- Confirm the app explains and requests only the required special permissions.
- Confirm Mode B requires Usage Access and both automation modes require Display over other apps access.

## T3 — Standby and wake

- Test Mode A across repeated standby/wake cycles.
- Test Mode B with DIGI TV as the last foreground app.
- Test Mode B with another app or the launcher as the last foreground app.
- Confirm the configured delay is respected.
- Confirm the disabled state does not reopen the TV app.

## T4 — Startup and recovery

- Test startup launch when enabled.
- Confirm startup launch remains disabled by default.
- Confirm enabled standby monitoring is restored after a device restart.
- Confirm enabled monitoring is restored after an application update.
- Confirm startup launch remains independent of Mode A/B.

## T5 — Target resolution

- Confirm the official DIGI TV app is selected by default when available.
- Test conservative discovery of compatible Android TV alternatives.
- Confirm ambiguous candidates require manual selection.
- Confirm the selected target is used consistently for standby and startup.

## T6 — Release isolation and privacy

- Confirm the normal Release has no Debug menu, diagnostic collection or private test updater.
- Confirm Release does not request Debug-only internet or package-install permissions.
- Confirm no device identifiers, credentials, signing material or private network details are included in public documentation.

## Evidence rules

Mark a test as passed only when supported by observed device results. Distinguish confirmed behavior from failed tests and unverified assumptions. Do not publish device identifiers, diagnostic reports or private network details.
