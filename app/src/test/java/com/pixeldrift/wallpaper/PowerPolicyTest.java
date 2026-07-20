package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class PowerPolicyTest {
    @Test
    public void renderingRequiresEveryLifecycleGate() {
        assertTrue(PowerPolicy.shouldRender(true, true, true, false));
        assertFalse(PowerPolicy.shouldRender(false, true, true, false));
        assertFalse(PowerPolicy.shouldRender(true, false, true, false));
        assertFalse(PowerPolicy.shouldRender(true, true, false, false));
        assertFalse(PowerPolicy.shouldRender(true, true, true, true));
    }

    @Test
    public void batterySaverFreezesOnlyWhenConfigured() {
        assertEquals(0, PowerPolicy.effectiveFps(12, true, true, 8));
        assertEquals(12, PowerPolicy.effectiveFps(12, true, false, 8));
        assertEquals(12, PowerPolicy.effectiveFps(12, false, true, 8));
    }

    @Test
    public void effectiveFpsAlwaysRespectsCap() {
        assertEquals(0, PowerPolicy.effectiveFps(-4, false, false, 8));
        assertEquals(PowerPolicy.MAX_FPS, PowerPolicy.effectiveFps(60, false, false, 8));
    }

    @Test
    public void singleFrameAssetsNeverScheduleDuplicateDraws() {
        assertEquals(0, PowerPolicy.effectiveFps(24, false, false, 1));
        assertEquals(0, PowerPolicy.effectiveFps(24, false, false, 0));
    }
}
