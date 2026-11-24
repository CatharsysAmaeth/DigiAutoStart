# Debug diagnostics

## Scope

The diagnostics implementation is deliberately isolated under `src/debug`.
The Release variant receives only a no-op API shim under `src/release`, has
no diagnostics UI, no diagnostic receiver, no INTERNET permission and no
ZIP-encryption or upload dependencies.

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
- The ZIP contains the current and previous log segment, when present.

## Private GitHub upload

The Debug build can upload the latest encrypted ZIP to
`CatharsysAmaeth/DigiAutoStart-Diagnostics`, under `reports/`.

The user must create a GitHub fine-grained personal access token with:
- Resource owner: `CatharsysAmaeth`
- Repository access: only `DigiAutoStart-Diagnostics`
- Repository permissions: `Contents: Read and write`
- An expiry date (prefer a short lifetime, e.g. 30 days).

The token is entered in the Debug app, encrypted at rest using an
Android Keystore AES-GCM key, and never included in logs or reports. The app
uses it only for the private repository API. Revoking the token in GitHub
immediately disables future uploads. Clearing app data removes the encrypted
token; uninstalling the app removes the app's Keystore key.

Only the already AES-256-encrypted ZIP is uploaded. The GitHub repository
therefore stores ciphertext, not readable diagnostic content. The ZIP
password is separate from the GitHub token.

## Limitations

Screen broadcasts are observations, not proof of standby/wake. No automatic
standby detection or DIGI TV relaunch is implemented yet. No Usage Access,
Accessibility, overlay, root, bootloader or firmware permissions are requested.
