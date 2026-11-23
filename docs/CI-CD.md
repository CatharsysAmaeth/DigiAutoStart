# CI/CD with GitHub Actions

## Goals

Use GitHub-hosted runners to validate and build the Android application without
requiring the developer Mac to perform CI builds.

## Android CI workflow

Workflow: `.github/workflows/android-ci.yml`

Triggers:
- Pushes to `main` that change Android/Gradle project files or the workflow.
- Pull requests targeting `main` with those changes.
- Manual execution through GitHub Actions (`workflow_dispatch`).

Runner:
- GitHub-hosted `ubuntu-latest`.
- Temurin JDK 21.
- Android SDK command-line tools, platform-tools, Android Platform 36
  and Build Tools 36.0.0.

When the Gradle Wrapper is present, the workflow:
1. Validates the Gradle Wrapper.
2. Sets up Java and the Android SDK.
3. Configures Gradle caching.
4. Runs `testDebugUnitTest` and `assembleDebug`.
5. Uploads the debug APK as a workflow artifact when the build succeeds.

Until `gradlew` exists, the workflow reports a notice and skips the build.
This allows the CI configuration to be introduced before the Android project.

## Artifact policy

Debug APKs are CI artifacts, not production releases. They are intended for
installation tests on the DIGI R1A and expire according to the workflow's
artifact retention setting.

## Production releases and signing

Production APK signing is deliberately not configured yet.

Before enabling signed releases:
- Decide and record the Android application ID.
- Generate the production signing key in a trusted local environment.
- Keep the keystore and passwords out of Git.
- Store signing material only in appropriately protected GitHub Secrets
  or a protected GitHub Environment.
- Define a recovery backup for the signing key.
- Add a tag-triggered release workflow only after the signing process is tested.

Never commit a keystore, private signing key, password, token or credential.

## Device testing boundary

GitHub-hosted runners cannot validate standby/wake behavior or DRM state on
the physical DIGI R1A. Those checks remain manual device acceptance tests.
Widevine L1 and Google/Android TV certification must not be put at risk.

## Security and maintenance

- Workflows use read-only repository permissions unless a future publishing
  workflow has a documented need for additional permissions.
- Third-party actions use maintained major-version tags and should be reviewed
  periodically for security updates.
- Do not expose secrets to pull-request code from untrusted forks.
- Keep build logs and artifacts free of credentials and personal data.
