# Debug diagnostics

## Scope

The diagnostics implementation is deliberately isolated under `src/debug`.
The Release variant receives only a no-op API shim under `src/release`, has
no diagnostics UI, no diagnostic receiver and no ZIP-encryption dependencies.

The Debug build records only app-owned lifecycle/launch events and the
ACTION_SCREEN_ON / ACTION_SCREEN_OFF broadcasts observed while the app process
is alive. A received screen broadcast is recorded as an observation; it is
not proof that the device entered or left standby.

## Privacy and retention

- Capture is opt-in and starts only when the user selects "Iniciar recolha".
- The log is bounded to approximately 1 MiB, with one previous segment.
- No serial number, MAC address, IP address, SSID, account information,
  credentials, DIGI TV content or broad app-usage history is collected.
- Device summary contains Android version, manufacturer/model and app version.
- "Apagar registos" deletes the current and previous event logs.
- The password is entered at export time and is not saved by the app.
- Export uses ZIP AES-256 encryption. The password is not embedded in this
  public repository or in the APK source.
- The encrypted ZIP is shared using Android's system share sheet.

## Current limitations

This milestone does not upload reports to GitHub. The available GitHub
integration can modify repository contents and pull requests, but does not
provide the permission needed to create a private repository or configure
repository secrets. Automatic upload will be added only after a private
destination and a narrowly scoped upload credential are available.

No standby automation is implemented. No Usage Access, Accessibility,
overlay, root, bootloader or firmware permissions are requested.
