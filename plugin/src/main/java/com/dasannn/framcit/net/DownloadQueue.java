package com.dasannn.framcit.net;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Cola de descargas acotada con un pool de 4 hilos dedicados fuera del hilo principal.
 * Cumple con Constitution 5.1 (el servidor nunca se bloquea) y Spec 5 (4 descargas simultaneas).
 *
 * Cuando la cola acotada se llena, rechaza limpiamente la ejecucion devolviendo
 * un CompletableFuture completado con DownloadError.QueueFull, sin lanzar excepciones sueltas.
 */
public class DownloadQueue implements AutoCloseable {

    public static final int DEFAULT_THREADS = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 20;

    private final ThreadPoolExecutor executor;
    private final int capacity;

    public DownloadQueue() {
        this(DEFAULT_THREADS, DEFAULT_QUEUE_CAPACITY);
    }

    public DownloadQueue(int threads, int queueCapacity) {
        if (threads <= 0) {
            throw new IllegalArgumentException("threads must be > 0");
        }
        if (queueCapacity <= 0) {
            throw new IllegalArgumentException("queueCapacity must be > 0");
        }

        this.capacity = queueCapacity;
        AtomicInteger threadCount = new AtomicInteger(1);
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "framcit-net-" + threadCount.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };

        this.executor = new ThreadPoolExecutor(
            threads,
            threads,
            60L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(queueCapacity),
            threadFactory,
            new ThreadPoolExecutor.AbortPolicy()
        );
    }

    /**
     * Envia una tarea de descarga al pool de hilos.
     * Si la cola acotada esta llena, devuelve un CompletableFuture con DownloadError.QueueFull.
     *
     * @param task la tarea que retorna un DownloadResult.
     * @return CompletableFuture con el DownloadResult obtenido o el rechazo por cola llena.
     */
    public CompletableFuture<DownloadResult> submit(Callable<DownloadResult> task) {
        Objects.requireNonNull(task, "task cannot be null");
        CompletableFuture<DownloadResult> future = new CompletableFuture<>();

        try {
            executor.execute(() -> {
                try {
                    DownloadResult result = task.call();
                    future.complete(result);
                } catch (Throwable t) {
                    future.complete(DownloadResult.failure(new DownloadError.ConnectionFailed(t.getMessage())));
                }
            });
        } catch (RejectedExecutionException e) {
            future.complete(DownloadResult.failure(new DownloadError.QueueFull()));
        }

        return future;
    }

    /**
     * Envia la descarga de una URL usando el downloader especificado.
     */
    public CompletableFuture<DownloadResult> submit(Downloader downloader, String rawUrl) {
        Objects.requireNonNull(downloader, "downloader cannot be null");
        return submit(() -> downloader.download(rawUrl));
    }

    /**
     * Envia la descarga de un URI usando el downloader especificado.
     */
    public CompletableFuture<DownloadResult> submit(Downloader downloader, URI uri) {
        Objects.requireNonNull(downloader, "downloader cannot be null");
        return submit(() -> downloader.download(uri));
    }

    public int getActiveCount() {
        return executor.getActiveCount();
    }

    public int getQueueSize() {
        return executor.getQueue().size();
    }

    public int getQueueCapacity() {
        return capacity;
    }

    public boolean isBusy() {
        return executor.getActiveCount() >= executor.getCorePoolSize();
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
