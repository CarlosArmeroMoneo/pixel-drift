package com.pixeldrift.wallpaper;

/**
 * Immutable frame scheduling result anchored to a monotonic epoch.
 *
 * <p>The timeline derives every frame and deadline from {@code epochMs}; it never adds a rounded
 * frame duration to a previous deadline. That keeps fractional rates such as 24 FPS drift-free
 * and naturally skips frames after a renderer stall.</p>
 */
public final class FrameTimeline {
    public static final int MAX_FPS = 24;
    private static final long MILLIS_PER_SECOND = 1_000L;

    private final int frameIndex;
    private final long nextDeadlineMs;
    private final int fps;

    private FrameTimeline(int frameIndex, long nextDeadlineMs, int fps) {
        this.frameIndex = frameIndex;
        this.nextDeadlineMs = nextDeadlineMs;
        this.fps = fps;
    }

    /**
     * Calculates the frame visible at {@code nowMs} and the first anchored deadline after it.
     *
     * <p>Both time arguments must come from the same monotonic clock. A time before the epoch is
     * treated as the epoch itself. Invalid frame counts degrade to a single frame, while FPS is
     * clamped to the supported 0..24 range. Zero FPS freezes frame zero indefinitely.</p>
     */
    public static FrameTimeline schedule(
            long epochMs,
            long nowMs,
            int frameCount,
            int requestedFps
    ) {
        Tick tick = scheduleInto(new Tick(), epochMs, nowMs, frameCount, requestedFps);
        return new FrameTimeline(tick.frameIndex, tick.nextDeadlineMs, tick.fps);
    }

    /**
     * Allocation-free variant for the render loop. The caller owns and may reuse {@code output}.
     */
    public static Tick scheduleInto(
            Tick output,
            long epochMs,
            long nowMs,
            int frameCount,
            int requestedFps
    ) {
        if (output == null) {
            throw new NullPointerException("output");
        }
        int safeFps = clampFps(requestedFps);
        int safeFrameCount = Math.max(1, frameCount);
        if (safeFps == 0) {
            output.set(0, Long.MAX_VALUE, 0);
            return output;
        }

        long elapsedMs = nonNegativeDifference(nowMs, epochMs);
        long elapsedSeconds = elapsedMs / MILLIS_PER_SECOND;
        long remainingMs = elapsedMs % MILLIS_PER_SECOND;

        // This decomposition avoids overflowing elapsedMs * fps.
        long completedFrames = elapsedSeconds * safeFps
                + (remainingMs * safeFps) / MILLIS_PER_SECOND;
        int frameIndex = frameModulo(
                elapsedSeconds,
                remainingMs,
                safeFps,
                safeFrameCount
        );

        long nextOffsetMs = deadlineOffsetMs(completedFrames + 1L, safeFps);
        long nextDeadlineMs = saturatedAdd(epochMs, nextOffsetMs);
        output.set(frameIndex, nextDeadlineMs, safeFps);
        return output;
    }

    public static int clampFps(int fps) {
        return Math.max(0, Math.min(MAX_FPS, fps));
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public long getNextDeadlineMs() {
        return nextDeadlineMs;
    }

    public int getFps() {
        return fps;
    }

    public boolean isFrozen() {
        return fps == 0;
    }

    /** Returns a non-negative delay suitable for a monotonic-clock scheduler. */
    public long delayFrom(long nowMs) {
        if (nextDeadlineMs == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return Math.max(0L, nonNegativeDifference(nextDeadlineMs, nowMs));
    }

    private static int frameModulo(
            long elapsedSeconds,
            long remainingMs,
            int fps,
            int frameCount
    ) {
        long wholeSecondsModulo = elapsedSeconds % frameCount;
        long completeSubsecondFrames = (remainingMs * fps) / MILLIS_PER_SECOND;
        return (int) ((wholeSecondsModulo * fps + completeSubsecondFrames) % frameCount);
    }

    /** Returns ceil(frameNumber * 1000 / fps) without multiplying a large frame number by 1000. */
    private static long deadlineOffsetMs(long frameNumber, int fps) {
        long wholeSeconds = frameNumber / fps;
        long remainder = frameNumber % fps;
        long wholeMillis = saturatedMultiply(wholeSeconds, MILLIS_PER_SECOND);
        long fractionalMillis = (remainder * MILLIS_PER_SECOND + fps - 1L) / fps;
        return saturatedAdd(wholeMillis, fractionalMillis);
    }

    private static long nonNegativeDifference(long later, long earlier) {
        if (later <= earlier) {
            return 0L;
        }
        long difference = later - earlier;
        return difference < 0L ? Long.MAX_VALUE : difference;
    }

    private static long saturatedMultiply(long value, long multiplier) {
        if (value > Long.MAX_VALUE / multiplier) {
            return Long.MAX_VALUE;
        }
        return value * multiplier;
    }

    private static long saturatedAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        if (right < 0L && left < Long.MIN_VALUE - right) {
            return Long.MIN_VALUE;
        }
        return left + right;
    }

    /** Reusable frame scheduling result for allocation-sensitive rendering paths. */
    public static final class Tick {
        private int frameIndex;
        private long nextDeadlineMs;
        private int fps;

        public Tick() {
        }

        public int getFrameIndex() {
            return frameIndex;
        }

        public long getNextDeadlineMs() {
            return nextDeadlineMs;
        }

        public int getFps() {
            return fps;
        }

        public boolean isFrozen() {
            return fps == 0;
        }

        public long delayFrom(long nowMs) {
            if (nextDeadlineMs == Long.MAX_VALUE) {
                return Long.MAX_VALUE;
            }
            return Math.max(0L, nonNegativeDifference(nextDeadlineMs, nowMs));
        }

        private void set(int frameIndex, long nextDeadlineMs, int fps) {
            this.frameIndex = frameIndex;
            this.nextDeadlineMs = nextDeadlineMs;
            this.fps = fps;
        }
    }
}
