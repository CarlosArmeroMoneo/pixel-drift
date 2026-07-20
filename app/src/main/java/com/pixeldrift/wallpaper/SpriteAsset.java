package com.pixeldrift.wallpaper;

import android.graphics.Bitmap;
import android.graphics.Rect;

/** Decoded sprite sheet plus prevalidated row-major frame geometry. */
final class SpriteAsset {
    private final Bitmap bitmap;
    private final int columns;
    private final int rows;
    private final int frameCount;
    private final int frameWidth;
    private final int frameHeight;

    SpriteAsset(Bitmap bitmap, int columns, int rows, int frameCount) {
        if (bitmap == null) {
            throw new IllegalArgumentException("Bitmap is required");
        }
        if (columns < 1 || rows < 1 || frameCount < 1 || frameCount > columns * rows) {
            throw new IllegalArgumentException("Invalid sprite grid");
        }
        if (bitmap.getWidth() % columns != 0 || bitmap.getHeight() % rows != 0) {
            throw new IllegalArgumentException("Sprite dimensions must divide evenly by the grid");
        }
        this.bitmap = bitmap;
        this.columns = columns;
        this.rows = rows;
        this.frameCount = frameCount;
        this.frameWidth = bitmap.getWidth() / columns;
        this.frameHeight = bitmap.getHeight() / rows;
    }

    Bitmap getBitmap() {
        return bitmap;
    }

    int getFrameCount() {
        return frameCount;
    }

    int getFrameWidth() {
        return frameWidth;
    }

    int getFrameHeight() {
        return frameHeight;
    }

    void sourceRect(int frameIndex, Rect output) {
        int safeFrame = frameIndex % frameCount;
        if (safeFrame < 0) {
            safeFrame += frameCount;
        }
        int column = safeFrame % columns;
        int row = safeFrame / columns;
        int left = column * frameWidth;
        int top = row * frameHeight;
        output.set(left, top, left + frameWidth, top + frameHeight);
    }

}
