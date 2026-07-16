package com.cy311.omnisearch.data.client;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * Fixed-size thread pool executor for blocking I/O operations.
 * <p>
 * Replaces ad-hoc {@code new Thread()} creation with a bounded pool
 * that limits concurrent network requests and image downloads.
 */
public final class RequestExecutor implements AutoCloseable {
    private static final int THREAD_COUNT = 6;

    private final ExecutorService ioPool;

    public RequestExecutor() {
        this.ioPool = Executors.newFixedThreadPool(THREAD_COUNT, new DaemonThreadFactory("omnisearch-io"));
    }

    /**
     * Submits a blocking task to the I/O pool.
     * Exceptions are wrapped in {@link CompletionException}.
     */
    public <T> CompletableFuture<T> submit(Callable<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.call();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, ioPool);
    }

    @Override
    public void close() {
        ioPool.shutdownNow();
    }

    private static final class DaemonThreadFactory implements ThreadFactory {
        private final String namePrefix;

        DaemonThreadFactory(String namePrefix) {
            this.namePrefix = namePrefix;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setName(namePrefix + "-" + t.threadId());
            t.setDaemon(true);
            return t;
        }
    }
}
