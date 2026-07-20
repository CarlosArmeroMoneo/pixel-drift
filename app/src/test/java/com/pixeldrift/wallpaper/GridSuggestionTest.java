package com.pixeldrift.wallpaper;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class GridSuggestionTest {
    @Test
    public void firstCustomImportDoesNotInheritTheDemoGrid() {
        GridSuggestion suggestion = GridSuggestion.suggestForImport(
                1024,
                1024,
                4,
                2,
                8,
                false
        );

        assertEquals(new GridSuggestion(1, 1, 1), suggestion);
    }

    @Test
    public void explicitlyEnteredGridIsRespectedForFirstSquareImport() {
        GridSuggestion suggestion = GridSuggestion.suggestForImport(
                1024,
                1024,
                4,
                4,
                16,
                true
        );

        assertEquals(new GridSuggestion(4, 4, 16), suggestion);
    }

    @Test
    public void infersHorizontalSquareFrameStrip() {
        GridSuggestion suggestion = GridSuggestion.suggest(320, 64, 2, 2, 3);

        assertEquals(5, suggestion.getColumns());
        assertEquals(1, suggestion.getRows());
        assertEquals(5, suggestion.getFrameCount());
        assertEquals(64, suggestion.frameWidth(320));
    }

    @Test
    public void infersVerticalSquareFrameStrip() {
        GridSuggestion suggestion = GridSuggestion.suggest(48, 192, 4, 1, 4);

        assertEquals(1, suggestion.getColumns());
        assertEquals(4, suggestion.getRows());
        assertEquals(4, suggestion.getFrameCount());
        assertEquals(48, suggestion.frameHeight(192));
    }

    @Test
    public void preservesValidCurrentGridForNonStripImage() {
        GridSuggestion suggestion = GridSuggestion.suggest(300, 200, 3, 2, 5);

        assertEquals(new GridSuggestion(3, 2, 5), suggestion);
    }

    @Test
    public void invalidCurrentGridFallsBackToSingleFrame() {
        GridSuggestion nonDivisible = GridSuggestion.suggest(301, 200, 3, 2, 5);
        GridSuggestion tooManyCells = GridSuggestion.suggest(300, 200, 20, 20, 4);
        GridSuggestion tooManyFrames = GridSuggestion.suggest(300, 200, 3, 2, 7);

        assertEquals(new GridSuggestion(1, 1), nonDivisible);
        assertEquals(new GridSuggestion(1, 1), tooManyCells);
        assertEquals(new GridSuggestion(1, 1), tooManyFrames);
    }

    @Test
    public void squareImageDefaultsToOneByOne() {
        assertEquals(
                new GridSuggestion(1, 1),
                GridSuggestion.suggest(128, 128, 0, 0, 0)
        );
    }

    @Test
    public void oversizedStripUsesValidCurrentGridOrSingleFrame() {
        GridSuggestion preserved = GridSuggestion.suggest(6_500, 100, 5, 1, 5);
        GridSuggestion fallback = GridSuggestion.suggest(6_500, 100, 65, 1, 65);

        assertEquals(new GridSuggestion(5, 1, 5), preserved);
        assertEquals(new GridSuggestion(1, 1), fallback);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsOversizedAxis() {
        new GridSuggestion(65, 1, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void constructorRejectsMoreThanMaximumCells() {
        new GridSuggestion(32, 9, 1);
    }
}
