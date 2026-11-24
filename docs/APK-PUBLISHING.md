# APK publishing

The Android CI workflow builds and tests the Debug and normal Release variants.

- The normal Release APK is published at `apk/DigiAutoStart-normal.apk` after changes are merged to `main`.
- Release includes the user-facing configuration and tested standby/startup automation.
- Release excludes Debug menus, diagnostic collection, private test-updater code and Debug-only network/package-install permissions.
- Debug builds are for development and device testing; they are not presented as normal public releases.
- Pull requests build and test both variants but do not publish the public APK.

The workflow checks the contents of the Release manifest and DEX files: automation must be present, while private diagnostics and Debug-only tools must be absent.

Signing material and credentials are kept outside the public source tree in protected CI configuration. Never commit a keystore, private key, password, token or credential.
