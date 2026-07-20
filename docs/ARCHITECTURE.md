# Architecture

Pixel Drift uses Android platform views and `WallpaperService`. It does not depend on Compose, a
game engine, an animated-image decoder, a permanent OpenGL loop, or any third-party runtime library.

## Rendering gates

Each active or preview engine owns one background renderer thread. A frame is eligible only when:

```text
visible && surfaceReady && screenInteractive && !destroyed
```

The engine removes scheduled work on invisibility, screen-off, surface destruction, and teardown.
It uses monotonic epoch-based deadlines, so a stall skips expired frames rather than generating a
catch-up burst. Static and single-frame sheets draw once and schedule nothing further.

## Pixel path

Imports are validated and decoded off the activity thread, then installed as a new app-private
revision through `AtomicFile`. Preferences commit the selected revision and grid as one tuple. A
service-scoped reference-counted cache lets active and preview engines share one immutable decoded
bitmap for the same revision.

The steady-state draw path reuses `Paint`, source and destination `Rect` objects, and its timeline
tick. Nearest-neighbor bitmap sampling preserves pixel edges. Hardware Canvas is preferred on
Android 8+, with one bounded software retry and a terminal pause after repeated failures.

## Power policy

- Authored playback is capped at 24 FPS; 8 FPS is the default.
- Touch and offset notifications are disabled.
- Battery Saver can freeze playback on one frame.
- No wake lock, foreground service, alarm, job, sensor, network, or battery-optimization exemption.
- No hidden renderer survives when the wallpaper is not visible.

Actual energy use still depends on the phone, launcher, display, sheet size, and authored FPS. See
[BATTERY_VALIDATION.md](BATTERY_VALIDATION.md) for the physical-device release gate.

## Failure containment

- Invalid or damaged imported state falls back to the built-in demo.
- Image and grid bounds prevent unbounded allocation and authored work.
- Canvas failures cannot escape the live-wallpaper host callback.
- A hardware memory failure receives one software attempt; a software memory failure pauses.
- A build-time contract rejects wallpaper-incompatible `setKeepScreenOn()` calls.

## Source map

- `PixelWallpaperService.java` — lifecycle gates, rendering, failure containment, and scheduling
- `FrameTimeline.java` — allocation-free, drift-resistant frame timing
- `AssetImporter.java` — container validation, decode limits, and atomic import
- `AssetStore.java` / `AssetRepository.java` — revision transactions and shared bitmap leases
- `AssetWorkQueue.java` — rotation-safe serialized import work
- `DemoSpriteFactory.java` — built-in procedural sprite sheet
- `MainActivity.java` — import and configuration UI
- `app/src/test` — scheduler, power, grid, geometry, and work-state unit tests
