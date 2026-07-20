package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class FrameTimelineTest {
    @Test
    public void clampsFpsToSupportedRange() {
        assertEquals(0, FrameTimeline.clampFps(-1));
        assertEquals(0, FrameTimeline.clampFps(0));
        assertEquals(12, FrameTimeline.clampFps(12));
        assertEquals(24, FrameTimeline.clampFps(240));
    }

    @Test
    public void zeroFpsFreezesFirstFrameWithoutDeadline() {
        FrameTimeline timeline = FrameTimeline.schedule(1_000L, 9_000L, 8, 0);

        assertEquals(0, timeline.getFrameIndex());
        assertEquals(Long.MAX_VALUE, timeline.getNextDeadlineMs());
        assertTrue(timeline.isFrozen());
    }

    @Test
    public void fractionalFramePeriodRemainsAnchoredWithoutDrift() {
        FrameTimeline beforeFirstDeadline = FrameTimeline.schedule(1_000L, 1_041L, 7, 24);
        FrameTimeline atFirstDeadline = FrameTimeline.schedule(1_000L, 1_042L, 7, 24);
        FrameTimeline afterOneThousandFrames = FrameTimeline.schedule(
                1_000L,
                42_666L,
                7,
                24
        );

        assertEquals(0, beforeFirstDeadline.getFrameIndex());
        assertEquals(1_042L, beforeFirstDeadline.getNextDeadlineMs());
        assertEquals(1, atFirstDeadline.getFrameIndex());
        assertEquals(1_084L, atFirstDeadline.getNextDeadlineMs());
        assertEquals(5, afterOneThousandFrames.getFrameIndex());
        assertEquals(42_667L, afterOneThousandFrames.getNextDeadlineMs());
    }

    @Test
    public void stallSkipsExpiredFramesAndDeadlines() {
        FrameTimeline timeline = FrameTimeline.schedule(1_000L, 2_550L, 4, 10);

        assertEquals(3, timeline.getFrameIndex());
        assertEquals(2_600L, timeline.getNextDeadlineMs());
        assertEquals(50L, timeline.delayFrom(2_550L));
    }

    @Test
    public void scheduleIntoReusesCallerOwnedTick() {
        FrameTimeline.Tick tick = new FrameTimeline.Tick();

        assertTrue(tick == FrameTimeline.scheduleInto(tick, 1_000L, 1_250L, 4, 8));
        assertEquals(2, tick.getFrameIndex());
        assertEquals(1_375L, tick.getNextDeadlineMs());

        assertTrue(tick == FrameTimeline.scheduleInto(tick, 1_000L, 9_000L, 4, 0));
        assertTrue(tick.isFrozen());
    }

    @Test
    public void timeBeforeEpochStartsAtFrameZero() {
        FrameTimeline timeline = FrameTimeline.schedule(5_000L, 4_000L, 4, 2);

        assertEquals(0, timeline.getFrameIndex());
        assertEquals(5_500L, timeline.getNextDeadlineMs());
    }
}
