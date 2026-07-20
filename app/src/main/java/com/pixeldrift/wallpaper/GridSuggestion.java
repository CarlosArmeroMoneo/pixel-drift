package com.pixeldrift.wallpaper;

import java.util.Objects;

/** Immutable, validated sprite-sheet grid suggestion. */
public final class GridSuggestion {
    public static final int MAX_GRID_AXIS = 64;
    public static final int MAX_CELLS = 256;
    private static final GridSuggestion SINGLE_FRAME = new GridSuggestion(1, 1, 1);

    private final int columns;
    private final int rows;
    private final int frameCount;

    public GridSuggestion(int columns, int rows) {
        this(columns, rows, checkedCellCount(columns, rows));
    }

    public GridSuggestion(int columns, int rows, int frameCount) {
        int cells = checkedCellCount(columns, rows);
        if (frameCount < 1 || frameCount > cells) {
            throw new IllegalArgumentException("frameCount must be between 1 and grid cell count");
        }
        this.columns = columns;
        this.rows = rows;
        this.frameCount = frameCount;
    }

    /**
     * Infers unambiguous horizontal or vertical square-frame strips. For other image shapes, a
     * valid existing grid is retained; otherwise the safe single-frame layout is returned.
     */
    public static GridSuggestion suggest(
            int imageWidth,
            int imageHeight,
            int currentColumns,
            int currentRows,
            int currentFrameCount
    ) {
        requirePositiveDimension("imageWidth", imageWidth);
        requirePositiveDimension("imageHeight", imageHeight);

        if (imageWidth > imageHeight && imageWidth % imageHeight == 0) {
            int columns = imageWidth / imageHeight;
            if (isWithinGridLimits(columns, 1)) {
                return new GridSuggestion(columns, 1, columns);
            }
        }
        if (imageHeight > imageWidth && imageHeight % imageWidth == 0) {
            int rows = imageHeight / imageWidth;
            if (isWithinGridLimits(1, rows)) {
                return new GridSuggestion(1, rows, rows);
            }
        }

        if (isValidExistingGrid(
                imageWidth,
                imageHeight,
                currentColumns,
                currentRows,
                currentFrameCount
        )) {
            return new GridSuggestion(currentColumns, currentRows, currentFrameCount);
        }
        return SINGLE_FRAME;
    }

    public static GridSuggestion suggest(int imageWidth, int imageHeight) {
        return suggest(imageWidth, imageHeight, 1, 1, 1);
    }

    /** Existing layout metadata is relevant only when replacing an already-custom source. */
    public static GridSuggestion suggestForImport(
            int imageWidth,
            int imageHeight,
            int currentColumns,
            int currentRows,
            int currentFrameCount,
            boolean preserveExistingGrid
    ) {
        return suggest(
                imageWidth,
                imageHeight,
                preserveExistingGrid ? currentColumns : 1,
                preserveExistingGrid ? currentRows : 1,
                preserveExistingGrid ? currentFrameCount : 1
        );
    }

    public int getColumns() {
        return columns;
    }

    public int getRows() {
        return rows;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public int frameWidth(int imageWidth) {
        if (imageWidth <= 0 || imageWidth % columns != 0) {
            throw new IllegalArgumentException("imageWidth must divide evenly by columns");
        }
        return imageWidth / columns;
    }

    public int frameHeight(int imageHeight) {
        if (imageHeight <= 0 || imageHeight % rows != 0) {
            throw new IllegalArgumentException("imageHeight must divide evenly by rows");
        }
        return imageHeight / rows;
    }

    private static boolean isValidExistingGrid(
            int imageWidth,
            int imageHeight,
            int columns,
            int rows,
            int frameCount
    ) {
        if (columns < 1 || rows < 1 || frameCount < 1) {
            return false;
        }
        long cells = (long) columns * rows;
        return isWithinGridLimits(columns, rows)
                && frameCount <= cells
                && imageWidth % columns == 0
                && imageHeight % rows == 0;
    }

    private static boolean isWithinGridLimits(int columns, int rows) {
        return columns > 0
                && rows > 0
                && columns <= MAX_GRID_AXIS
                && rows <= MAX_GRID_AXIS
                && (long) columns * rows <= MAX_CELLS;
    }

    private static int checkedCellCount(int columns, int rows) {
        if (columns < 1 || rows < 1
                || columns > MAX_GRID_AXIS || rows > MAX_GRID_AXIS) {
            throw new IllegalArgumentException("columns and rows must be between 1 and 64");
        }
        long cells = (long) columns * rows;
        if (cells > MAX_CELLS) {
            throw new IllegalArgumentException("grid may contain at most 256 cells");
        }
        return (int) cells;
    }

    private static void requirePositiveDimension(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof GridSuggestion)) {
            return false;
        }
        GridSuggestion that = (GridSuggestion) object;
        return columns == that.columns && rows == that.rows && frameCount == that.frameCount;
    }

    @Override
    public int hashCode() {
        return Objects.hash(columns, rows, frameCount);
    }

    @Override
    public String toString() {
        return "GridSuggestion{" + columns + "x" + rows + ", frames=" + frameCount + '}';
    }
}
