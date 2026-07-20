package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WallpaperTargetPolicyTest {
    @Test
    public void independentTargetsRequireAndroid14() {
        assertFalse(WallpaperTargetPolicy.supportsIndependentTargets(33));
        assertTrue(WallpaperTargetPolicy.supportsIndependentTargets(34));
    }

    @Test
    public void oldAndroidNormalizesEveryRequestToSharedWallpaper() {
        assertEquals(
                WallpaperTargetPolicy.BOTH,
                WallpaperTargetPolicy.normalizeRequestedTarget(WallpaperTargetPolicy.LOCK, 33)
        );
    }

    @Test
    public void android14PreservesExplicitTargets() {
        assertEquals(
                WallpaperTargetPolicy.SYSTEM,
                WallpaperTargetPolicy.normalizeRequestedTarget(WallpaperTargetPolicy.SYSTEM, 34)
        );
        assertEquals(
                WallpaperTargetPolicy.LOCK,
                WallpaperTargetPolicy.normalizeRequestedTarget(WallpaperTargetPolicy.LOCK, 34)
        );
        assertEquals(
                WallpaperTargetPolicy.BOTH,
                WallpaperTargetPolicy.normalizeRequestedTarget(WallpaperTargetPolicy.BOTH, 34)
        );
    }

    @Test
    public void lockOnlyEngineUsesLockSlot() {
        assertEquals(
                WallpaperTargetPolicy.LOCK,
                WallpaperTargetPolicy.configSlotForEngine(WallpaperTargetPolicy.LOCK, 34)
        );
        assertEquals(
                WallpaperTargetPolicy.SYSTEM,
                WallpaperTargetPolicy.configSlotForEngine(WallpaperTargetPolicy.BOTH, 34)
        );
        assertEquals(
                WallpaperTargetPolicy.SYSTEM,
                WallpaperTargetPolicy.configSlotForEngine(WallpaperTargetPolicy.LOCK, 33)
        );
    }
}
