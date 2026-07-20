package com.pixeldrift.wallpaper;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Draft and applied wallpaper state. Every read is sanitized before reaching a renderer. */
public final class WallpaperPreferences {
    static final String FILE_NAME = "pixel_drift_wallpaper";
    static final String KEY_SOURCE_CUSTOM = "source_custom";
    static final String KEY_ASSET_REVISION = "asset_revision";
    static final String KEY_ASSET_WIDTH = "asset_width";
    static final String KEY_ASSET_HEIGHT = "asset_height";
    static final String PREFIX_SYSTEM = "applied_system_";
    static final String PREFIX_LOCK = "applied_lock_";
    private static final String KEY_SCHEMA = "schema";
    private static final String KEY_APPLIED_SCHEMA = "applied_schema";
    private static final String KEY_APPLY_GENERATION = "apply_generation";
    private static final String KEY_LAST_APPLIED_TARGET = "last_applied_target";
    static final String KEY_COLUMNS = "columns";
    static final String KEY_ROWS = "rows";
    static final String KEY_FRAME_COUNT = "frame_count";
    private static final String KEY_FPS = "fps";
    private static final String KEY_SCALE = "scale";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_FREEZE_SAVER = "freeze_saver";
    private static final int CURRENT_SCHEMA = 2;

    private final Context context;
    private final SharedPreferences preferences;

    public WallpaperPreferences(Context context) {
        this.context = context.getApplicationContext();
        this.preferences = preferences(this.context);
        ensureAppliedSlots();
    }

    public static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE);
    }

    /** Returns the editable draft used only by the app and Android's preview engine. */
    public WallpaperConfig load() {
        synchronized (AssetStore.STORE_LOCK) {
            return loadConfig("");
        }
    }

    /** Returns the immutable applied slot for an active wallpaper engine. */
    WallpaperConfig loadApplied(int wallpaperFlags) {
        synchronized (AssetStore.STORE_LOCK) {
            if (preferences.getInt(KEY_APPLIED_SCHEMA, 0) < 1) {
                return loadConfig("");
            }
            int slot = WallpaperTargetPolicy.configSlotForEngine(
                    wallpaperFlags,
                    Build.VERSION.SDK_INT
            );
            return loadConfig(prefixForSlot(slot));
        }
    }

    /**
     * Copies the current draft to the actual target confirmed by Android's wallpaper picker.
     * Importing and saving never call this method.
     */
    boolean applyDraft(int requestedWhich) {
        synchronized (AssetStore.STORE_LOCK) {
            int which = WallpaperTargetPolicy.normalizeRequestedTarget(
                    requestedWhich,
                    Build.VERSION.SDK_INT
            );
            WallpaperConfig draft = loadConfig("");
            int draftWidth = preferences.getInt(KEY_ASSET_WIDTH, 0);
            int draftHeight = preferences.getInt(KEY_ASSET_HEIGHT, 0);
            WallpaperConfig previousSystem = loadConfig(PREFIX_SYSTEM);
            WallpaperConfig previousLock = loadConfig(PREFIX_LOCK);
            int previousSystemWidth = preferences.getInt(key(PREFIX_SYSTEM, KEY_ASSET_WIDTH), 0);
            int previousSystemHeight = preferences.getInt(key(PREFIX_SYSTEM, KEY_ASSET_HEIGHT), 0);
            int previousLockWidth = preferences.getInt(key(PREFIX_LOCK, KEY_ASSET_WIDTH), 0);
            int previousLockHeight = preferences.getInt(key(PREFIX_LOCK, KEY_ASSET_HEIGHT), 0);
            int previousGeneration = preferences.getInt(KEY_APPLY_GENERATION, 0);
            int previousTarget = preferences.getInt(KEY_LAST_APPLIED_TARGET, 0);
            int nextGeneration = previousGeneration == Integer.MAX_VALUE
                    ? 1
                    : previousGeneration + 1;

            SharedPreferences.Editor editor = preferences.edit()
                    .putInt(KEY_APPLIED_SCHEMA, 1)
                    .putInt(KEY_APPLY_GENERATION, nextGeneration)
                    .putInt(KEY_LAST_APPLIED_TARGET, which);
            if ((which & WallpaperTargetPolicy.SYSTEM) != 0) {
                putConfig(editor, PREFIX_SYSTEM, draft, draftWidth, draftHeight);
            }
            if ((which & WallpaperTargetPolicy.LOCK) != 0) {
                putConfig(editor, PREFIX_LOCK, draft, draftWidth, draftHeight);
            }
            if (editor.commit()) {
                new AssetStore(context).cleanupUnreferencedRevisions();
                return true;
            }

            // commit() can update the in-process map even when disk persistence fails.
            SharedPreferences.Editor restore = preferences.edit()
                    .putInt(KEY_APPLIED_SCHEMA, 1)
                    .putInt(KEY_APPLY_GENERATION, previousGeneration)
                    .putInt(KEY_LAST_APPLIED_TARGET, previousTarget);
            putConfig(
                    restore,
                    PREFIX_SYSTEM,
                    previousSystem,
                    previousSystemWidth,
                    previousSystemHeight
            );
            putConfig(restore, PREFIX_LOCK, previousLock, previousLockWidth, previousLockHeight);
            restore.apply();
            return false;
        }
    }

    boolean draftMatchesApplied(int target) {
        synchronized (AssetStore.STORE_LOCK) {
            WallpaperConfig draft = loadConfig("");
            int normalized = WallpaperTargetPolicy.normalizeRequestedTarget(
                    target,
                    Build.VERSION.SDK_INT
            );
            boolean matches = true;
            if ((normalized & WallpaperTargetPolicy.SYSTEM) != 0) {
                matches = draft.equals(loadConfig(PREFIX_SYSTEM));
            }
            if ((normalized & WallpaperTargetPolicy.LOCK) != 0) {
                matches = matches && draft.equals(loadConfig(PREFIX_LOCK));
            }
            return matches;
        }
    }

    int getApplyGeneration() {
        return preferences.getInt(KEY_APPLY_GENERATION, 0);
    }

    int getLastAppliedTarget() {
        return preferences.getInt(KEY_LAST_APPLIED_TARGET, 0);
    }

    static boolean affectsEngine(
            boolean preview,
            int wallpaperFlags,
            int sdkInt,
            String changedKey
    ) {
        if (changedKey == null) {
            return true;
        }
        if (KEY_APPLY_GENERATION.equals(changedKey)
                || KEY_LAST_APPLIED_TARGET.equals(changedKey)
                || KEY_APPLIED_SCHEMA.equals(changedKey)) {
            return false;
        }
        if (preview) {
            return !changedKey.startsWith(PREFIX_SYSTEM)
                    && !changedKey.startsWith(PREFIX_LOCK);
        }
        int slot = WallpaperTargetPolicy.configSlotForEngine(wallpaperFlags, sdkInt);
        return changedKey.startsWith(prefixForSlot(slot));
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

    /** Validates and commits the editable draft. Asset selection is owned by AssetStore. */
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
            WallpaperConfig previous = loadConfig("");
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
                    .putInt(KEY_SCHEMA, CURRENT_SCHEMA)
                    .putInt(KEY_COLUMNS, sanitized.getColumns())
                    .putInt(KEY_ROWS, sanitized.getRows())
                    .putInt(KEY_FRAME_COUNT, sanitized.getFrameCount())
                    .putInt(KEY_FPS, sanitized.getFps())
                    .putString(KEY_SCALE, sanitized.getScaleMode())
                    .putInt(KEY_BACKGROUND, sanitized.getBackgroundColor())
                    .putBoolean(KEY_FREEZE_SAVER, sanitized.freezesInBatterySaver())
                    .commit();
            if (!committed) {
                // Restore only draft settings; applied slots are never touched by Save.
                preferences.edit()
                        .putInt(KEY_SCHEMA, CURRENT_SCHEMA)
                        .putInt(KEY_COLUMNS, previous.getColumns())
                        .putInt(KEY_ROWS, previous.getRows())
                        .putInt(KEY_FRAME_COUNT, previous.getFrameCount())
                        .putInt(KEY_FPS, previous.getFps())
                        .putString(KEY_SCALE, previous.getScaleMode())
                        .putInt(KEY_BACKGROUND, previous.getBackgroundColor())
                        .putBoolean(KEY_FREEZE_SAVER, previous.freezesInBatterySaver())
                        .apply();
            }
            return committed;
        }
    }

    private void ensureAppliedSlots() {
        synchronized (AssetStore.STORE_LOCK) {
            if (preferences.getInt(KEY_APPLIED_SCHEMA, 0) >= 1) {
                return;
            }
            WallpaperConfig legacy = loadConfig("");
            int width = preferences.getInt(KEY_ASSET_WIDTH, 0);
            int height = preferences.getInt(KEY_ASSET_HEIGHT, 0);
            SharedPreferences.Editor editor = preferences.edit()
                    .putInt(KEY_SCHEMA, CURRENT_SCHEMA)
                    .putInt(KEY_APPLIED_SCHEMA, 1);
            putConfig(editor, PREFIX_SYSTEM, legacy, width, height);
            putConfig(editor, PREFIX_LOCK, legacy, width, height);
            editor.apply();
        }
    }

    private WallpaperConfig loadConfig(String prefix) {
        WallpaperConfig defaults = WallpaperConfig.builtInDefaults();
        int assetRevision = preferences.getInt(
                key(prefix, KEY_ASSET_REVISION),
                defaults.getAssetRevision()
        );
        boolean custom = preferences.getBoolean(key(prefix, KEY_SOURCE_CUSTOM), false)
                && new AssetStore(context).hasImportedAsset(assetRevision);
        return new WallpaperConfig(
                custom,
                assetRevision,
                preferences.getInt(key(prefix, KEY_COLUMNS), defaults.getColumns()),
                preferences.getInt(key(prefix, KEY_ROWS), defaults.getRows()),
                preferences.getInt(key(prefix, KEY_FRAME_COUNT), defaults.getFrameCount()),
                preferences.getInt(key(prefix, KEY_FPS), defaults.getFps()),
                preferences.getString(key(prefix, KEY_SCALE), defaults.getScaleMode()),
                preferences.getInt(
                        key(prefix, KEY_BACKGROUND),
                        defaults.getBackgroundColor()
                ),
                preferences.getBoolean(
                        key(prefix, KEY_FREEZE_SAVER),
                        defaults.freezesInBatterySaver()
                )
        );
    }

    private static void putConfig(
            SharedPreferences.Editor editor,
            String prefix,
            WallpaperConfig config,
            int width,
            int height
    ) {
        editor.putBoolean(key(prefix, KEY_SOURCE_CUSTOM), config.usesCustomAsset())
                .putInt(key(prefix, KEY_ASSET_REVISION), config.getAssetRevision())
                .putInt(key(prefix, KEY_ASSET_WIDTH), Math.max(0, width))
                .putInt(key(prefix, KEY_ASSET_HEIGHT), Math.max(0, height))
                .putInt(key(prefix, KEY_COLUMNS), config.getColumns())
                .putInt(key(prefix, KEY_ROWS), config.getRows())
                .putInt(key(prefix, KEY_FRAME_COUNT), config.getFrameCount())
                .putInt(key(prefix, KEY_FPS), config.getFps())
                .putString(key(prefix, KEY_SCALE), config.getScaleMode())
                .putInt(key(prefix, KEY_BACKGROUND), config.getBackgroundColor())
                .putBoolean(key(prefix, KEY_FREEZE_SAVER), config.freezesInBatterySaver());
    }

    private static String prefixForSlot(int slot) {
        return slot == WallpaperTargetPolicy.LOCK ? PREFIX_LOCK : PREFIX_SYSTEM;
    }

    private static String key(String prefix, String base) {
        return prefix + base;
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
