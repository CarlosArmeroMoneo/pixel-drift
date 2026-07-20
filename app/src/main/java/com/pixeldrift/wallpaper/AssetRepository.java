package com.pixeldrift.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/** Service-scoped, single-flight bitmap cache shared by active and preview engines. */
final class AssetRepository {
    private final List<Entry> entries = new ArrayList<>();

    synchronized Lease acquire(Context context, WallpaperConfig config) {
        Entry entry = find(config);
        boolean created = false;
        if (entry == null) {
            Bitmap bitmap = AssetLoader.decodeBitmap(context, config);
            try {
                entry = new Entry(config.usesCustomAsset(), config.getAssetRevision(), bitmap);
            } catch (RuntimeException | OutOfMemoryError error) {
                bitmap.recycle();
                throw error;
            }
            created = true;
        }

        boolean added = false;
        boolean retained = false;
        try {
            AssetLoader.validateGrid(entry.bitmap.getWidth(), entry.bitmap.getHeight(), config);
            SpriteAsset asset = new SpriteAsset(
                    entry.bitmap,
                    config.getColumns(),
                    config.getRows(),
                    config.getFrameCount()
            );
            if (created) {
                entries.add(entry);
                added = true;
            }
            entry.references++;
            retained = true;
            return new Lease(this, entry, asset);
        } catch (RuntimeException | OutOfMemoryError error) {
            if (retained) {
                entry.references--;
            }
            if (added) {
                entries.remove(entry);
            }
            if (created && !entry.bitmap.isRecycled()) {
                entry.bitmap.recycle();
            }
            throw error;
        }
    }

    private Entry find(WallpaperConfig config) {
        for (Entry entry : entries) {
            if (entry.custom == config.usesCustomAsset()
                    && (!entry.custom || entry.revision == config.getAssetRevision())) {
                return entry;
            }
        }
        return null;
    }

    private synchronized void release(Lease lease) {
        if (lease.released) {
            return;
        }
        lease.released = true;
        Entry entry = lease.entry;
        entry.references--;
        if (entry.references == 0) {
            entries.remove(entry);
            if (!entry.bitmap.isRecycled()) {
                entry.bitmap.recycle();
            }
        }
    }

    static final class Lease {
        private final AssetRepository repository;
        private final Entry entry;
        private final SpriteAsset asset;
        private boolean released;

        private Lease(AssetRepository repository, Entry entry, SpriteAsset asset) {
            this.repository = repository;
            this.entry = entry;
            this.asset = asset;
        }

        SpriteAsset getAsset() {
            return asset;
        }

        void release() {
            repository.release(this);
        }
    }

    private static final class Entry {
        private final boolean custom;
        private final int revision;
        private final Bitmap bitmap;
        private int references;

        private Entry(boolean custom, int revision, Bitmap bitmap) {
            this.custom = custom;
            this.revision = revision;
            this.bitmap = bitmap;
        }
    }
}
