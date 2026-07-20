package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class ScaleGeometryTest {
    @Test
    public void fitLetterboxesInsideTarget() {
        ScaleGeometry.Rect rect = ScaleGeometry.calculate(
                ScaleGeometry.Mode.FIT,
                200,
                100,
                100,
                100
        );

        assertEquals(new ScaleGeometry.Rect(0, 25, 100, 75), rect);
    }

    @Test
    public void fillCropsOutsideTarget() {
        ScaleGeometry.Rect rect = ScaleGeometry.calculate(
                ScaleGeometry.Mode.FILL,
                200,
                100,
                100,
                100
        );

        assertEquals(new ScaleGeometry.Rect(-50, 0, 150, 100), rect);
    }

    @Test
    public void stretchUsesExactTargetBounds() {
        ScaleGeometry.Rect rect = ScaleGeometry.calculate(
                ScaleGeometry.Mode.STRETCH,
                3,
                7,
                101,
                51
        );

        assertEquals(new ScaleGeometry.Rect(0, 0, 101, 51), rect);
        assertEquals(101, rect.getWidth());
        assertEquals(51, rect.getHeight());
    }

    @Test
    public void fillRoundsOutwardSoTargetIsCovered() {
        ScaleGeometry.Rect rect = ScaleGeometry.calculate(
                ScaleGeometry.Mode.FILL,
                4,
                3,
                6,
                6
        );

        assertEquals(new ScaleGeometry.Rect(-1, 0, 7, 6), rect);
    }

    @Test
    public void fillCentersNegativeOddOverflowWithFloorSemantics() {
        ScaleGeometry.Rect rect = ScaleGeometry.calculate(
                ScaleGeometry.Mode.FILL,
                5,
                3,
                4,
                4
        );

        assertEquals(new ScaleGeometry.Rect(-2, 0, 5, 4), rect);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsZeroDimensions() {
        ScaleGeometry.calculate(ScaleGeometry.Mode.FIT, 0, 10, 100, 100);
    }
}
