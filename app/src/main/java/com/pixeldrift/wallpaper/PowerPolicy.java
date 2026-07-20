package com.pixeldrift.wallpaper;

/** Pure power-state decisions shared by the wallpaper engine and unit tests. */
public final class PowerPolicy {
    public static final int MAX_FPS = FrameTimeline.MAX_FPS;

    private PowerPolicy() {
    }

    /** Rendering is allowed only while every lifecycle gate is open. */
    public static boolean shouldRender(
            boolean visible,
            boolean surfaceReady,
            boolean interactive,
            boolean destroyed
    ) {
        return visible && surfaceReady && interactive && !destroyed;
    }

    /**
     * Applies the global FPS cap and optionally freezes animation while battery saver is active.
     */
    public static int effectiveFps(
            int userFps,
            boolean batterySaver,
            boolean freezeInBatterySaver,
            int frameCount
    ) {
        if (frameCount <= 1 || (batterySaver && freezeInBatterySaver)) {
            return 0;
        }
        return FrameTimeline.clampFps(userFps);
    }
}
