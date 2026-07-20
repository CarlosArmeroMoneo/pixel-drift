# Changelog

Notable user-visible changes are documented here. Versions follow semantic versioning while the
project is in early development.

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
