package com.pixeldrift.wallpaper;

/** Pure target-selection rules shared by the UI, persistence layer, and wallpaper engines. */
final class WallpaperTargetPolicy {
    static final int SYSTEM = 1;
    static final int LOCK = 2;
    static final int BOTH = SYSTEM | LOCK;
    static final int INDEPENDENT_TARGETS_API = 34;

    private WallpaperTargetPolicy() {
    }

    static boolean supportsIndependentTargets(int sdkInt) {
        return sdkInt >= INDEPENDENT_TARGETS_API;
    }

    static int normalizeRequestedTarget(int requested, int sdkInt) {
        if (!supportsIndependentTargets(sdkInt)) {
            return BOTH;
        }
        int sanitized = requested & BOTH;
        return sanitized == 0 ? SYSTEM : sanitized;
    }

    static int configSlotForEngine(int wallpaperFlags, int sdkInt) {
        if (supportsIndependentTargets(sdkInt)
                && (wallpaperFlags & LOCK) != 0
                && (wallpaperFlags & SYSTEM) == 0) {
            return LOCK;
        }
        return SYSTEM;
    }
}
