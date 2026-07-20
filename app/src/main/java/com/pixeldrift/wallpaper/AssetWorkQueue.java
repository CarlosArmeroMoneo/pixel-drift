package com.pixeldrift.wallpaper;

import java.io.InterruptedIOException;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Process-wide serial work and observable state: rotation cannot create competing asset writers. */
final class AssetWorkQueue {
    enum OperationType {
        IMPORT,
        DEMO
    }

    interface Listener {
        void onAssetWorkStateChanged();
    }

    private static final AtomicLong GENERATION = new AtomicLong();
    private static final CopyOnWriteArrayList<Listener> LISTENERS =
            new CopyOnWriteArrayList<>();
    private static final ThreadPoolExecutor EXECUTOR = new ThreadPoolExecutor(
            0,
            1,
            30L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            new ImportThreadFactory()
    );

    private static Snapshot state = Snapshot.idle();

    private AssetWorkQueue() {
    }

    static long beginOperation(OperationType type) {
        long token = GENERATION.incrementAndGet();
        EXECUTOR.getQueue().clear();
        synchronized (AssetWorkQueue.class) {
            state = Snapshot.running(token, type);
        }
        notifyListeners();
        return token;
    }

    static void execute(Runnable runnable) {
        EXECUTOR.execute(runnable);
    }

    static boolean isCurrent(long token) {
        return token == GENERATION.get();
    }

    static void completeSuccess(long token) {
        complete(token, null);
    }

    static void completeFailure(long token, String detail) {
        complete(token, detail == null || detail.trim().isEmpty() ? "Unknown error" : detail);
    }

    private static void complete(long token, String failureDetail) {
        synchronized (AssetWorkQueue.class) {
            if (!isCurrent(token) || !state.running || state.token != token) {
                return;
            }
            state = Snapshot.result(token, state.type, failureDetail);
        }
        notifyListeners();
    }

    static synchronized Snapshot snapshot() {
        return state;
    }

    static synchronized Snapshot consumeResult() {
        Snapshot current = state;
        if (current.running || !current.hasResult()) {
            return current;
        }
        state = Snapshot.idle();
        return current;
    }

    static void addListener(Listener listener) {
        LISTENERS.addIfAbsent(listener);
    }

    static void removeListener(Listener listener) {
        LISTENERS.remove(listener);
    }

    static void throwIfCancelled(long token) throws InterruptedIOException {
        if (Thread.currentThread().isInterrupted() || !isCurrent(token)) {
            throw new InterruptedIOException("Import was superseded");
        }
    }

    private static void notifyListeners() {
        for (Listener listener : LISTENERS) {
            try {
                listener.onAssetWorkStateChanged();
            } catch (RuntimeException ignored) {
                // A broken screen listener must not break the serialized storage transaction.
            }
        }
    }

    static final class Snapshot {
        private final long token;
        private final OperationType type;
        private final boolean running;
        private final boolean hasResult;
        private final String failureDetail;

        private Snapshot(
                long token,
                OperationType type,
                boolean running,
                boolean hasResult,
                String failureDetail
        ) {
            this.token = token;
            this.type = type;
            this.running = running;
            this.hasResult = hasResult;
            this.failureDetail = failureDetail;
        }

        static Snapshot idle() {
            return new Snapshot(0L, null, false, false, null);
        }

        static Snapshot running(long token, OperationType type) {
            return new Snapshot(token, type, true, false, null);
        }

        static Snapshot result(long token, OperationType type, String failureDetail) {
            return new Snapshot(token, type, false, true, failureDetail);
        }

        boolean isRunning() {
            return running;
        }

        boolean hasResult() {
            return hasResult;
        }

        OperationType getType() {
            return type;
        }

        boolean isSuccess() {
            return hasResult && failureDetail == null;
        }

        String getFailureDetail() {
            return failureDetail;
        }
    }

    private static final class ImportThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "PixelDriftImporter");
            thread.setPriority(Thread.MIN_PRIORITY);
            return thread;
        }
    }
}
