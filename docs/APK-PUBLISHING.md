# APK publishing

The `android-ci` workflow builds and tests both variants on pushes to `main`.

- The Release APK is committed to `apk/DigiAutoStart-normal.apk` in this public repository.
- The Debug APK is uploaded to `apk/DigiAutoStart-debug.apk` in the private `DigiAutoStart-Diagnostics` repository.
- The private upload uses the `DIAGNOSTICS_REPO_TOKEN` Actions secret. It must be a fine-grained token scoped to the private diagnostics repository with Contents read/write permission.
- Pull requests build and test the APKs but do not publish them.

The Release APK is checked to ensure that diagnostics code, Zip4j, and the Debug-only internet permission are absent.
