package com.dasannn.framcit.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class DownloadQueueTest {

    @Test
    @DisplayName("F2.6: DownloadQueue ejecuta hasta 4 descargas simultaneas")
    void shouldExecuteUpTo4ConcurrentTasks() throws InterruptedException {
        int threads = 4;
        int capacity = 10;
        try (DownloadQueue queue = new DownloadQueue(threads, capacity)) {
            CountDownLatch readyLatch = new CountDownLatch(threads);
            CountDownLatch releaseLatch = new CountDownLatch(1);
            AtomicInteger runningSimultaneously = new AtomicInteger(0);
            AtomicInteger maxObserved = new AtomicInteger(0);

            List<CompletableFuture<DownloadResult>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(queue.submit(() -> {
                    int current = runningSimultaneously.incrementAndGet();
                    maxObserved.updateAndGet(max -> Math.max(max, current));
                    readyLatch.countDown();
                    releaseLatch.await(5, TimeUnit.SECONDS);
                    runningSimultaneously.decrementAndGet();
                    return DownloadResult.success(new byte[] { 1 }, "image/png", URI.create("http://example.com/1.png"));
                }));
            }

            assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "Los 4 hilos deben estar corriendo concurrentemente");
            assertEquals(4, maxObserved.get(), "Deben observarse 4 hilos corriendo en paralelo");

            releaseLatch.countDown();

            for (CompletableFuture<DownloadResult> f : futures) {
                DownloadResult res = f.join();
                assertTrue(res.isSuccess());
            }
        }
    }

    @Test
    @DisplayName("F2.6: Rechaza limpiamente con QueueFull cuando la cola acotada se satura")
    void shouldRejectCleanlyWhenQueueIsFull() throws InterruptedException {
        int threads = 2;
        int capacity = 2; // Capacidad acotada muy pequena para forzar el rechazo
        try (DownloadQueue queue = new DownloadQueue(threads, capacity)) {
            CountDownLatch blockingLatch = new CountDownLatch(1);
            List<CompletableFuture<DownloadResult>> activeFutures = new ArrayList<>();

            // Ocupamos los 2 hilos activos
            for (int i = 0; i < threads; i++) {
                activeFutures.add(queue.submit(() -> {
                    blockingLatch.await(5, TimeUnit.SECONDS);
                    return DownloadResult.success(new byte[] { 1 }, "image/png", URI.create("http://example.com/ok"));
                }));
            }

            // Llenamos la cola acotada con 2 tareas
            for (int i = 0; i < capacity; i++) {
                activeFutures.add(queue.submit(() -> {
                    blockingLatch.await(5, TimeUnit.SECONDS);
                    return DownloadResult.success(new byte[] { 1 }, "image/png", URI.create("http://example.com/ok"));
                }));
            }

            // La siguiente tarea supera hilos (2) + cola (2) = 4. Debe rechazarse con QueueFull
            CompletableFuture<DownloadResult> overflow = queue.submit(() -> {
                return DownloadResult.success(new byte[] { 1 }, "image/png", URI.create("http://example.com/ok"));
            });

            DownloadResult overflowResult = overflow.join();
            assertFalse(overflowResult.isSuccess(), "La tarea desbordada debe ser rechazada");
            assertTrue(overflowResult.error().isPresent());
            assertInstanceOf(DownloadError.QueueFull.class, overflowResult.error().get());
            assertEquals("Hay varias descargas en curso, espera un momento.", overflowResult.error().get().userMessage());

            // Liberamos las tareas bloqueadas
            blockingLatch.countDown();
            for (CompletableFuture<DownloadResult> f : activeFutures) {
                f.join();
            }
        }
    }

    @Test
    @DisplayName("F2.6: Cierre limpio del executor")
    void shouldShutdownCleanly() {
        DownloadQueue queue = new DownloadQueue();
        assertFalse(queue.isShutdown());
        queue.close();
        assertTrue(queue.isShutdown());
    }
}
