package com.pixeldrift.wallpaper;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/** Validates an untrusted document off the UI thread, then atomically installs a private copy. */
public final class AssetImporter {
    private static final int MAX_CONTAINER_CHUNKS = 262_144;
    private static final int PNG_IHDR = 0x49484452;
    private static final int PNG_IEND = 0x49454E44;
    private static final int PNG_ACTL = 0x6163544C;
    private static final int PNG_FCTL = 0x6663544C;
    private static final int PNG_FDAT = 0x66644154;
    private static final int WEBP_ANIM = 0x414E494D;
    private static final int WEBP_ANMF = 0x414E4D46;
    private static final int WEBP_VP8 = 0x56503820;
    private static final int WEBP_VP8L = 0x5650384C;
    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private AssetImporter() {
    }

    public static GridSuggestion importDocument(
            Context context,
            Uri uri,
            AssetStore assetStore,
            int currentColumns,
            int currentRows,
            int currentFrameCount,
            boolean preserveExistingGrid,
            long operationToken
    ) throws IOException {
        if (uri == null) {
            throw new IllegalArgumentException("No image was selected");
        }
        Context appContext = context.getApplicationContext();
        File temporary = File.createTempFile("pixel_drift_import_", ".image", appContext.getCacheDir());
        try {
            copyDocument(appContext.getContentResolver(), uri, temporary, operationToken);
            validateContainer(temporary, operationToken);

            BitmapFactory.Options bounds = AssetStore.decodeBounds(temporary);
            AssetLoader.validateDimensions(bounds.outWidth, bounds.outHeight);
            validateFullDecode(temporary);

            GridSuggestion suggestion = GridSuggestion.suggestForImport(
                    bounds.outWidth,
                    bounds.outHeight,
                    currentColumns,
                    currentRows,
                    currentFrameCount,
                    preserveExistingGrid
            );
            WallpaperConfig proposed = new WallpaperConfig(
                    true,
                    0,
                    suggestion.getColumns(),
                    suggestion.getRows(),
                    suggestion.getFrameCount(),
                    8,
                    "fill",
                    0xFF070814,
                    true
            );
            AssetLoader.validateGrid(bounds.outWidth, bounds.outHeight, proposed);
            throwIfCancelled(operationToken);
            assetStore.installValidated(
                    temporary,
                    suggestion,
                    bounds.outWidth,
                    bounds.outHeight,
                    operationToken
            );
            return suggestion;
        } finally {
            // A failed import never replaces the previous valid private asset.
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
        }
    }

    private static void copyDocument(
            ContentResolver resolver,
            Uri uri,
            File outputFile,
            long operationToken
    ) throws IOException {
        try (InputStream rawInput = resolver.openInputStream(uri)) {
            if (rawInput == null) {
                throw new IOException("The selected document could not be opened");
            }
            try (BufferedInputStream input = new BufferedInputStream(rawInput, 64 * 1024);
                 FileOutputStream output = new FileOutputStream(outputFile)) {
                byte[] buffer = new byte[64 * 1024];
                long total = 0L;
                int read;
                while ((read = input.read(buffer)) != -1) {
                    throwIfCancelled(operationToken);
                    total += read;
                    if (total > AssetStore.MAX_ENCODED_BYTES) {
                        throw new IOException("Encoded image exceeds the 25 MiB limit");
                    }
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
                if (total == 0L) {
                    throw new IOException("The selected document is empty");
                }
            }
        } catch (SecurityException error) {
            throw new IOException("Read access to the selected document was denied", error);
        }
    }

    private static void validateContainer(File file, long operationToken) throws IOException {
        byte[] header = new byte[12];
        try (FileInputStream input = new FileInputStream(file)) {
            int offset = 0;
            while (offset < header.length) {
                int read = input.read(header, offset, header.length - offset);
                if (read < 0) {
                    break;
                }
                offset += read;
            }
        }

        if (startsWith(header, PNG_SIGNATURE)) {
            rejectAnimatedPng(file, operationToken);
            return;
        }
        if (asciiEquals(header, 0, "RIFF") && asciiEquals(header, 8, "WEBP")) {
            rejectAnimatedWebp(file, operationToken);
            return;
        }
        throw new IOException("Use a static PNG or static WebP sprite sheet");
    }

    private static void rejectAnimatedPng(File file, long operationToken) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long position = PNG_SIGNATURE.length;
            long length = input.length();
            int chunks = 0;
            while (position + 12L <= length) {
                throwIfCancelled(operationToken);
                if (++chunks > MAX_CONTAINER_CHUNKS) {
                    throw new IOException("PNG contains too many chunks");
                }
                input.seek(position);
                long chunkLength = input.readInt() & 0xFFFF_FFFFL;
                int type = input.readInt();
                if (chunks == 1 && type != PNG_IHDR) {
                    throw new IOException("PNG does not begin with IHDR");
                }
                if (type == PNG_ACTL || type == PNG_FCTL || type == PNG_FDAT) {
                    throw new IOException("Animated PNG is not accepted; export a sprite sheet instead");
                }
                long next = position + 12L + chunkLength;
                if (next <= position || next > length) {
                    throw new IOException("PNG chunk table is malformed");
                }
                if (type == PNG_IEND) {
                    if (chunkLength != 0L || next != length) {
                        throw new IOException("PNG IEND or trailing data is malformed");
                    }
                    return;
                }
                position = next;
            }
            throw new IOException("PNG is truncated or missing IEND");
        }
    }

    private static void rejectAnimatedWebp(File file, long operationToken) throws IOException {
        try (RandomAccessFile input = new RandomAccessFile(file, "r")) {
            long length = input.length();
            input.seek(4L);
            long declaredSize = readUnsignedLittleEndianInt(input);
            if (declaredSize < 4L || declaredSize + 8L != length) {
                throw new IOException("WebP RIFF size is malformed");
            }

            long position = 12L;
            int chunks = 0;
            boolean sawImageData = false;
            while (position + 8L <= length) {
                throwIfCancelled(operationToken);
                if (++chunks > MAX_CONTAINER_CHUNKS) {
                    throw new IOException("WebP contains too many chunks");
                }
                input.seek(position);
                int type = input.readInt();
                long chunkLength = readUnsignedLittleEndianInt(input);
                if (type == WEBP_ANIM || type == WEBP_ANMF) {
                    throw new IOException("Animated WebP is not accepted; export a sprite sheet instead");
                }
                if (type == WEBP_VP8 || type == WEBP_VP8L) {
                    sawImageData = true;
                }
                long padded = chunkLength + (chunkLength & 1L);
                long next = position + 8L + padded;
                if (next <= position || next > length) {
                    throw new IOException("WebP chunk table is malformed");
                }
                position = next;
            }
            if (position != length || !sawImageData) {
                throw new IOException("WebP is truncated or missing image data");
            }
        }
    }

    private static void validateFullDecode(File file) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        options.inDither = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try {
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (OutOfMemoryError error) {
            throw new IOException("The decoded image does not fit the memory budget", error);
        }
        if (bitmap == null) {
            throw new IOException("Android could not decode the selected image");
        }
        try {
            AssetLoader.validateBitmapAllocation(bitmap);
        } catch (IllegalArgumentException error) {
            throw new IOException(error.getMessage(), error);
        } finally {
            bitmap.recycle();
        }
    }

    private static long readUnsignedLittleEndianInt(RandomAccessFile input) throws IOException {
        long b0 = input.readUnsignedByte();
        long b1 = input.readUnsignedByte();
        long b2 = input.readUnsignedByte();
        long b3 = input.readUnsignedByte();
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24);
    }

    private static boolean startsWith(byte[] input, byte[] prefix) {
        if (input.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (input[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }

    private static boolean asciiEquals(byte[] input, int offset, String value) {
        byte[] expected = value.getBytes(StandardCharsets.US_ASCII);
        if (offset < 0 || offset + expected.length > input.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (input[offset + index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static void throwIfCancelled(long operationToken) throws InterruptedIOException {
        AssetWorkQueue.throwIfCancelled(operationToken);
    }
}
