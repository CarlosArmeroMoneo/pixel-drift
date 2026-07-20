package com.pixeldrift.wallpaper;

import android.graphics.Color;

import java.util.Locale;
import java.util.Objects;

/** Immutable, sanitized wallpaper configuration used by each engine instance. */
public final class WallpaperConfig {
    public static final int MAX_GRID_AXIS = 64;
    public static final int MAX_FRAMES = 256;

    private final boolean customAsset;
    private final int assetRevision;
    private final int columns;
    private final int rows;
    private final int frameCount;
    private final int fps;
    private final String scaleMode;
    private final int backgroundColor;
    private final boolean freezeInBatterySaver;

    public WallpaperConfig(
            boolean customAsset,
            int assetRevision,
            int columns,
            int rows,
            int frameCount,
            int fps,
            String scaleMode,
            int backgroundColor,
            boolean freezeInBatterySaver
    ) {
        this.customAsset = customAsset;
        this.assetRevision = Math.max(0, assetRevision);
        this.columns = clamp(columns, 1, MAX_GRID_AXIS);
        int safeRows = clamp(rows, 1, MAX_GRID_AXIS);
        this.rows = Math.min(safeRows, Math.max(1, MAX_FRAMES / this.columns));
        int cells = Math.min(MAX_FRAMES, this.columns * this.rows);
        this.frameCount = clamp(frameCount, 1, cells);
        this.fps = clamp(fps, 0, PowerPolicy.MAX_FPS);
        this.scaleMode = sanitizeScaleMode(scaleMode);
        this.backgroundColor = backgroundColor | 0xFF000000;
        this.freezeInBatterySaver = freezeInBatterySaver;
    }

    public static WallpaperConfig builtInDefaults() {
        return new WallpaperConfig(
                false,
                0,
                DemoSpriteFactory.COLUMNS,
                DemoSpriteFactory.ROWS,
                DemoSpriteFactory.FRAME_COUNT,
                8,
                "fill",
                Color.rgb(7, 8, 20),
                true
        );
    }

    public boolean usesCustomAsset() {
        return customAsset;
    }

    public int getAssetRevision() {
        return assetRevision;
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int getFps() {
        return fps;
    }

    public String getScaleMode() {
        return scaleMode;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public boolean freezesInBatterySaver() {
        return freezeInBatterySaver;
    }

    public boolean sameAssetLayout(WallpaperConfig other) {
        return other != null
                && customAsset == other.customAsset
                && assetRevision == other.assetRevision
                && columns == other.columns
                && rows == other.rows
                && frameCount == other.frameCount;
    }

    public static int parseColor(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("Background color is required");
        }
        String value = raw.trim();
        if (!value.startsWith("#")) {
            value = "#" + value;
        }
        if (value.length() != 7) {
            throw new IllegalArgumentException("Use #RRGGBB");
        }
        try {
            return Color.parseColor(value);
        } catch (IllegalArgumentException error) {
            throw new IllegalArgumentException("Invalid background color", error);
        }
    }

    public static String formatColor(int color) {
        return String.format(Locale.US, "#%06X", color & 0x00FFFFFF);
    }

    private static String sanitizeScaleMode(String value) {
        String normalized = value == null ? "fill" : value.trim().toLowerCase(Locale.US);
        if (!normalized.equals("fit") && !normalized.equals("fill") && !normalized.equals("stretch")) {
            return "fill";
        }
        return normalized;
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof WallpaperConfig)) {
            return false;
        }
        WallpaperConfig that = (WallpaperConfig) object;
        return customAsset == that.customAsset
                && assetRevision == that.assetRevision
                && columns == that.columns
                && rows == that.rows
                && frameCount == that.frameCount
                && fps == that.fps
                && backgroundColor == that.backgroundColor
                && freezeInBatterySaver == that.freezeInBatterySaver
                && scaleMode.equals(that.scaleMode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                customAsset,
                assetRevision,
                columns,
                rows,
                frameCount,
                fps,
                scaleMode,
                backgroundColor,
                freezeInBatterySaver
        );
    }
}
