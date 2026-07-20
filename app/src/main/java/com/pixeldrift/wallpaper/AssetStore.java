package com.pixeldrift.wallpaper;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Owns revisioned private sprite sheets and switches bytes plus grid metadata together. */
public final class AssetStore {
    public static final long MAX_ENCODED_BYTES = 25L * 1024L * 1024L;
    public static final long MAX_DECODED_PIXELS = 4_194_304L;
    public static final long MAX_DECODED_BYTES = 16L * 1024L * 1024L;
    public static final int MAX_DIMENSION = 4_096;

    /** AtomicFile does not provide locking. All readers and writers in this process use this lock. */
    static final Object STORE_LOCK = new Object();

    private final Context context;
    private final File assetDirectory;

    public AssetStore(Context context) {
        this.context = context.getApplicationContext();
        this.assetDirectory = new File(this.context.getFilesDir(), "assets");
    }

    public boolean hasImportedAsset() {
        synchronized (STORE_LOCK) {
            SharedPreferences preferences = WallpaperPreferences.preferences(context);
            return hasImportedAsset(
                    preferences.getInt(WallpaperPreferences.KEY_ASSET_REVISION, 0)
            );
        }
    }

    boolean hasImportedAsset(int revision) {
        if (revision < 1) {
            return false;
        }
        synchronized (STORE_LOCK) {
            try (FileInputStream input = atomicFileFor(revision).openRead()) {
                return input.read() != -1;
            } catch (IOException error) {
                return false;
            }
        }
    }

    public boolean isCustomSelected() {
        synchronized (STORE_LOCK) {
            SharedPreferences preferences = WallpaperPreferences.preferences(context);
            int revision = preferences.getInt(WallpaperPreferences.KEY_ASSET_REVISION, 0);
            return preferences.getBoolean(WallpaperPreferences.KEY_SOURCE_CUSTOM, false)
                    && hasImportedAsset(revision);
        }
    }

    public String getSourceStatus() {
        synchronized (STORE_LOCK) {
            SharedPreferences preferences = WallpaperPreferences.preferences(context);
            boolean custom = preferences.getBoolean(
                    WallpaperPreferences.KEY_SOURCE_CUSTOM,
                    false
            );
            if (!custom) {
                return "Built-in 48×80 ember demo · 8 frames";
            }
            int width = preferences.getInt(WallpaperPreferences.KEY_ASSET_WIDTH, 0);
            int height = preferences.getInt(WallpaperPreferences.KEY_ASSET_HEIGHT, 0);
            if (width > 0 && height > 0) {
                return "Imported sprite sheet · " + width + "×" + height + " px";
            }
            return "Imported sprite sheet selected";
        }
    }

    /** Rejects settings that would cut partial cells from the selected imported revision. */
    public void validateSelectedGrid(int columns, int rows, int frameCount) {
        synchronized (STORE_LOCK) {
            SharedPreferences preferences = WallpaperPreferences.preferences(context);
            int revision = preferences.getInt(WallpaperPreferences.KEY_ASSET_REVISION, 0);
            boolean custom = preferences.getBoolean(WallpaperPreferences.KEY_SOURCE_CUSTOM, false)
                    && hasImportedAsset(revision);
            if (!custom) {
                return;
            }
            try {
                BitmapFactory.Options bounds = decodeImportedBounds(revision);
                AssetLoader.validateDimensions(bounds.outWidth, bounds.outHeight);
                WallpaperConfig candidate = new WallpaperConfig(
                        true,
                        revision,
                        columns,
                        rows,
                        frameCount,
                        0,
                        "fit",
                        0xFF000000,
                        true
                );
                AssetLoader.validateGrid(bounds.outWidth, bounds.outHeight, candidate);
            } catch (IOException error) {
                throw new IllegalArgumentException("The selected imported asset is unavailable", error);
            }
        }
    }

    /** Selects the built-in asset and its known grid in one durable preference transaction. */
    public void createDemoAsset(long operationToken) throws IOException {
        synchronized (STORE_LOCK) {
            AssetWorkQueue.throwIfCancelled(operationToken);
            SharedPreferences preferences = WallpaperPreferences.preferences(context);
            boolean previousCustom = preferences.getBoolean(
                    WallpaperPreferences.KEY_SOURCE_CUSTOM,
                    false
            );
            int previousRevision = preferences.getInt(
                    WallpaperPreferences.KEY_ASSET_REVISION,
                    0
            );
            int previousColumns = preferences.getInt(
                    WallpaperPreferences.KEY_COLUMNS,
                    DemoSpriteFactory.COLUMNS
            );
            int previousRows = preferences.getInt(
                    WallpaperPreferences.KEY_ROWS,
                    DemoSpriteFactory.ROWS
            );
            int previousFrames = preferences.getInt(
                    WallpaperPreferences.KEY_FRAME_COUNT,
                    DemoSpriteFactory.FRAME_COUNT
            );
            int previousWidth = preferences.getInt(WallpaperPreferences.KEY_ASSET_WIDTH, 0);
            int previousHeight = preferences.getInt(WallpaperPreferences.KEY_ASSET_HEIGHT, 0);
            boolean committed = preferences.edit()
                    .putBoolean(WallpaperPreferences.KEY_SOURCE_CUSTOM, false)
                    .putInt(WallpaperPreferences.KEY_COLUMNS, DemoSpriteFactory.COLUMNS)
                    .putInt(WallpaperPreferences.KEY_ROWS, DemoSpriteFactory.ROWS)
                    .putInt(WallpaperPreferences.KEY_FRAME_COUNT, DemoSpriteFactory.FRAME_COUNT)
                    .commit();
            if (!committed) {
                restoreAssetSelection(
                        preferences,
                        previousCustom,
                        previousRevision,
                        previousColumns,
                        previousRows,
                        previousFrames,
                        previousWidth,
                        previousHeight
                );
                throw new IOException("Could not select the built-in asset");
            }
            cleanupUnreferencedRevisions(previousRevision);
        }
    }

    /**
     * Installs a new revision, then atomically switches the source pointer and grid. The previous
     * revision remains selected if either the file write or preference commit fails.
     */
    void installValidated(
            File validatedTemporaryFile,
            GridSuggestion grid,
            int imageWidth,
            int imageHeight,
            long operationToken
    ) throws IOException {
        AssetWorkQueue.throwIfCancelled(operationToken);
        File directory = assetDirectory;
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Could not create private asset directory");
        }

        SharedPreferences preferences = WallpaperPreferences.preferences(context);
        int previousRevision;
        int nextRevision;
        synchronized (STORE_LOCK) {
            previousRevision = preferences.getInt(WallpaperPreferences.KEY_ASSET_REVISION, 0);
            nextRevision = previousRevision == Integer.MAX_VALUE ? 1 : previousRevision + 1;
        }

        // The new revision is immutable and not yet referenced, so the potentially 25 MiB copy
        // need not block status reads or settings validation for the selected revision.
        AtomicFile destination = atomicFileFor(nextRevision);
        FileOutputStream output = null;
        try (FileInputStream input = new FileInputStream(validatedTemporaryFile)) {
            output = destination.startWrite();
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) {
                AssetWorkQueue.throwIfCancelled(operationToken);
                output.write(buffer, 0, read);
            }
            destination.finishWrite(output);
            output = null;
        } catch (IOException error) {
            if (output != null) {
                destination.failWrite(output);
            }
            throw error;
        }

        synchronized (STORE_LOCK) {
            AssetWorkQueue.throwIfCancelled(operationToken);
            if (preferences.getInt(WallpaperPreferences.KEY_ASSET_REVISION, 0)
                    != previousRevision) {
                throw new IOException("The selected asset changed during import");
            }
            boolean previousCustom = preferences.getBoolean(
                    WallpaperPreferences.KEY_SOURCE_CUSTOM,
                    false
            );
            int previousColumns = preferences.getInt(
                    WallpaperPreferences.KEY_COLUMNS,
                    DemoSpriteFactory.COLUMNS
            );
            int previousRows = preferences.getInt(
                    WallpaperPreferences.KEY_ROWS,
                    DemoSpriteFactory.ROWS
            );
            int previousFrames = preferences.getInt(
                    WallpaperPreferences.KEY_FRAME_COUNT,
                    DemoSpriteFactory.FRAME_COUNT
            );
            int previousWidth = preferences.getInt(WallpaperPreferences.KEY_ASSET_WIDTH, 0);
            int previousHeight = preferences.getInt(WallpaperPreferences.KEY_ASSET_HEIGHT, 0);
            boolean committed = preferences.edit()
                    .putBoolean(WallpaperPreferences.KEY_SOURCE_CUSTOM, true)
                    .putInt(WallpaperPreferences.KEY_ASSET_REVISION, nextRevision)
                    .putInt(WallpaperPreferences.KEY_COLUMNS, grid.getColumns())
                    .putInt(WallpaperPreferences.KEY_ROWS, grid.getRows())
                    .putInt(WallpaperPreferences.KEY_FRAME_COUNT, grid.getFrameCount())
                    .putInt(WallpaperPreferences.KEY_ASSET_WIDTH, imageWidth)
                    .putInt(WallpaperPreferences.KEY_ASSET_HEIGHT, imageHeight)
                    .commit();
            if (!committed) {
                // commit() may update the in-process map before reporting disk failure. Keep both
                // revision files and restore the old tuple in memory; either durable tuple remains
                // valid after a restart.
                restoreAssetSelection(
                        preferences,
                        previousCustom,
                        previousRevision,
                        previousColumns,
                        previousRows,
                        previousFrames,
                        previousWidth,
                        previousHeight
                );
                throw new IOException("Could not activate the imported asset");
            }
            cleanupUnreferencedRevisions(nextRevision);
        }
    }

    BitmapFactory.Options decodeImportedBounds(int revision) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inScaled = false;
        FileInputStream input;
        synchronized (STORE_LOCK) {
            input = atomicFileFor(revision).openRead();
        }
        try (FileInputStream closeable = input) {
            BitmapFactory.decodeStream(closeable, null, options);
        }
        return options;
    }

    Bitmap decodeImportedBitmap(int revision, BitmapFactory.Options options) throws IOException {
        FileInputStream input;
        synchronized (STORE_LOCK) {
            input = atomicFileFor(revision).openRead();
        }
        try (FileInputStream closeable = input) {
            return BitmapFactory.decodeStream(closeable, null, options);
        }
    }

    private AtomicFile atomicFileFor(int revision) {
        if (revision < 1) {
            throw new IllegalArgumentException("Asset revision must be positive");
        }
        return new AtomicFile(new File(assetDirectory, "sprite_" + revision + ".bin"));
    }

    private void cleanupUnreferencedRevisions(int activeRevision) {
        File[] files = assetDirectory.listFiles();
        if (files == null) {
            return;
        }
        String activeName = "sprite_" + activeRevision + ".bin";
        for (File file : files) {
            String name = file.getName();
            String baseName = name;
            if (baseName.endsWith(".bak") || baseName.endsWith(".new")) {
                baseName = baseName.substring(0, baseName.length() - 4);
            }
            if (baseName.startsWith("sprite_")
                    && baseName.endsWith(".bin")
                    && !baseName.equals(activeName)) {
                new AtomicFile(new File(assetDirectory, baseName)).delete();
            }
        }
    }

    @SuppressLint("ApplySharedPref") // A failed activation needs a synchronous durability attempt.
    private static void restoreAssetSelection(
            SharedPreferences preferences,
            boolean custom,
            int revision,
            int columns,
            int rows,
            int frameCount,
            int width,
            int height
    ) {
        preferences.edit()
                .putBoolean(WallpaperPreferences.KEY_SOURCE_CUSTOM, custom)
                .putInt(WallpaperPreferences.KEY_ASSET_REVISION, revision)
                .putInt(WallpaperPreferences.KEY_COLUMNS, columns)
                .putInt(WallpaperPreferences.KEY_ROWS, rows)
                .putInt(WallpaperPreferences.KEY_FRAME_COUNT, frameCount)
                .putInt(WallpaperPreferences.KEY_ASSET_WIDTH, width)
                .putInt(WallpaperPreferences.KEY_ASSET_HEIGHT, height)
                .commit();
    }

    static BitmapFactory.Options decodeBounds(File file) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        options.inScaled = false;
        BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        return options;
    }
}
