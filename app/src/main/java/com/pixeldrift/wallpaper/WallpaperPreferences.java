package com.pixeldrift.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;

/** Small SharedPreferences facade. Reads are sanitized before reaching the renderer. */
public final class WallpaperPreferences {
    static final String FILE_NAME = "pixel_drift_wallpaper";
    static final String KEY_SOURCE_CUSTOM = "source_custom";
    static final String KEY_ASSET_REVISION = "asset_revision";
    static final String KEY_ASSET_WIDTH = "asset_width";
    static final String KEY_ASSET_HEIGHT = "asset_height";
    private static final String KEY_SCHEMA = "schema";
    static final String KEY_COLUMNS = "columns";
    static final String KEY_ROWS = "rows";
    static final String KEY_FRAME_COUNT = "frame_count";
    private static final String KEY_FPS = "fps";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_FREEZE_SAVER = "freeze_saver";

    private final Context context;
    private final SharedPreferences preferences;

    public WallpaperPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = preferences(this.context);
    }

    public static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    public WallpaperConfig load() {
        synchronized (AssetStore.STORE_LOCK) {
            WallpaperConfig defaults = WallpaperConfig.builtInDefaults();
            int assetRevision = preferences.getInt(
                    KEY_ASSET_REVISION,
                    defaults.getAssetRevision()
            );
            boolean custom = preferences.getBoolean(KEY_SOURCE_CUSTOM, false)
                    && new AssetStore(context).hasImportedAsset(assetRevision);
            return new WallpaperConfig(
                    custom,
                    assetRevision,
                    preferences.getInt(KEY_COLUMNS, defaults.getColumns()),
                    preferences.getInt(KEY_ROWS, defaults.getRows()),
                    preferences.getInt(KEY_FRAME_COUNT, defaults.getFrameCount()),
                    preferences.getInt(KEY_FPS, defaults.getFps()),
                    preferences.getString(KEY_SCALE, defaults.getScaleMode()),
                    preferences.getInt(KEY_BACKGROUND, defaults.getBackgroundColor()),
                    preferences.getBoolean(KEY_FREEZE_SAVER, defaults.freezesInBatterySaver())
            );
        }
    }

    public int getColumns() {
        return load().getColumns();
    }

    public int getRows() {
        return load().getRows();
    }

    public int getFrameCount() {
        return load().getFrameCount();
    }

    public int getFps() {
        return load().getFps();
    }

    public String getScaleMode() {
        return load().getScaleMode();
    }

    public int getBackgroundColor() {
        return load().getBackgroundColor();
    }

    public boolean getFreezeInBatterySaver() {
        return load().freezesInBatterySaver();
    }

    /** Validates and commits user-editable settings. Asset source is owned by AssetStore. */
    public boolean save(
            int columns,
            int rows,
            int frameCount,
            int fps,
            String scaleMode,
            int backgroundColor,
            boolean freezeInBatterySaver
    ) {
        synchronized (AssetStore.STORE_LOCK) {
            validateGrid(columns, rows, frameCount);
            if (fps < 0 || fps > PowerPolicy.MAX_FPS) {
                throw new IllegalArgumentException("Frame rate must be between 0 and 24 FPS");
            }
            new AssetStore(context).validateSelectedGrid(columns, rows, frameCount);
            int previousSchema = preferences.getInt(KEY_SCHEMA, 0);
            int previousColumns = preferences.getInt(KEY_COLUMNS, DemoSpriteFactory.COLUMNS);
            int previousRows = preferences.getInt(KEY_ROWS, DemoSpriteFactory.ROWS);
            int previousFrames = preferences.getInt(
                    KEY_FRAME_COUNT,
                    DemoSpriteFactory.FRAME_COUNT
            );
            int previousFps = preferences.getInt(KEY_FPS, 8);
            String previousScale = preferences.getString(KEY_SCALE, "fill");
            int previousBackground = preferences.getInt(KEY_BACKGROUND, 0xFF070814);
            boolean previousFreeze = preferences.getBoolean(KEY_FREEZE_SAVER, true);
            WallpaperConfig sanitized = new WallpaperConfig(
                    preferences.getBoolean(KEY_SOURCE_CUSTOM, false),
                    preferences.getInt(KEY_ASSET_REVISION, 0),
                    columns,
                    rows,
                    frameCount,
                    fps,
                    scaleMode,
                    backgroundColor,
                    freezeInBatterySaver
            );
            boolean committed = preferences.edit()
                    .putInt(KEY_SCHEMA, 1)
                    .putInt(KEY_COLUMNS, sanitized.getColumns())
                    .putInt(KEY_ROWS, sanitized.getRows())
                    .putInt(KEY_FRAME_COUNT, sanitized.getFrameCount())
                    .putInt(KEY_FPS, sanitized.getFps())
                    .putString(KEY_SCALE, sanitized.getScaleMode())
                    .putInt(KEY_BACKGROUND, sanitized.getBackgroundColor())
                    .putBoolean(KEY_FREEZE_SAVER, sanitized.freezesInBatterySaver())
                    .commit();
            if (!committed) {
                // Restore the in-process map even if the underlying disk is currently unwritable.
                preferences.edit()
                        .putInt(KEY_SCHEMA, previousSchema)
                        .putInt(KEY_COLUMNS, previousColumns)
                        .putInt(KEY_ROWS, previousRows)
                        .putInt(KEY_FRAME_COUNT, previousFrames)
                        .putInt(KEY_FPS, previousFps)
                        .putString(KEY_SCALE, previousScale)
                        .putInt(KEY_BACKGROUND, previousBackground)
                        .putBoolean(KEY_FREEZE_SAVER, previousFreeze)
                        .apply();
            }
            return committed;
        }
    }

    private static void validateGrid(int columns, int rows, int frameCount) {
        if (columns < 1 || columns > WallpaperConfig.MAX_GRID_AXIS
                || rows < 1 || rows > WallpaperConfig.MAX_GRID_AXIS) {
            throw new IllegalArgumentException("Grid axes must be between 1 and 64");
        }
        long cells = (long) columns * rows;
        if (cells > WallpaperConfig.MAX_FRAMES) {
            throw new IllegalArgumentException("The sprite grid may contain at most 256 cells");
        }
        if (frameCount < 1 || frameCount > cells) {
            throw new IllegalArgumentException("Frame count must fit inside the sprite grid");
        }
    }
}
