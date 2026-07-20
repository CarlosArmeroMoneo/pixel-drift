# Changelog

Notable user-visible changes are documented here. Versions follow semantic versioning while the
project is in early development.

## [0.2.0] - 2026-07-20

### Added

- Independent applied configurations for Home and Lock live wallpapers on Android 14 and newer.
- Explicit destination selection and clear draft-versus-applied status in the configuration screen.
- Android 16 apply-callback support, Android 14 engine-target handling, and conservative lifecycle
  fallbacks for earlier supported Android versions.

### Fixed

- Importing artwork or saving display settings no longer changes an already-active wallpaper.
- Preview engines render the editable draft while active engines render only their last explicitly
  applied slot.
- Private revision cleanup now retains every sprite sheet referenced by the draft, Home, or Lock.

## [0.1.2] - 2026-07-20

### Added

- Android App Bundle generation in ordinary CI.
- A manually approved Google Play workflow that signs AAB uploads with a separate upload key.
- A Google Play publication runbook and localized English and Spanish store-listing copy.
- An accessible in-app link to the public privacy policy.

### Changed

- Expanded the privacy policy with Google Play disclosures covering on-device artwork access,
  collection, sharing, retention, deletion, and security.

## [0.1.1] - 2026-07-20

### Fixed

- Removed a wallpaper-host-incompatible `SurfaceHolder.setKeepScreenOn(false)` call that crashed
  Android's live-wallpaper preview before the first frame.
- Added a build-time contract preventing the forbidden call from returning.
- Contained unexpected renderer, synchronous redraw, and canvas-memory failures.
- Added one bounded software-render retry after a hardware bitmap-upload memory failure.

### Verified

- 29 unit tests and strict Android lint pass.
- The exact signed APK cold-started in the Android API 36 live-wallpaper picker, bound the wallpaper
  service, rendered its surface, and remained alive without a fatal exception.

## [0.1.0] - 2026-07-20

### Added

- Static PNG and WebP sprite-sheet import.
- Grid, frame-count, scale, background-color, and FPS controls.
- Battery Saver freeze option and built-in demo artwork.
- Lifecycle-gated renderer with no network, wake-lock, foreground-service, alarm, job, or sensor
  permission.
