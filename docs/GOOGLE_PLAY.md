# Google Play publication runbook

This runbook prepares Pixel Drift for its first Google Play release without weakening the existing
GitHub APK signing model.

## Current release identity

| Field | Value |
| --- | --- |
| App name | Pixel Drift |
| Application ID | `com.pixeldrift.wallpaper` |
| Version | `0.1.2` |
| Version code | `3` |
| Minimum Android | Android 6.0 / API 23 |
| Target Android | Android 16 / API 36 |
| Suggested Play category | Personalization |
| Monetization | Free, no ads, no in-app purchases |
| Network access | None |
| Accounts | None |

The application ID is permanent after the first Play upload. Verify it before creating the app in
Play Console.

## Signing architecture

Pixel Drift already publishes signed APKs outside Google Play. To preserve update compatibility,
configure Play App Signing with the **existing Pixel Drift release key as the app signing key**.
Do not allow Google to generate a different app signing key if users must be able to move between
GitHub and Google Play builds without uninstalling.

Use a separate **upload key** for AAB uploads:

1. The existing release/app-signing key remains the long-term application identity.
2. Google Play stores that app-signing key and signs device APKs delivered from the AAB.
3. GitHub Actions signs the AAB with a separate upload key.
4. Google verifies the upload certificate, then re-signs generated APKs with the app-signing key.

A compromised upload key can be reset without changing the app-signing identity. Never reuse the
GitHub release keystore as the workflow upload key.

### Create the upload key

Run this on a trusted local machine, not in CI:

```bash
keytool -genkeypair \
  -keystore pixel-drift-upload.p12 \
  -storetype PKCS12 \
  -alias pixel-drift-upload \
  -keyalg RSA \
  -keysize 4096 \
  -sigalg SHA256withRSA \
  -validity 10000
```

Use unique high-entropy passwords and keep an encrypted offline backup. Never commit the keystore,
passwords, or base64 output.

Export its public certificate for Play Console when requested:

```bash
keytool -exportcert -rfc \
  -keystore pixel-drift-upload.p12 \
  -storetype PKCS12 \
  -alias pixel-drift-upload \
  -file pixel-drift-upload-certificate.pem
```

Encode the keystore as one base64 line before placing it in GitHub Actions secrets:

```bash
base64 -w 0 pixel-drift-upload.p12
```

On macOS, use `base64 < pixel-drift-upload.p12 | tr -d '\n'`.

### Configure protected GitHub secrets

Create a GitHub environment named `google-play`, require manual approval, restrict deployment to
`main`, and add these environment secrets:

- `PIXELDRIFT_UPLOAD_KEYSTORE_B64`
- `PIXELDRIFT_UPLOAD_KEYSTORE_PASSWORD`
- `PIXELDRIFT_UPLOAD_KEY_ALIAS`
- `PIXELDRIFT_UPLOAD_KEY_PASSWORD`

The `Prepare Google Play bundle` workflow runs only by manual dispatch from `main`. It executes the
verification suite, builds the release AAB, signs it with the upload key, verifies the signature,
exports the public upload certificate, records package/version metadata, and publishes a protected
seven-day workflow artifact.

## First Play Console setup

1. Complete developer-account identity, contact, payment-profile, and device verification.
2. Create a new app:
   - Name: `Pixel Drift`
   - Default language: English (United States)
   - Type: App
   - Free or paid: Free
3. Confirm the package is exactly `com.pixeldrift.wallpaper` on the first AAB upload.
4. Enrol in Play App Signing.
5. Choose the option to provide the existing app-signing key. Use Play Console's current PEPK
   instructions to encrypt and upload the private release key. Do not upload unencrypted key
   material.
6. Register the separate upload certificate when Play Console requests it.
7. Run the GitHub `Prepare Google Play bundle` workflow and download the protected AAB artifact.
8. Upload the AAB first to **Internal testing**, not directly to Production.
9. Inspect App Bundle Explorer and confirm:
   - Application ID: `com.pixeldrift.wallpaper`
   - Version code: `3`
   - Target SDK: `36`
   - App-signing certificate matches the existing Pixel Drift release certificate
   - Upload certificate matches the new upload key

## Store listing packet

Ready-to-paste English and Spanish text lives under:

```text
fastlane/metadata/android/en-US/
fastlane/metadata/android/es-ES/
```

Play Console limits are 30 characters for the title, 80 for the short description, and 4,000 for
the full description.

### Required visual assets

Create these from the actual release build:

- App icon: 512 x 512 PNG, 32-bit with alpha, at most 1 MiB.
- Feature graphic: 1024 x 500 JPEG or 24-bit PNG without alpha.
- At least two phone screenshots, JPEG or 24-bit PNG without alpha.
- Prefer four portrait screenshots at 1080 x 1920 or higher for better Play discovery.

Screenshots must show the real app. A useful sequence is:

1. Built-in demo and artwork-import screen.
2. Sprite-sheet grid controls.
3. Playback, scaling, colour, and Battery Saver controls.
4. Android live-wallpaper preview with the demo visibly rendered.

Do not include private imported artwork, notifications, account identifiers, status-bar personal
information, or misleading device frames.

## App content declarations

Complete every declaration from the behaviour of the submitted binary, not from assumptions.
Pixel Drift `0.1.2` supports the following answers:

### Privacy policy

Use the public URL:

```text
https://github.com/CarlosArmeroMoneo/pixel-drift/blob/main/PRIVACY.md
```

The same policy is linked from the app UI.

### Ads

- Contains ads: **No**

### App access

- All functionality is available without credentials, membership, location, or special access.

### Data safety

For version `0.1.2` as currently implemented:

- Does the app collect or share required user data types? **No**
- Data sold or shared with third parties? **No**
- Accounts supported? **No accounts**

Selecting a local image through Android's document picker is on-device access rather than Google
Play "collection" because Pixel Drift does not transmit it off the device. Re-audit the declaration
before every release, especially if any SDK, crash reporting, analytics, advertising, network,
backup, or cloud feature is added.

### Content rating

Complete the IARC questionnaire honestly. Pixel Drift itself contains no violence, sexual content,
gambling, controlled substances, social interaction, or shared user-generated content. User-chosen
artwork remains local and is not distributed by the app.

### Target audience

Choose the intended age groups deliberately. Do not select child-directed audiences unless the app,
listing, privacy handling, and future development will comply with the Families policy.

### News, health, finance, government, and social features

Pixel Drift does not provide these functions. Answer the corresponding declarations accordingly
when Play Console presents them.

## Testing and production access

All accounts can use Internal testing immediately. For a personal developer account created after
November 13, 2023, Google currently requires a Closed test with at least 12 testers continuously
opted in for 14 days before the account can apply for Production access.

During testing, verify at minimum:

- Fresh installation from Google Play.
- Import of valid PNG and WebP sprite sheets.
- Rejection of oversized, malformed, animated, and unsupported files.
- Built-in demo fallback.
- Live-wallpaper preview and set flow on more than one launcher/device vendor.
- Screen-off, wallpaper-hidden, Battery Saver, rotation, process recreation, and device reboot.
- Upgrade from the latest GitHub APK without uninstalling, which proves signing-key compatibility.
- Long-running memory and battery behaviour.
- Play pre-launch report findings.

Do not apply for Production access until tester feedback is recorded and material defects are fixed.

## Release sequence

1. Merge the release changes to `main` only after CI passes.
2. Create and protect the upload key and GitHub `google-play` environment.
3. Configure Play App Signing with the existing release/app-signing key.
4. Run `Prepare Google Play bundle` from `main`.
5. Upload to Internal testing and complete the store listing and App content declarations.
6. Run the required Closed test when the account is subject to it.
7. Review Play pre-launch reports, policy warnings, device compatibility, and signing certificates.
8. Promote the same tested artifact to Production using a staged rollout.
9. Tag `v0.1.2` separately only when the matching GitHub APK release is ready.

## Official references

- Target API requirements: https://support.google.com/googleplay/android-developer/answer/11926878
- Testing requirements: https://support.google.com/googleplay/android-developer/answer/14151465
- Data safety: https://support.google.com/googleplay/android-developer/answer/10787469
- Store listing setup: https://support.google.com/googleplay/android-developer/answer/9859152
- Preview assets: https://support.google.com/googleplay/android-developer/answer/9866151
- Android app signing: https://developer.android.com/studio/publish/app-signing
