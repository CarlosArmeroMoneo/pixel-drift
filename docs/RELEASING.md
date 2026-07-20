# Release process

Only maintainers with access to the protected `release` environment can publish an official APK.

## One-time repository setup

1. Create a GitHub environment named `release` and require maintainer approval.
2. Add these environment secrets:
   - `PIXELDRIFT_KEYSTORE_B64` — base64 of the PKCS#12 release key
   - `PIXELDRIFT_KEYSTORE_PASSWORD`
   - `PIXELDRIFT_KEY_ALIAS`
   - `PIXELDRIFT_KEY_PASSWORD`
3. Enable private vulnerability reporting.
4. Protect `main`: require pull requests and the Android build check, block force pushes, and require
   conversation resolution.
5. Restrict tag creation for `v*` to maintainers when the repository rules UI supports it.

Never store the keystore or password in Git, release assets, caches, logs, or ordinary Actions
artifacts. Retain a separate encrypted offline backup.

## Publish a version

1. Update `versionCode`, `versionName`, `CHANGELOG.md`, and user documentation in a pull request.
2. Run the physical-device gates in `BATTERY_VALIDATION.md`.
3. Merge only after CI succeeds.
4. Create an annotated tag matching the manifest version, for example `v0.1.1`, on the reviewed
   `main` commit and push it.
5. The `Signed release` workflow rebuilds, tests, lints, signs, verifies the pinned certificate,
   checks the tag/version match, and creates the GitHub Release.
6. Download the published APK and verify `SHA256SUMS` and the certificate before announcing it.

Never rerun a release with a different key. If a release workflow fails after signing but before
publication, diagnose the workflow and preserve the existing signing identity.
