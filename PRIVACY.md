# Pixel Drift Privacy Policy

**Last updated: July 20, 2026**

Pixel Drift is developed and published by **Carlos Armero Moneo**. This policy applies to the
Pixel Drift Android application with package name `com.pixeldrift.wallpaper`.

## Summary

Pixel Drift is an offline live-wallpaper application. It does not collect, transmit, sell, or share
personal or sensitive user data. It contains no advertising, analytics, telemetry, crash-reporting,
or other third-party data-collection SDK.

## Data the app accesses

Pixel Drift can access a PNG or WebP image only after the user deliberately selects it through
Android's system document picker. The selected image is used solely to render the user's live
wallpaper. Pixel Drift does not scan media or storage outside the document selected by the user.

The app does not access accounts, location, contacts, calendar data, messages, microphone, camera,
phone state, health data, financial data, advertising identifiers, or hardware identifiers.

## Collection and sharing

Pixel Drift has no Internet permission and does not transmit user data or device data off the
user's device. No data is collected by the developer and no data is shared with third parties.

Android, Google Play, and the device manufacturer may independently process installation,
security, purchase, or operating-system diagnostic information under their own terms and privacy
policies. Pixel Drift does not receive that information from the app.

## Local storage, security, and retention

After validating a selected image, Pixel Drift stores a private local copy so the wallpaper remains
available if the original document moves. Imported files are treated as untrusted input and are
format-checked, size-bounded, decoded away from the main thread, and installed atomically into the
app's private storage.

App backup and device-to-device extraction are disabled. The private artwork copy and settings are
retained on the device until they are replaced, the app's storage is cleared, or Pixel Drift is
uninstalled. Clearing app storage or uninstalling Pixel Drift deletes the private copy and settings;
it does not delete the original image selected through Android.

## Accounts and deletion requests

Pixel Drift does not provide user accounts and the developer does not hold user data that can be
associated with an account. Users can delete all Pixel Drift local data at any time through
Android's app-storage settings or by uninstalling the app.

## Changes to this policy

Material changes to Pixel Drift's data handling will be reflected in this policy, the app's Google
Play Data safety declaration, and the application before the changed behavior is released.

## Privacy contact

For a privacy question that contains no sensitive information, open an issue through:

https://github.com/CarlosArmeroMoneo/pixel-drift/issues/new/choose

For a security or privacy vulnerability, use GitHub's private vulnerability-reporting flow and do
not include private artwork or personal data in a public issue:

https://github.com/CarlosArmeroMoneo/pixel-drift/security/advisories/new
