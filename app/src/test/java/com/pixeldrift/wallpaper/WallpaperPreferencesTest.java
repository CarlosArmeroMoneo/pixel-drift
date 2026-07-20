package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WallpaperPreferencesTest {
    @Test
    public void activeHomeEngineIgnoresDraftAndLockChanges() {
        assertFalse(WallpaperPreferences.affectsEngine(
                false,
                WallpaperTargetPolicy.SYSTEM,
                34,
                WallpaperPreferences.KEY_ASSET_REVISION
        ));
        assertFalse(WallpaperPreferences.affectsEngine(
                false,
                WallpaperTargetPolicy.SYSTEM,
                34,
                WallpaperPreferences.PREFIX_LOCK + WallpaperPreferences.KEY_ASSET_REVISION
        ));
        assertTrue(WallpaperPreferences.affectsEngine(
                false,
                WallpaperTargetPolicy.SYSTEM,
                34,
                WallpaperPreferences.PREFIX_SYSTEM + WallpaperPreferences.KEY_ASSET_REVISION
        ));
    }

    @Test
    public void activeLockEngineIgnoresDraftAndHomeChanges() {
        assertFalse(WallpaperPreferences.affectsEngine(
                false,
                WallpaperTargetPolicy.LOCK,
                34,
                WallpaperPreferences.KEY_COLUMNS
        ));
        assertFalse(WallpaperPreferences.affectsEngine(
                false,
                WallpaperTargetPolicy.LOCK,
                34,
                WallpaperPreferences.PREFIX_SYSTEM + WallpaperPreferences.KEY_COLUMNS
        ));
        assertTrue(WallpaperPreferences.affectsEngine(
                false,
                WallpaperTargetPolicy.LOCK,
                34,
                WallpaperPreferences.PREFIX_LOCK + WallpaperPreferences.KEY_COLUMNS
        ));
    }

    @Test
    public void previewEngineIgnoresAppliedChanges() {
        assertTrue(WallpaperPreferences.affectsEngine(
                true,
                WallpaperTargetPolicy.SYSTEM,
                34,
                WallpaperPreferences.KEY_FRAME_COUNT
        ));
        assertFalse(WallpaperPreferences.affectsEngine(
                true,
                WallpaperTargetPolicy.SYSTEM,
                34,
                WallpaperPreferences.PREFIX_SYSTEM + WallpaperPreferences.KEY_FRAME_COUNT
        ));
    }
}
