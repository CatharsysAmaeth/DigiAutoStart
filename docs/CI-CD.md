# CI/CD with GitHub Actions

## Goals

Build and test the Android application on repeatable GitHub-hosted runners.

## Android CI workflow

Workflow: `.github/workflows/android-ci.yml`

Triggers:
- Relevant pushes to `main` and `feature/**`.
- Pull requests targeting `main`.
- Manual execution through `workflow_dispatch`.

Build environment:
- GitHub-hosted Ubuntu runner.
- Temurin JDK 21.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Gradle 8.13.

The workflow:
1. Runs unit tests.
2. Builds Debug and normal Release APKs.
3. Checks that Release contains the standby/startup automation and required permissions.
4. Checks that Release excludes private diagnostics, the test updater and Debug-only network/package-install permissions.
5. Creates or updates a versioned GitHub Release with the stable APK only from `main`; pull requests do not publish it.

A successful CI build proves that compilation, unit tests and APK-content checks passed. It does not replace physical-device validation of standby/wake behavior.

## Signing and updates

Release APKs must retain the application's established signing identity so an existing installation can be updated without uninstalling it. Signing material is kept outside the public repository in protected CI storage. Never commit signing material or passwords.

## Device testing boundary

Standby/wake behavior and DRM certification must be checked on physical Android TV hardware. GitHub-hosted runners cannot validate firmware behavior or Widevine state.

## Security

- Do not expose secrets to pull-request code from untrusted forks.
- Keep credentials, signing material, diagnostic reports and personal/device data out of public source and build logs.
