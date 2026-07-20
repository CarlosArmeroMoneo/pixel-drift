package com.pixeldrift.wallpaper;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

/** Generates a tiny eight-frame ember scene at decode time; no bundled animation decoder needed. */
final class DemoSpriteFactory {
    static final int FRAME_WIDTH = 48;
    static final int FRAME_HEIGHT = 80;
    static final int COLUMNS = 4;
    static final int ROWS = 2;
    static final int FRAME_COUNT = 8;

    private static final int[] FLAME_HEIGHT = {9, 12, 10, 13, 11, 14, 10, 12};
    private static final int[] FLAME_BEND = {-1, 0, 1, 0, -1, 1, 0, -1};

    private DemoSpriteFactory() {
    }

    static Bitmap create() {
        Bitmap bitmap = Bitmap.createBitmap(
                FRAME_WIDTH * COLUMNS,
                FRAME_HEIGHT * ROWS,
                Bitmap.Config.ARGB_8888
        );
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setFilterBitmap(false);
        paint.setDither(false);

        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            int originX = (frame % COLUMNS) * FRAME_WIDTH;
            int originY = (frame / COLUMNS) * FRAME_HEIGHT;
            drawScene(canvas, paint, originX, originY, frame);
        }
        return bitmap;
    }

    private static void drawScene(Canvas canvas, Paint paint, int x, int y, int frame) {
        fill(canvas, paint, Color.rgb(7, 8, 20), x, y, x + FRAME_WIDTH, y + FRAME_HEIGHT);

        // Cold horizon and stepped ruins.
        fill(canvas, paint, Color.rgb(14, 18, 36), x, y + 38, x + 48, y + 66);
        fill(canvas, paint, Color.rgb(18, 22, 41), x + 2, y + 34, x + 9, y + 66);
        fill(canvas, paint, Color.rgb(18, 22, 41), x + 5, y + 29, x + 7, y + 34);
        fill(canvas, paint, Color.rgb(18, 22, 41), x + 38, y + 42, x + 46, y + 66);
        fill(canvas, paint, Color.rgb(18, 22, 41), x + 41, y + 37, x + 44, y + 42);

        // Sparse fixed stars and a small moon.
        pixel(canvas, paint, Color.rgb(101, 116, 159), x + 12, y + 13);
        pixel(canvas, paint, Color.rgb(133, 139, 170), x + 33, y + 20);
        pixel(canvas, paint, Color.rgb(80, 93, 137), x + 42, y + 10);
        pixel(canvas, paint, Color.rgb(177, 167, 168), x + 26, y + 8);
        fill(canvas, paint, Color.rgb(124, 126, 153), x + 20, y + 15, x + 24, y + 19);
        pixel(canvas, paint, Color.rgb(73, 78, 112), x + 20, y + 15);

        // Opaque ground means the surface never relies on preserved pixels.
        fill(canvas, paint, Color.rgb(10, 12, 19), x, y + 64, x + 48, y + 80);
        fill(canvas, paint, Color.rgb(25, 24, 29), x + 8, y + 68, x + 40, y + 71);
        fill(canvas, paint, Color.rgb(37, 35, 39), x + 15, y + 66, x + 19, y + 69);
        fill(canvas, paint, Color.rgb(37, 35, 39), x + 29, y + 66, x + 34, y + 69);

        // Logs and stones.
        fill(canvas, paint, Color.rgb(60, 34, 28), x + 18, y + 64, x + 31, y + 67);
        fill(canvas, paint, Color.rgb(87, 46, 31), x + 20, y + 65, x + 33, y + 68);
        fill(canvas, paint, Color.rgb(63, 62, 70), x + 14, y + 67, x + 19, y + 70);
        fill(canvas, paint, Color.rgb(75, 71, 73), x + 30, y + 67, x + 36, y + 70);

        int bend = FLAME_BEND[frame];
        int top = y + 64 - FLAME_HEIGHT[frame];

        // Blocky outer flame.
        fill(canvas, paint, Color.rgb(175, 48, 25), x + 21, top + 4, x + 29, y + 65);
        fill(canvas, paint, Color.rgb(221, 76, 28), x + 20 + bend, top + 7, x + 31, y + 63);
        fill(canvas, paint, Color.rgb(235, 117, 32), x + 23, top + 2, x + 28 + bend, y + 62);
        pixel(canvas, paint, Color.rgb(243, 141, 42), x + 25 + bend, top);

        // Hot core.
        fill(canvas, paint, Color.rgb(255, 190, 66), x + 23, y + 58, x + 29, y + 65);
        fill(canvas, paint, Color.rgb(255, 224, 135), x + 25, y + 60, x + 28, y + 64);

        // Two embers move on different eight-frame cycles.
        int emberOneY = y + 54 - ((frame * 3) % 15);
        int emberTwoY = y + 58 - ((frame * 5 + 4) % 18);
        pixel(canvas, paint, Color.rgb(242, 102, 31), x + 20 + (frame % 3), emberOneY);
        if ((frame & 1) == 0) {
            pixel(canvas, paint, Color.rgb(255, 172, 58), x + 30 - (frame % 2), emberTwoY);
        }

        // A small glow band, still fully opaque.
        fill(canvas, paint, Color.rgb(29, 20, 24), x + 9, y + 71, x + 40, y + 73);
        fill(canvas, paint, Color.rgb(18, 16, 22), x + 5, y + 73, x + 44, y + 75);
    }

    private static void pixel(Canvas canvas, Paint paint, int color, int x, int y) {
        fill(canvas, paint, color, x, y, x + 1, y + 1);
    }

    private static void fill(
            Canvas canvas,
            Paint paint,
            int color,
            int left,
            int top,
            int right,
            int bottom
    ) {
        paint.setColor(color);
        canvas.drawRect(left, top, right, bottom, paint);
    }
}
