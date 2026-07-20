# Battery and lifecycle validation

Do not publish a universal mAh claim. Wallpaper power depends on the device, launcher, display refresh policy, brightness, and artwork. Use a physical-device A/B baseline and preserve the raw traces.

## Automated gates

Run on every change:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew lintProfile
./gradlew assembleDebug
./gradlew assembleProfile
./gradlew assembleRelease
./gradlew bundleRelease
```

Before release, manually exercise the lifecycle matrix below on the minimum supported API,
Android 12/13, Android 14+, and a current Samsung device. Add instrumentation tests before making
`connectedDebugAndroidTest` a required CI gate; the current project contains pure unit tests only.

Verify the merged manifest contains:

- an exported wallpaper service protected by `android.permission.BIND_WALLPAPER`;
- no internet, wake-lock, alarm, foreground-service, boot, or broad storage permission;
- the required `android.service.wallpaper` metadata.

## Lifecycle cases

Exercise active and preview engines both separately and simultaneously:

1. Preview, cancel, preview again, and apply.
2. Switch home → app → home rapidly 100 times.
3. Turn the display off/on 20 times, including AOD if present.
4. Toggle Battery Saver while the wallpaper is visible.
5. Restart the launcher, kill the app process, reboot, rotate, and resize on a tablet/foldable.
6. Replace the active asset and change its grid while a preview engine also exists.
7. Delete the original imported document and confirm the private copy still renders.
8. Attempt malformed, truncated, oversized, animated, and wrong-extension inputs; the last valid asset must survive.
9. Rotate during a maximum-size import; the replacement screen must stay disabled, then show the same success or failure result.

Release criteria:

- Within one second of hidden, screen-off, or surface loss: zero pending frame callbacks, draws, newly started decodes, or periodic process wakeups. A decode already in flight is noninterruptible and must finish inside that measured bound for the permitted asset budget.
- Repeated visibility events never create duplicate animation loops.
- A stall draws only the current logical frame; it never catches up expired frames.
- Draw count is no more than the selected FPS plus one transition frame per second.
- Battery Saver produces one transition draw and then no repeated renderer work.
- No decode, file read, preference read, or meaningful Java allocation occurs per steady-state frame.
- Preview and active engines can release independent leases without recycling pixels still used by the other. Verify one shared allocation for a common revision and no more than the old and new 16 MiB sheets during replacement.
- Pixel edges stay unfiltered in Fit, Fill, and Stretch modes.

## Power experiment

Use the minified, release-derived `profile` build on an API 29+ physical device supported by Android Studio Power Profiler. It is debug-signed and opts into shell profiling while otherwise following release settings. Record Perfetto/System Trace alongside power rails where available.

Control the experiment:

- fixed brightness and refresh-rate mode;
- airplane mode and no notifications or updates;
- identical launcher page, widgets, and icon layout;
- thermally settled, unplugged device;
- identical opaque sprite sheet and scale mode;
- at least three 20–30 minute runs per condition, alternating order.

Compare:

1. the first animation frame set as a static wallpaper;
2. Pixel Drift Static;
3. 4 FPS;
4. default 8 FPS;
5. 12 FPS;
6. 24 FPS.

Capture total charge/energy delta, CPU and GPU energy, process CPU time, render-thread wakeups, canvas posts, p50/p95 draw time, allocations, GC count, decoded bitmap memory, peak RSS, and 30 minutes of screen-off activity.

Initial regression gate: default 8 FPS must not regress median CPU+GPU energy by more than 10% against the project's previously recorded 8 FPS baseline. Hidden and screen-off traces should be indistinguishable from the static control within measurement noise.

Use [Android Studio Power Profiler](https://developer.android.com/studio/profile/power-profiler) for device power rails and [Macrobenchmark guidance](https://developer.android.com/topic/performance/benchmarking/macrobenchmark-overview) for why emulator timing is not a physical-device performance result.
