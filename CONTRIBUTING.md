# Contributing

Thanks for helping improve Pixel Drift. Keep changes focused on predictable rendering, low power
use, privacy, and safe handling of untrusted image input.

## Development setup

- JDK 17
- Android SDK 36 and Build Tools 36.0.0
- The checked-in Gradle 8.13 wrapper

Run the same core verification used by CI:

```bash
python3 scripts/check_repository.py
./gradlew --no-daemon --dependency-verification=strict --warning-mode all \
  verifyWallpaperSurfaceContract testDebugUnitTest lintDebug lintProfile \
  assembleDebug assembleProfile assembleRelease
```

Do not weaken dependency verification to make a build pass. When intentionally updating a
dependency, review the provenance and update verification metadata in the same pull request.

## Design expectations

- Schedule no frame while the wallpaper is hidden, the screen is off, or the surface is absent.
- Keep authored FPS bounded and skip stale frames after a stall.
- Avoid permanent render loops, wake locks, background services, analytics, and unnecessary
  permissions.
- Treat document-provider bytes and saved preferences as untrusted.
- Bound encoded bytes, decoded pixels, bitmap allocation, grid dimensions, and retry behavior.
- Never perform bitmap decoding on Android's preview or activity main thread.
- Preserve the built-in demo fallback and fail closed instead of crash-looping.

## Pull requests

Explain user impact, battery impact, and security/privacy impact. Add tests for pure logic and attach
sanitized device evidence when Android framework behavior changes. Never commit imported artwork,
device data, signing files, credentials, or generated APKs.

Security vulnerabilities belong in a private advisory; see [SECURITY.md](SECURITY.md).
