# Install Pixel Drift

## Recommended: signed GitHub Release

1. Open the repository's **Releases** page.
2. Download `PixelDrift-<version>.apk` and `SHA256SUMS` from the latest release.
3. Confirm the APK checksum when practical.
4. On Android, allow your browser or file manager to install unknown apps only for this install.
5. Open the APK, install Pixel Drift, and disable that temporary installer permission afterward.

Do not install debug APKs or unsigned CI artifacts from the Actions page. Official releases are
non-debuggable and use the certificate fingerprint published in [SECURITY.md](../SECURITY.md).

## Updating

A normally signed release installs over an earlier official release. If Android reports a signing
conflict, the installed build came from a different signing identity. Back up the original artwork,
uninstall the conflicting build, install the official release, and reimport the sheet. Uninstalling
deletes Pixel Drift's private copy and settings.

## First wallpaper

1. Open Pixel Drift.
2. Keep the demo or choose **Import artwork**.
3. Enter the sheet's columns, rows, and active frame count.
4. Choose FPS, scale, and background color.
5. Tap **Save**, then **Preview / Set**.

## Supported artwork

- Static PNG or static WebP.
- Equal-size grid cells, read left-to-right and then top-to-bottom.
- Up to 25 MiB encoded, 4,194,304 decoded pixels, 16 MiB bitmap allocation, and 4096 px on either
  axis.
- Up to 64 columns or rows, 256 cells, and 24 FPS.

Animated GIF, animated WebP, and APNG are deliberately rejected. Export animation frames into one
static sprite sheet instead.

## Troubleshooting

- **Preview is unavailable:** confirm the device supports Android live wallpapers.
- **Import rejected:** use a static PNG/WebP, reduce dimensions, and check that the grid divides the
  image evenly.
- **Animation appears static:** select a non-static FPS, use more than one active frame, and check
  whether Battery Saver freeze is enabled.
- **Install reports a conflict:** follow the updating guidance above; signatures cannot be bypassed
  safely.
