package com.pixeldrift.wallpaper;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.IOException;

/** Decodes immutable source pixels; frame geometry is built separately and can share the bitmap. */
final class AssetLoader {
    private AssetLoader() {
    }

    static Bitmap decodeBitmap(Context context, WallpaperConfig config) {
        if (!config.usesCustomAsset()) {
            Bitmap demo = DemoSpriteFactory.create();
            validateBitmapAllocation(demo);
            return demo;
        }

        AssetStore store = new AssetStore(context);
        try {
            BitmapFactory.Options bounds = store.decodeImportedBounds(config.getAssetRevision());
            validateDimensions(bounds.outWidth, bounds.outHeight);

            BitmapFactory.Options decode = new BitmapFactory.Options();
            decode.inScaled = false;
            decode.inDither = false;
            decode.inPreferredConfig = Bitmap.Config.ARGB_8888;
            Bitmap bitmap = store.decodeImportedBitmap(config.getAssetRevision(), decode);
            if (bitmap == null) {
                throw new IllegalArgumentException("The imported sprite sheet could not be decoded");
            }
            try {
                validateBitmapAllocation(bitmap);
                return bitmap;
            } catch (RuntimeException error) {
                bitmap.recycle();
                throw error;
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("The imported sprite sheet is unavailable", error);
        }
    }

    static void validateDimensions(int width, int height) {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Image dimensions are invalid");
        }
        if (width > AssetStore.MAX_DIMENSION || height > AssetStore.MAX_DIMENSION) {
            throw new IllegalArgumentException("Image dimensions exceed 4096 px");
        }
        long pixels = (long) width * (long) height;
        if (pixels > AssetStore.MAX_DECODED_PIXELS) {
            throw new IllegalArgumentException("Decoded sprite sheet exceeds the 4 megapixel budget");
        }
    }

    static void validateBitmapAllocation(Bitmap bitmap) {
        long bytes = bitmap.getAllocationByteCount();
        if (bytes < 0L || bytes > AssetStore.MAX_DECODED_BYTES) {
            throw new IllegalArgumentException("Decoded sprite sheet exceeds the 16 MiB memory budget");
        }
    }

    static void validateGrid(int width, int height, WallpaperConfig config) {
        if (width % config.getColumns() != 0 || height % config.getRows() != 0) {
            throw new IllegalArgumentException(
                    "Image dimensions must divide evenly by "
                            + config.getColumns() + " columns and " + config.getRows() + " rows"
            );
        }
    }
}
