package com.pixeldrift.wallpaper;

import java.util.Locale;
import java.util.Objects;

/** Aspect-ratio geometry calculated without Android framework dependencies. */
public final class ScaleGeometry {
    public enum Mode {
        FIT,
        FILL,
        STRETCH;

        public static Mode fromString(String value) {
            if (value == null) {
                return FILL;
            }
            try {
                return valueOf(value.trim().toUpperCase(Locale.US));
            } catch (IllegalArgumentException ignored) {
                return FILL;
            }
        }
    }

    private ScaleGeometry() {
    }

    /**
     * Returns target-space bounds. FIT stays wholly inside the target, FILL covers it and may
     * extend outside it, and STRETCH uses the target bounds exactly.
     */
    public static Rect calculate(
            Mode mode,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        Objects.requireNonNull(mode, "mode");
        requirePositive("sourceWidth", sourceWidth);
        requirePositive("sourceHeight", sourceHeight);
        requirePositive("targetWidth", targetWidth);
        requirePositive("targetHeight", targetHeight);

        if (mode == Mode.STRETCH) {
            return new Rect(0, 0, targetWidth, targetHeight);
        }

        long sourceByTargetHeight = (long) sourceWidth * targetHeight;
        long targetBySourceHeight = (long) targetWidth * sourceHeight;
        boolean sourceIsAtLeastAsWide = sourceByTargetHeight >= targetBySourceHeight;

        int scaledWidth;
        int scaledHeight;
        if (mode == Mode.FIT) {
            if (sourceIsAtLeastAsWide) {
                scaledWidth = targetWidth;
                scaledHeight = atLeastOne(
                        ((long) sourceHeight * targetWidth) / sourceWidth
                );
            } else {
                scaledHeight = targetHeight;
                scaledWidth = atLeastOne(
                        ((long) sourceWidth * targetHeight) / sourceHeight
                );
            }
        } else {
            if (sourceIsAtLeastAsWide) {
                scaledHeight = targetHeight;
                scaledWidth = checkedInt(ceilDivide(
                        (long) sourceWidth * targetHeight,
                        sourceHeight
                ));
            } else {
                scaledWidth = targetWidth;
                scaledHeight = checkedInt(ceilDivide(
                        (long) sourceHeight * targetWidth,
                        sourceWidth
                ));
            }
        }

        int left = floorHalf((long) targetWidth - scaledWidth);
        int top = floorHalf((long) targetHeight - scaledHeight);
        return new Rect(left, top, left + scaledWidth, top + scaledHeight);
    }

    public static Rect calculate(
            String mode,
            int sourceWidth,
            int sourceHeight,
            int targetWidth,
            int targetHeight
    ) {
        return calculate(
                Mode.fromString(mode),
                sourceWidth,
                sourceHeight,
                targetWidth,
                targetHeight
        );
    }

    private static int atLeastOne(long value) {
        return (int) Math.max(1L, value);
    }

    private static long ceilDivide(long numerator, long denominator) {
        return numerator / denominator + (numerator % denominator == 0L ? 0L : 1L);
    }

    private static int checkedInt(long value) {
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) {
            throw new ArithmeticException("Scaled geometry exceeds integer coordinates");
        }
        return (int) value;
    }

    /** API-23-safe floor division by two, including negative odd coordinates. */
    private static int floorHalf(long value) {
        long quotient = value / 2L;
        if (value < 0L && value % 2L != 0L) {
            quotient--;
        }
        return checkedInt(quotient);
    }

    private static void requirePositive(String name, int value) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    /** Immutable Android-Rect-compatible bounds with exclusive right and bottom edges. */
    public static final class Rect {
        private final int left;
        private final int top;
        private final int right;
        private final int bottom;

        public Rect(int left, int top, int right, int bottom) {
            if (right < left || bottom < top) {
                throw new IllegalArgumentException("Rect edges are inverted");
            }
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public int getLeft() {
            return left;
        }

        public int getTop() {
            return top;
        }

        public int getRight() {
            return right;
        }

        public int getBottom() {
            return bottom;
        }

        public int width() {
            return right - left;
        }

        public int height() {
            return bottom - top;
        }

        public int getWidth() {
            return width();
        }

        public int getHeight() {
            return height();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) {
                return true;
            }
            if (!(object instanceof Rect)) {
                return false;
            }
            Rect rect = (Rect) object;
            return left == rect.left
                    && top == rect.top
                    && right == rect.right
                    && bottom == rect.bottom;
        }

        @Override
        public int hashCode() {
            return Objects.hash(left, top, right, bottom);
        }

        @Override
        public String toString() {
            return "Rect{" + left + ',' + top + ',' + right + ',' + bottom + '}';
        }
    }
}
