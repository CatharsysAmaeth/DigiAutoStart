# CI/CD with GitHub Actions

## Goals

Build and test Android code on GitHub-hosted runners without requiring the
developer Mac to compile the application.

## Android CI workflow

Workflow: `.github/workflows/android-ci.yml`

Triggers:
- Relevant pushes to `main` and `feature/**`.
- Pull requests targeting `main`.
- Manual execution through `workflow_dispatch`.

Runner:
- GitHub-hosted `ubuntu-latest`.
- Temurin JDK 21.
- Android SDK Platform 36 and Build Tools 36.0.0.
- Gradle 8.13 installed by Gradle Actions.

The workflow runs:
1. Android SDK setup.
2. Gradle dependency caching.
3. `testDebugUnitTest`.
4. `assembleDebug`.
5. Uploads the debug APK as a 14-day workflow artifact.

A successful CI build proves that the APK compiles and unit tests pass. It
does not prove compatibility with the DIGI R1A firmware.

## APK artifact

The debug APK is intended for installation tests. It is unsigned with a
production key (Gradle debug builds use the standard debug signing config).
It is not a production release.

## Production signing and releases

Production signing is deliberately deferred.

Before enabling signed releases:
- Confirm the permanent Android application ID.
- Generate the production signing key in a trusted environment.
- Keep the keystore and passwords out of Git.
- Store signing material only in protected GitHub Secrets / Environment.
- Maintain a secure offline backup of the signing key.
- Add a tag-triggered release workflow only after signing is tested.

Never commit a keystore, private key, password, token or credential.

## Device testing boundary

GitHub-hosted runners cannot validate standby/wake behavior or DRM state on
the physical DIGI R1A. Those checks remain device acceptance tests.
Widevine L1 and Google / Android TV certification must not be put at risk.

## Security

- Workflows use read-only repository permissions.
- Do not expose secrets to pull-request code from untrusted forks.
- Keep build logs and artifacts free of credentials and personal data.
