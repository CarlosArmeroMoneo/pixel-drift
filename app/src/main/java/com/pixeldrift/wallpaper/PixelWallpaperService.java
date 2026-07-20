package com.pixeldrift.wallpaper;

import android.annotation.RequiresApi;
import android.app.wallpaper.WallpaperDescription;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.os.Process;
import android.os.SystemClock;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.SurfaceHolder;

import java.util.concurrent.atomic.AtomicBoolean;

/** Battery-first live wallpaper: one scheduled bitmap blit per authored frame, and none while hidden. */
public final class PixelWallpaperService extends WallpaperService {
    private static final String TAG = "PixelDrift";
    private static final int MAX_CONSECUTIVE_DRAW_FAILURES = 3;
    private final AssetRepository assetRepository = new AssetRepository();

    @Override
    public Engine onCreateEngine() {
        if (Build.VERSION.SDK_INT >= 36) {
            return new PixelEngineApi36();
        }
        if (Build.VERSION.SDK_INT >= WallpaperTargetPolicy.INDEPENDENT_TARGETS_API) {
            return new PixelEngineApi34();
        }
        return new PixelEngine();
    }

    private class PixelEngine extends Engine
            implements SharedPreferences.OnSharedPreferenceChangeListener {
        private final Paint bitmapPaint = new Paint();
        private final Rect sourceRect = new Rect();
        private final Rect destinationRect = new Rect();
        private final AtomicBoolean preferencesDirty = new AtomicBoolean(true);
        private final AtomicBoolean geometryDirty = new AtomicBoolean(true);
        private final FrameTimeline.Tick tick = new FrameTimeline.Tick();
        private final Object renderStateLock = new Object();
        private final Runnable renderRunnable = this::renderSafely;
        private final BroadcastReceiver powerReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null) {
                    return;
                }
                String action = intent.getAction();
                if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                    screenInteractive = false;
                    cancelRendering();
                } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                    screenInteractive = true;
                    requestImmediateFrame();
                } else if (PowerManager.ACTION_POWER_SAVE_MODE_CHANGED.equals(action)) {
                    batterySaver = powerManager != null && powerManager.isPowerSaveMode();
                    requestImmediateFrame();
                } else {
                    requestImmediateFrame();
                }
            }
        };

        private final PowerManager powerManager =
                (PowerManager) getSystemService(Context.POWER_SERVICE);

        private HandlerThread renderThread;
        private Handler renderHandler;
        private SharedPreferences sharedPreferences;
        private AssetRepository.Lease assetLease;
        private SpriteAsset asset;
        private WallpaperConfig config;
        private long animationEpochMs;
        private volatile int surfaceWidth;
        private volatile int surfaceHeight;
        private int lastEffectiveFps = -1;
        private volatile boolean hardwareCanvasEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
        private volatile boolean retryWithSoftwareCanvas;
        private volatile int consecutiveDrawFailures;
        private volatile int lastFrameIndex;
        private volatile boolean visible;
        private volatile boolean surfaceReady;
        private volatile boolean destroyed;
        private volatile boolean screenInteractive;
        private volatile boolean batterySaver;
        private boolean receiverRegistered;
        private volatile boolean previewEngine;
        private volatile int wallpaperFlags = WallpaperTargetPolicy.SYSTEM;

        PixelEngine() {
            bitmapPaint.setAntiAlias(false);
            bitmapPaint.setFilterBitmap(false);
            bitmapPaint.setDither(false);
        }

        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            previewEngine = isPreview();
            setTouchEventsEnabled(false);
            setOffsetNotificationsEnabled(false);
            surfaceHolder.setFormat(PixelFormat.OPAQUE);
            // WallpaperService's SurfaceHolder rejects setKeepScreenOn(), even when passed false.
            // The framework default is already off, and this app never requests a wake lock.

            renderThread = new HandlerThread(
                    "PixelDriftRenderer",
                    Process.THREAD_PRIORITY_BACKGROUND
            );
            renderThread.start();
            renderHandler = new Handler(renderThread.getLooper());

            sharedPreferences = WallpaperPreferences.preferences(PixelWallpaperService.this);
            sharedPreferences.registerOnSharedPreferenceChangeListener(this);
            refreshPowerState();
            registerPowerReceiver();
        }

        @Override
        public void onVisibilityChanged(boolean isVisible) {
            previewEngine = previewEngine || isPreview();
            visible = isVisible;
            if (isVisible) {
                refreshPowerState();
                requestImmediateFrame();
            } else {
                cancelRendering();
            }
        }

        @Override
        public void onSurfaceCreated(SurfaceHolder holder) {
            super.onSurfaceCreated(holder);
            surfaceReady = true;
            hardwareCanvasEnabled = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O;
            retryWithSoftwareCanvas = false;
            consecutiveDrawFailures = 0;
            Rect frame = holder.getSurfaceFrame();
            surfaceWidth = frame.width();
            surfaceHeight = frame.height();
            geometryDirty.set(true);
            requestImmediateFrame();
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);
            surfaceWidth = width;
            surfaceHeight = height;
            geometryDirty.set(true);
            consecutiveDrawFailures = 0;
            requestImmediateFrame();
        }

        @Override
        public void onSurfaceRedrawNeeded(SurfaceHolder holder) {
            super.onSurfaceRedrawNeeded(holder);
            // Callback2 expects the redraw to be complete on return. Never decode here: draw the
            // last cached frame, or a cheap opaque placeholder while the background decode starts.
            cancelRendering();
            boolean drew = false;
            try {
                synchronized (renderStateLock) {
                    if (!destroyed && surfaceReady && asset != null && config != null) {
                        geometryDirty.getAndSet(false);
                        recomputeDestinationRect();
                        drew = drawFrame(lastFrameIndex);
                    } else {
                        drew = drawPlaceholder(holder);
                    }
                }
            } catch (RuntimeException | OutOfMemoryError error) {
                // This callback runs on the wallpaper host's main thread, so no renderer failure
                // may escape it and terminate the preview process.
                Log.e(TAG, "Wallpaper redraw failed safely", error);
            }
            if (drew) {
                consecutiveDrawFailures = 0;
            }
            requestImmediateFrame();
        }

        @Override
        public void onSurfaceDestroyed(SurfaceHolder holder) {
            surfaceReady = false;
            cancelRendering();
            super.onSurfaceDestroyed(holder);
        }

        @Override
        public void onSharedPreferenceChanged(SharedPreferences preferences, String key) {
            if (!WallpaperPreferences.affectsEngine(
                    previewEngine,
                    wallpaperFlags,
                    Build.VERSION.SDK_INT,
                    key
            )) {
                return;
            }
            preferencesDirty.set(true);
            requestImmediateFrame();
        }

        protected final boolean wasCreatedForPreview() {
            return previewEngine;
        }

        protected final void updateTargetAndReload(int which, boolean draftWasApplied) {
            wallpaperFlags = which;
            if (draftWasApplied) {
                previewEngine = false;
            }
            preferencesDirty.set(true);
            requestImmediateFrame();
        }

        @Override
        public void onDestroy() {
            destroyed = true;
            visible = false;
            surfaceReady = false;
            cancelRendering();

            if (sharedPreferences != null) {
                sharedPreferences.unregisterOnSharedPreferenceChangeListener(this);
            }
            unregisterPowerReceiver();

            Handler handler = renderHandler;
            HandlerThread thread = renderThread;
            Runnable releaseAsset = () -> {
                synchronized (renderStateLock) {
                    if (assetLease != null) {
                        assetLease.release();
                        assetLease = null;
                    }
                    asset = null;
                }
            };
            if (handler == null || !handler.post(releaseAsset)) {
                // A rejected post means the render looper is already gone, so direct release is safe.
                releaseAsset.run();
            }
            if (thread != null) {
                thread.quitSafely();
            }
            super.onDestroy();
        }

        private void requestImmediateFrame() {
            Handler handler = renderHandler;
            if (handler == null || destroyed || !visible || !surfaceReady) {
                return;
            }
            handler.removeCallbacks(renderRunnable);
            handler.post(renderRunnable);
        }

        private void cancelRendering() {
            Handler handler = renderHandler;
            if (handler != null) {
                handler.removeCallbacks(renderRunnable);
            }
        }

        private void renderSafely() {
            try {
                renderFrameAndScheduleNext();
            } catch (RuntimeException | OutOfMemoryError error) {
                // OEM Canvas implementations and damaged private state must not be able to kill
                // Android's live-wallpaper preview host. Stop this cadence instead of retrying in
                // an exception loop; a lifecycle or preference change can request a fresh frame.
                cancelRendering();
                consecutiveDrawFailures = MAX_CONSECUTIVE_DRAW_FAILURES;
                Log.e(TAG, "Renderer paused after an unexpected failure", error);
            }
        }

        private void renderFrameAndScheduleNext() {
            // A lifecycle event can post an immediate render while this runnable is executing.
            // Clearing future duplicates here guarantees that only one cadence survives.
            renderHandler.removeCallbacks(renderRunnable);

            if (!PowerPolicy.shouldRender(visible, surfaceReady, screenInteractive, destroyed)) {
                return;
            }

            long now = SystemClock.uptimeMillis();
            if (preferencesDirty.getAndSet(false) || config == null || asset == null) {
                reloadConfiguration(now);
            }
            // BitmapFactory decoding cannot be interrupted. Recheck volatile lifecycle gates
            // after a potentially slow reload so a newly hidden engine never posts that frame.
            if (!PowerPolicy.shouldRender(visible, surfaceReady, screenInteractive, destroyed)) {
                return;
            }
            if (asset == null || config == null || surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            if (geometryDirty.getAndSet(false)) {
                synchronized (renderStateLock) {
                    recomputeDestinationRect();
                }
            }

            int effectiveFps = PowerPolicy.effectiveFps(
                    config.getFps(),
                    batterySaver,
                    config.freezesInBatterySaver(),
                    asset.getFrameCount()
            );
            if (lastEffectiveFps != effectiveFps) {
                lastEffectiveFps = effectiveFps;
                animationEpochMs = now;
            }

            FrameTimeline.scheduleInto(
                    tick,
                    animationEpochMs,
                    now,
                    asset.getFrameCount(),
                    effectiveFps
            );
            if (!PowerPolicy.shouldRender(visible, surfaceReady, screenInteractive, destroyed)) {
                return;
            }
            lastFrameIndex = tick.getFrameIndex();
            boolean drawSucceeded;
            synchronized (renderStateLock) {
                drawSucceeded = drawFrame(lastFrameIndex);
            }
            if (drawSucceeded) {
                consecutiveDrawFailures = 0;
            } else if (retryWithSoftwareCanvas) {
                retryWithSoftwareCanvas = false;
                renderHandler.post(renderRunnable);
                return;
            } else if (++consecutiveDrawFailures >= MAX_CONSECUTIVE_DRAW_FAILURES) {
                Log.w(TAG, "Renderer paused after repeated surface draw failures");
                return;
            }

            if (!tick.isFrozen()
                    && PowerPolicy.shouldRender(
                    visible,
                    surfaceReady,
                    screenInteractive,
                    destroyed)) {
                renderHandler.postAtTime(renderRunnable, tick.getNextDeadlineMs());
            }
        }

        private void reloadConfiguration(long now) {
            WallpaperPreferences preferences = new WallpaperPreferences(
                    PixelWallpaperService.this
            );
            WallpaperConfig requested = previewEngine
                    ? preferences.load()
                    : preferences.loadApplied(wallpaperFlags);
            boolean needsAsset = asset == null || !requested.sameAssetLayout(config);
            WallpaperConfig active = requested;

            if (needsAsset) {
                AssetRepository.Lease replacementLease;
                SpriteAsset replacement;
                try {
                    replacementLease = assetRepository.acquire(
                            PixelWallpaperService.this,
                            requested
                    );
                    replacement = replacementLease.getAsset();
                } catch (RuntimeException | OutOfMemoryError error) {
                    Log.w(TAG, "Imported asset rejected by renderer; using built-in demo", error);
                    active = fallbackConfig(requested);
                    try {
                        replacementLease = assetRepository.acquire(
                                PixelWallpaperService.this,
                                active
                        );
                        replacement = replacementLease.getAsset();
                    } catch (RuntimeException | OutOfMemoryError fallbackError) {
                        Log.e(TAG, "Built-in wallpaper asset failed", fallbackError);
                        return;
                    }
                }

                synchronized (renderStateLock) {
                    AssetRepository.Lease previousLease = assetLease;
                    assetLease = replacementLease;
                    asset = replacement;
                    if (previousLease != null) {
                        previousLease.release();
                    }
                }
                consecutiveDrawFailures = 0;
            }

            boolean cadenceChanged = needsAsset
                    || config == null
                    || config.getFps() != active.getFps();
            synchronized (renderStateLock) {
                config = active;
                // The render loop owns the clear-before-compute operation. If a surface callback
                // sets this during geometry work, that newer update remains for the next pass.
                geometryDirty.set(true);
            }
            if (cadenceChanged) {
                animationEpochMs = now;
                lastEffectiveFps = -1;
            }
        }

        private WallpaperConfig fallbackConfig(WallpaperConfig requested) {
            return new WallpaperConfig(
                    false,
                    requested.getAssetRevision(),
                    DemoSpriteFactory.COLUMNS,
                    DemoSpriteFactory.ROWS,
                    DemoSpriteFactory.FRAME_COUNT,
                    requested.getFps(),
                    requested.getScaleMode(),
                    requested.getBackgroundColor(),
                    requested.freezesInBatterySaver()
            );
        }

        private void recomputeDestinationRect() {
            if (asset == null || config == null || surfaceWidth <= 0 || surfaceHeight <= 0) {
                return;
            }
            ScaleGeometry.Rect geometry = ScaleGeometry.calculate(
                    config.getScaleMode(),
                    asset.getFrameWidth(),
                    asset.getFrameHeight(),
                    surfaceWidth,
                    surfaceHeight
            );
            destinationRect.set(
                    geometry.getLeft(),
                    geometry.getTop(),
                    geometry.getRight(),
                    geometry.getBottom()
            );
        }

        private boolean drawFrame(int frameIndex) {
            SurfaceHolder holder = getSurfaceHolder();
            Canvas canvas = null;
            boolean drew = false;
            boolean posted = false;
            try {
                canvas = lockBestCanvas(holder);
                if (canvas == null) {
                    return false;
                }
                if (asset.getBitmap().getWidth() > canvas.getMaximumBitmapWidth()
                        || asset.getBitmap().getHeight() > canvas.getMaximumBitmapHeight()) {
                    canvas.drawColor(config.getBackgroundColor());
                    if (canvas.isHardwareAccelerated()) {
                        hardwareCanvasEnabled = false;
                        retryWithSoftwareCanvas = true;
                        Log.d(TAG, "Sprite exceeds hardware Canvas limit; retrying in software");
                    } else {
                        Log.w(TAG, "Sprite sheet exceeds this Canvas bitmap limit");
                    }
                    return false;
                }
                canvas.drawColor(config.getBackgroundColor());
                asset.sourceRect(frameIndex, sourceRect);
                canvas.drawBitmap(asset.getBitmap(), sourceRect, destinationRect, bitmapPaint);
                drew = true;
            } catch (RuntimeException error) {
                Log.d(TAG, "Surface changed during wallpaper draw", error);
            } catch (OutOfMemoryError error) {
                handleCanvasOutOfMemory(canvas, "wallpaper frame", error);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                        posted = true;
                    } catch (RuntimeException | OutOfMemoryError error) {
                        Log.d(TAG, "Surface disappeared before canvas post", error);
                    }
                }
            }
            return drew && posted;
        }

        private boolean drawPlaceholder(SurfaceHolder holder) {
            Canvas canvas = null;
            boolean drew = false;
            boolean posted = false;
            try {
                canvas = lockBestCanvas(holder);
                if (canvas == null) {
                    return false;
                }
                canvas.drawColor(0xFF070814);
                drew = true;
            } catch (RuntimeException error) {
                Log.d(TAG, "Surface unavailable for placeholder redraw", error);
            } catch (OutOfMemoryError error) {
                handleCanvasOutOfMemory(canvas, "placeholder", error);
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas);
                        posted = true;
                    } catch (RuntimeException | OutOfMemoryError error) {
                        Log.d(TAG, "Surface disappeared before placeholder post", error);
                    }
                }
            }
            return drew && posted;
        }

        private Canvas lockBestCanvas(SurfaceHolder holder) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hardwareCanvasEnabled) {
                try {
                    return holder.lockHardwareCanvas();
                } catch (RuntimeException | OutOfMemoryError hardwareFailure) {
                    hardwareCanvasEnabled = false;
                    Log.d(TAG, "Hardware canvas unavailable; falling back to software");
                }
            }
            return holder.lockCanvas();
        }

        private void handleCanvasOutOfMemory(
                Canvas canvas,
                String operation,
                OutOfMemoryError error
        ) {
            if (canvas != null && canvas.isHardwareAccelerated()) {
                hardwareCanvasEnabled = false;
                retryWithSoftwareCanvas = true;
                Log.w(TAG, "Hardware " + operation + " exhausted memory; retrying once in software", error);
                return;
            }
            retryWithSoftwareCanvas = false;
            consecutiveDrawFailures = MAX_CONSECUTIVE_DRAW_FAILURES - 1;
            Log.e(TAG, "Software " + operation + " exhausted memory; renderer will pause", error);
        }

        private void registerPowerReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_SCREEN_ON);
            filter.addAction(Intent.ACTION_SCREEN_OFF);
            filter.addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED);
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(powerReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
                } else {
                    registerReceiver(powerReceiver, filter);
                }
                receiverRegistered = true;
            } catch (RuntimeException error) {
                Log.w(TAG, "Power-state receiver unavailable; lifecycle gates remain active", error);
            }
        }

        private void refreshPowerState() {
            // Visibility/surface gates remain sufficient if an unusual device omits this service.
            screenInteractive = powerManager == null || powerManager.isInteractive();
            batterySaver = powerManager != null && powerManager.isPowerSaveMode();
        }

        private void unregisterPowerReceiver() {
            if (!receiverRegistered) {
                return;
            }
            try {
                unregisterReceiver(powerReceiver);
            } catch (IllegalArgumentException ignored) {
                // Already unregistered by the framework.
            }
            receiverRegistered = false;
        }
    }

    @RequiresApi(34)
    private class PixelEngineApi34 extends PixelEngine {
        @Override
        public void onCreate(SurfaceHolder surfaceHolder) {
            super.onCreate(surfaceHolder);
            int initialFlags = getWallpaperFlags();
            if (initialFlags != 0) {
                updateTargetAndReload(initialFlags, false);
            }
        }

        @Override
        public void onWallpaperFlagsChanged(int which) {
            if (which == 0) {
                return;
            }
            // A preview engine receives target flags only after the user confirms in Android's
            // picker. Active engines never copy a draft merely because draft preferences change.
            boolean committed = false;
            if (wasCreatedForPreview()) {
                committed = new WallpaperPreferences(PixelWallpaperService.this)
                        .applyDraft(which);
                if (!committed) {
                    Log.e(TAG, "Android applied the wallpaper but its draft could not be persisted");
                }
            }
            updateTargetAndReload(which, committed);
        }
    }

    @RequiresApi(36)
    private final class PixelEngineApi36 extends PixelEngineApi34 {
        @Override
        public WallpaperDescription onApplyWallpaper(int which) {
            if (which != 0) {
                boolean committed = new WallpaperPreferences(PixelWallpaperService.this)
                        .applyDraft(which);
                if (!committed) {
                    Log.e(TAG, "Confirmed wallpaper draft could not be persisted");
                }
                updateTargetAndReload(which, committed);
            }
            return null;
        }
    }
}
