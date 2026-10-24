package com.dasannn.framcit.store;

import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.render.FraMCitRenderer;
import org.bukkit.Bukkit;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Almacén principal de persistencia y cache de FraMCit (F5.1 - F5.14).
 * Coordina el índice en index.json, los archivos binarios .fmc en images/,
 * el cache LRU en memoria acotado en megabytes y la carga perezosa asíncrona.
 *
 * Cumple estrictamente la regla 1 de la Constitution: NUNCA realiza I/O de disco
 * en el hilo principal del servidor. Las escrituras, borrados y escaneos de disco
 * corren en el ejecutor de persistencia serializado (un solo hilo FIFO, aparte del
 * pool de descargas); las lecturas perezosas usan el pool de DownloadQueue.
 */
public class ImageStore {

    private final Path dataFolder;
    private final Path imagesFolder;
    private final IndexManager indexManager;
    private final LruMemoryCache lruCache;
    private final DownloadQueue downloadQueue;
    private final Logger logger;

    private final ConcurrentHashMap<Integer, CompletableFuture<ConvertedImage>> inFlightLoads = new ConcurrentHashMap<>();
    private final Set<Integer> pendingMapIds = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingNormalizedUrls = ConcurrentHashMap.newKeySet();
    private final Set<Integer> persistingMapIds = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<Integer, String> pendingUrlByMapId = new ConcurrentHashMap<>();

    /**
     * Ejecutor de persistencia: un único hilo daemon serializado, independiente del pool
     * de descargas. Todas las escrituras y borrados de disco (escrituras .fmc, index.json,
     * borrados .fmc y escaneos de tamaño en disco) corren aquí de forma FIFO, lo que
     * garantiza que un save encolado antes de un delete no puede recrear su archivo.
     */
    private final ExecutorService persistenceExecutor;
    private final AtomicBoolean persistenceClosed = new AtomicBoolean(false);

    public ImageStore(File dataFolder, DownloadQueue downloadQueue, Logger logger) {
        this(dataFolder.toPath(), downloadQueue, logger, LruMemoryCache.DEFAULT_MAX_BYTES);
    }

    public ImageStore(Path dataFolder, DownloadQueue downloadQueue, Logger logger) {
        this(dataFolder, downloadQueue, logger, LruMemoryCache.DEFAULT_MAX_BYTES);
    }

    public ImageStore(Path dataFolder, DownloadQueue downloadQueue, Logger logger, long maxCacheBytes) {
        this.dataFolder = Objects.requireNonNull(dataFolder, "dataFolder cannot be null");
        this.imagesFolder = dataFolder.resolve("images");
        this.indexManager = new IndexManager(dataFolder, logger);
        this.lruCache = new LruMemoryCache(maxCacheBytes);
        this.downloadQueue = downloadQueue;
        this.logger = logger;
        this.persistenceExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "framcit-persist");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Carga el archivo index.json al arrancar el servidor (F5.8).
     */
    public void loadIndex() {
        indexManager.load();
        for (ImageIndexEntry entry : indexManager.getAllEntries()) {
            if (entry.hasPendingDeliveries()) {
                trackPendingDelivery(entry);
            }
        }
    }

    /**
     * Re-vincula el FraMCitRenderer a cada MapView conocido según el índice al arrancar (F5.6, F5.7).
     * - Quita los renderers que traiga el mapa.
     * - Asigna nuestro FraMCitRenderer configurado con carga perezosa.
     * - Si el MapView ya no existe, se registra en el log y se marca como huérfano (F5.7).
     * - Si ocurre un fallo en un mapa, se registra y continúa; NUNCA aborta el arranque.
     */
    public void relinkMaps() {
        relinkMaps(id -> {
            try {
                return Bukkit.getMap(id);
            } catch (Throwable t) {
                return null;
            }
        });
    }

    /**
     * Re-vincula los mapas permitiendo inyectar la función de búsqueda de MapView (útil para pruebas unitarias).
     */
    public void relinkMaps(java.util.function.IntFunction<MapView> mapLookup) {
        Objects.requireNonNull(mapLookup, "mapLookup cannot be null");
        boolean modified = false;
        for (ImageIndexEntry entry : indexManager.getAllEntries()) {
            int mapId = entry.getMapId();
            try {
                MapView mapView = mapLookup.apply(mapId);
                if (mapView == null) {
                    if (logger != null) {
                        logger.warning(String.format("El mapa id=%d ya no existe en el servidor. Marcado como huérfano.", mapId));
                    }
                    entry.setOrphan(true);
                    modified = true;
                    continue;
                }

                for (MapRenderer r : mapView.getRenderers()) {
                    mapView.removeRenderer(r);
                }

                // F6.10: Los mapas animados necesitan renderer contextual.
                // Como index.json ya guarda frameCount, se decide al arrancar sin leer el .fmc.
                // Los estáticos se mantienen NO contextuales para ahorrar canvas por jugador.
                boolean contextual = entry.getFrameCount() > 1;
                mapView.addRenderer(new FraMCitRenderer(mapId, this, null, contextual));
                mapView.setTrackingPosition(false);
                mapView.setUnlimitedTracking(false);

            } catch (Throwable t) {
                if (logger != null) {
                    logger.log(Level.WARNING, "Error al re-vincular el mapa id=" + mapId + ", continuando...", t);
                }
            }
        }

        if (modified) {
            saveIndexAsync();
        }
    }

    /**
     * Busca una entrada en el índice por su URL normalizada (F5.4, F5.5).
     */
    public ImageIndexEntry findByNormalizedUrl(String normalizedUrl) {
        if (normalizedUrl != null && pendingNormalizedUrls.contains(normalizedUrl)) {
            return null;
        }
        return indexManager.getByNormalizedUrl(normalizedUrl);
    }

    /**
     * Busca una entrada en el índice a partir de la URL en texto plano normalizándola.
     */
    public ImageIndexEntry findByUrl(String rawUrl) {
        if (rawUrl == null) return null;
        try {
            String normalized = UrlNormalizer.normalize(rawUrl);
            return findByNormalizedUrl(normalized);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * Busca una entrada en el índice por su id de mapa.
     */
    public ImageIndexEntry findByMapId(int mapId) {
        if (pendingMapIds.contains(mapId)) {
            return null;
        }
        return indexManager.getByMapId(mapId);
    }

    /** True while a newly generated map is not yet durably committed to both files. */
    public boolean isPendingUrl(String normalizedUrl) {
        return normalizedUrl != null && pendingNormalizedUrls.contains(normalizedUrl);
    }

    /** True while a newly generated map is not yet durably committed to both files. */
    public boolean isPendingMap(int mapId) {
        return pendingMapIds.contains(mapId);
    }

    /** Returns persisted deliveries waiting for this player, including first-time and cache-hit maps. */
    public List<ImageIndexEntry> getPendingDeliveries(java.util.UUID playerUuid) {
        if (playerUuid == null) return List.of();
        String uuid = playerUuid.toString();
        return indexManager.getAllEntries().stream()
            .filter(entry -> entry.hasPendingDelivery(uuid))
            .toList();
    }

    /** Records a cache-hit delivery durably before charging or handing over the item. */
    public CompletableFuture<Void> preparePendingDeliveryAsync(int mapId, java.util.UUID playerUuid) {
        if (playerUuid == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("playerUuid cannot be null"));
        }
        ImageIndexEntry entry = indexManager.getByMapId(mapId);
        if (entry == null) {
            return CompletableFuture.failedFuture(new IllegalStateException("No existe el mapa id=" + mapId));
        }
        String uuid = playerUuid.toString();
        entry.addOwner(uuid);
        if (!entry.addPendingDelivery(uuid)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Ya hay una entrega pendiente para el mapa id=" + mapId));
        }
        trackPendingDelivery(entry);
        return saveIndexAsync();
    }

    /** Completes a persisted delivery after the player's inventory (or world drop) received the item. */
    public CompletableFuture<Void> completePendingDeliveryAsync(int mapId, java.util.UUID playerUuid) {
        ImageIndexEntry entry = indexManager.getByMapId(mapId);
        if (entry == null || playerUuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        entry.removePendingDelivery(playerUuid.toString());
        if (entry.hasPendingDeliveries()) {
            trackPendingDelivery(entry);
        } else {
            clearPendingDeliveryTracking(entry);
        }
        return saveIndexAsync();
    }

    /** Cancels a cache-hit delivery and optionally removes the newly acquired ownership. */
    public CompletableFuture<Void> cancelPendingDeliveryAsync(int mapId, java.util.UUID playerUuid, boolean removeOwner) {
        ImageIndexEntry entry = indexManager.getByMapId(mapId);
        if (entry == null || playerUuid == null) {
            return CompletableFuture.completedFuture(null);
        }
        String uuid = playerUuid.toString();
        entry.removePendingDelivery(uuid);
        if (removeOwner) entry.removeOwner(uuid);
        if (entry.hasPendingDeliveries()) {
            trackPendingDelivery(entry);
        } else {
            clearPendingDeliveryTracking(entry);
        }
        return saveIndexAsync();
    }

    private void trackPendingDelivery(ImageIndexEntry entry) {
        pendingMapIds.add(entry.getMapId());
        String normalizedUrl = entry.getNormalizedUrl();
        if (normalizedUrl != null) {
            pendingNormalizedUrls.add(normalizedUrl);
            pendingUrlByMapId.put(entry.getMapId(), normalizedUrl);
        }
    }

    private void clearPendingDeliveryTracking(ImageIndexEntry entry) {
        pendingMapIds.remove(entry.getMapId());
        String normalizedUrl = pendingUrlByMapId.remove(entry.getMapId());
        if (normalizedUrl != null) pendingNormalizedUrls.remove(normalizedUrl);
    }

    /**
     * Obtiene la imagen convertida si está presente en el cache LRU en memoria.
     * Retorna null de inmediato si no está cargada (no bloquea el hilo principal).
     */
    public ConvertedImage getIfPresent(int mapId) {
        return lruCache.get(mapId);
    }

    /**
     * Solicita la carga asíncrona de un archivo .fmc desde disco (F5.9, F5.10).
     * La lectura corre SIEMPRE en el pool de DownloadQueue, jamás en el hilo principal.
     * Si el archivo está ausente o corrupto, se registra y se retorna null (mapa en blanco) (F5.9).
     *
     * @param mapId id del mapa a cargar
     * @return CompletableFuture con la ConvertedImage cargada o null si falló
     */
    public CompletableFuture<ConvertedImage> requestLoadAsync(int mapId) {
        ConvertedImage cached = lruCache.get(mapId);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }

        CompletableFuture<ConvertedImage> existing = inFlightLoads.get(mapId);
        if (existing != null) {
            return existing;
        }

        CompletableFuture<ConvertedImage> result = new CompletableFuture<>();
        existing = inFlightLoads.putIfAbsent(mapId, result);
        if (existing != null) {
            return existing;
        }

        try {
            CompletableFuture.supplyAsync(() -> {
                ImageIndexEntry entry = indexManager.getByMapId(mapId);
                Path fmcPath = imagesFolder.resolve(mapId + ".fmc");

                if (!Files.exists(fmcPath)) {
                    // F5.9: .fmc ausente: mapa en blanco, log, el resto sigue
                    if (logger != null) {
                        logger.warning(String.format("Archivo .fmc ausente para el mapa id=%d (%s). El mapa permanecerá en blanco.", mapId, fmcPath));
                    }
                    return null;
                }

                ImageFormat format = null;
                if (entry != null && entry.getFormat() != null) {
                    try {
                        format = ImageFormat.valueOf(entry.getFormat());
                    } catch (Exception ignored) {}
                }

                try {
                    ConvertedImage loaded = FmcFormat.readImageFromFile(fmcPath, format);
                    lruCache.put(mapId, loaded);
                    return loaded;
                } catch (Throwable t) {
                    // F5.9: .fmc corrupto: mapa en blanco, log, el resto sigue
                    if (logger != null) {
                        logger.warning(String.format("Archivo .fmc corrupto para el mapa id=%d: %s. El mapa permanecerá en blanco.", mapId, t.getMessage()));
                    }
                    return null;
                }
            }, getAsyncExecutor()).whenComplete((loaded, error) -> {
                inFlightLoads.remove(mapId, result);
                if (error != null) {
                    result.completeExceptionally(error);
                } else {
                    result.complete(loaded);
                }
            });
        } catch (Throwable e) {
            inFlightLoads.remove(mapId, result);
            result.completeExceptionally(e);
        }
        return result;
    }

    /**
     * Persiste una imagen de forma duradera (F5.2, F5.11):
     * coloca la imagen en el cache LRU y la entrada en el índice en memoria (estado pendiente),
     * y en el ejecutor de persistencia escribe el .fmc de forma atómica y guardo index.json.
     *
     * El future se completa EXCEPTIONALMENTE si la escritura del .fmc o del índice falla, o si la
     * entrada fue purgada/borrada antes de ejecutarse la escritura. Ante cualquier fallo se hace
     * rollback del estado pendiente en memoria y en el índice, y se eliminan el .fmc final y sus
     * restos temporales.
     *
     * La llamada NUNCA bloquea y jamás corre en el hilo principal.
     *
     * @param mapId id del mapa
     * @param image imagen convertida
     * @param entry entrada a agregar al índice
     * @return CompletableFuture que se completa al finalizar la persistencia duradera
     */
    public CompletableFuture<Void> saveImageAsync(int mapId, ConvertedImage image, ImageIndexEntry entry) {
        return saveImageAsync(mapId, image, entry, false);
    }

    /** Persists a new command result but keeps it hidden from lookup until item delivery commits. */
    public CompletableFuture<Void> saveImagePendingDeliveryAsync(int mapId, ConvertedImage image, ImageIndexEntry entry) {
        return saveImageAsync(mapId, image, entry, true);
    }

    private CompletableFuture<Void> saveImageAsync(int mapId, ConvertedImage image, ImageIndexEntry entry, boolean keepHiddenUntilDelivery) {
        Objects.requireNonNull(image, "image cannot be null");
        Objects.requireNonNull(entry, "entry cannot be null");

        if (!pendingMapIds.add(mapId)) {
            return CompletableFuture.failedFuture(new IllegalStateException("Ya hay una persistencia pendiente para el mapa id=" + mapId));
        }
        persistingMapIds.add(mapId);
        String normalizedUrl = entry.getNormalizedUrl();
        if (normalizedUrl != null && !pendingNormalizedUrls.add(normalizedUrl)) {
            pendingMapIds.remove(mapId);
            persistingMapIds.remove(mapId);
            return CompletableFuture.failedFuture(new IllegalStateException("Ya hay una persistencia pendiente para esa URL."));
        }
        if (normalizedUrl != null) pendingUrlByMapId.put(mapId, normalizedUrl);

        // Estado pendiente en memoria: disponible de inmediato para render, pero revertido si la
        // persistencia falla para no cobrar/entregar algo que no quedó en disco.
        lruCache.put(mapId, image);
        indexManager.addEntry(entry);

        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> {
                // Orden save/delete: si la entrada ya no es esta en el índice, alguien la purgó
                // o borró antes de que la escritura se ejecutara; NO se debe recrear el archivo.
                ImageIndexEntry currentIndexed = indexManager.getByMapId(mapId);
                if (currentIndexed != entry) {
                    persistFailed(mapId, entry);
                    future.completeExceptionally(new IllegalStateException(
                        "La entrada del mapa id=" + mapId + " fue purgada antes de persistir la imagen"));
                    return;
                }
                try {
                    Path fmcPath = imagesFolder.resolve(mapId + ".fmc");
                    FmcFormat.writeToFile(fmcPath, image, entry.getPaletteVersion());
                    Set<Integer> otherPending = new HashSet<>(persistingMapIds);
                    otherPending.remove(mapId);
                    indexManager.saveJson(indexManager.snapshotJson(otherPending));
                    if (keepHiddenUntilDelivery) {
                        persistingMapIds.remove(mapId);
                    } else {
                        clearPending(entry);
                    }
                    future.complete(null);
                } catch (Throwable t) {
                    persistFailed(mapId, entry);
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            // El ejecutor cerrado no escribió ningún byte; revertir solo memoria aquí para
            // no hacer I/O síncrono desde el hilo llamador.
            if (indexManager.getByMapId(mapId) == entry) indexManager.removeEntry(mapId);
            lruCache.remove(mapId);
            clearPending(entry);
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Elimina el archivo .fmc del mapa y guarda index.json sin su entrada, ambos en el ejecutor
     * de persistencia (nunca en el hilo principal). El eliminado en memoria (cache LRU y entrada
     * del índice) debe hacerlo el llamador en el hilo principal ANTES de invocar este método,
     * para que cualquier save encolado antes vea la entrada ausente y no recrea el archivo.
     *
     * @param mapId id del mapa cuyo .fmc se eliminará
     * @return CompletableFuture que se completa al finalizar el borrado en disco
     */
    public CompletableFuture<Void> deleteImageAsync(int mapId) {
        pendingMapIds.remove(mapId);
        String pendingUrl = pendingUrlByMapId.remove(mapId);
        if (pendingUrl != null) pendingNormalizedUrls.remove(pendingUrl);
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> {
                Throwable deleteFailure = null;
                try {
                    Files.deleteIfExists(imagesFolder.resolve(mapId + ".fmc"));
                } catch (Throwable t) {
                    deleteFailure = t;
                }
                try {
                    indexManager.saveJson(indexManager.snapshotJson(new HashSet<>(persistingMapIds)));
                } catch (Throwable saveFailure) {
                    if (deleteFailure != null) saveFailure.addSuppressed(deleteFailure);
                    future.completeExceptionally(saveFailure);
                    return;
                }
                if (deleteFailure != null) {
                    future.completeExceptionally(deleteFailure);
                } else {
                    future.complete(null);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Guarda index.json en el ejecutor de persistencia, fuera del hilo principal (F5.11),
     * sin bloquear ni al llamador ni al hilo que invoca.
     *
     * @return CompletableFuture que se completa al finalizar la escritura del índice
     */
    public CompletableFuture<Void> saveIndexAsync() {
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> {
                try {
                    indexManager.saveJson(indexManager.snapshotJson(new HashSet<>(persistingMapIds)));
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    /**
     * Rollback del estado pendiente ante un fallo de persistencia: revierte la entrada del índice
     * y la imagen del cache LRU en memoria (si sigue siendo la pendiente) y borra el .fmc final
     * y cualquier resto temporal, para que nada quede a medio persistir.
     */
    private void persistFailed(int mapId, ImageIndexEntry entry) {
        if (indexManager.getByMapId(mapId) == entry) {
            indexManager.removeEntry(mapId);
        }
        clearPending(entry);
        lruCache.remove(mapId);
        try {
            Files.deleteIfExists(imagesFolder.resolve(mapId + ".fmc"));
        } catch (IOException e) {
            if (logger != null) {
                logger.warning("No se pudo eliminar el .fmc incompleto del mapa id=" + mapId + ": " + e.getMessage());
            }
        }
        cleanupTempFiles(mapId);
    }

    private void clearPending(ImageIndexEntry entry) {
        pendingMapIds.remove(entry.getMapId());
        persistingMapIds.remove(entry.getMapId());
        String normalizedUrl = pendingUrlByMapId.remove(entry.getMapId());
        if (normalizedUrl != null) pendingNormalizedUrls.remove(normalizedUrl);
    }

    /** Make a durably stored image visible after this recipient's delivery is complete. */
    public CompletableFuture<Void> markImageReady(int mapId, java.util.UUID playerUuid) {
        ImageIndexEntry entry = indexManager.getByMapId(mapId);
        if (entry == null || playerUuid == null || persistingMapIds.contains(mapId)) {
            return CompletableFuture.completedFuture(null);
        }
        return completePendingDeliveryAsync(mapId, playerUuid);
    }

    /**
     * Borra best-effort los archivos temporales de escritura (ToFmc y index.json) que pudieran
     * quedar de escrituras interrumpidas.
     */
    private void cleanupTempFiles(int mapId) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(imagesFolder, mapId + ".fmc.*.tmp")) {
            for (Path tmp : stream) {
                try {
                    Files.deleteIfExists(tmp);
                } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
    }

    /**
     * Guarda síncronamente el estado de index.json a disco (F5.11).
     * Usado al apagar el plugin o al detectar mapas huérfanos. No usar desde comandos en el
     * hilo principal; allí emplea saveIndexAsync().
     */
    public void saveIndex() {
        try {
            indexManager.save();
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "Error al volcar index.json a disco: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Espera hasta que el ejecutor de persistencia haya procesado todas las tareas encoladas,
     * sin bloquear el hilo principal en producción (pensado para pruebas y apagado).
     */
    public void awaitPersistenceIdle() {
        CountDownLatch drained = new CountDownLatch(1);
        try {
            persistenceExecutor.execute(drained::countDown);
        } catch (RejectedExecutionException ignored) {
            return;
        }
        try {
            drained.await(30L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Drena y sale de forma segura: hace que todas las tareas de persistencia pendientes se
     * completen, guarda un último index.json coherente y cierra el ejecutor (usar en onDisable).
     * Idempotente.
     */
    public void flushAndClosePersistence(long timeoutMillis) {
        if (!persistenceClosed.compareAndSet(false, true)) {
            return;
        }

        // Las entregas no confirmadas se conservan con sus destinatarios pendientes en index.json;
        // el listener de login las recuperará sin volver a cobrar.
        try {
            persistenceExecutor.execute(() -> {
                try {
                    indexManager.save();
                } catch (IOException e) {
                    if (logger != null) {
                        logger.log(Level.WARNING, "Error al volcar index.json durante el apagado: " + e.getMessage(), e);
                    }
                }
            });
        } catch (RejectedExecutionException ignored) {}
        persistenceExecutor.shutdown();
        try {
            if (!persistenceExecutor.awaitTermination(Math.max(1L, timeoutMillis), TimeUnit.MILLISECONDS)) {
                persistenceExecutor.shutdownNow();
                if (logger != null) {
                    logger.warning("La cola de persistencia no terminó a tiempo al apagar; tareas pendientes descartadas.");
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            persistenceExecutor.shutdownNow();
        }
    }

    private Executor getAsyncExecutor() {
        return (downloadQueue != null) ? downloadQueue.getExecutor() : ForkJoinPool.commonPool();
    }

    /**
     * Mide el espacio real en disco ocupado por los .fmc de las entradas dadas.
     * Bloqueante (hace I/O de disco), por lo que NUNCA debe invocarse desde el hilo
     * principal; usar measureDiskBytesAsync() desde comandos.
     */
    public long measureDiskBytes(Collection<ImageIndexEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");
        long total = 0;
        for (ImageIndexEntry entry : entries) {
            Path fmc = imagesFolder.resolve(entry.getMapId() + ".fmc");
            try {
                if (Files.exists(fmc)) {
                    total += Files.size(fmc);
                }
            } catch (IOException ignored) {}
        }
        return total;
    }

    /**
     * Versión asíncrona de measureDiskBytes: corre el escaneo de tamaños en el ejecutor de
     * persistencia y no toca el hilo principal.
     */
    public CompletableFuture<Long> measureDiskBytesAsync(Collection<ImageIndexEntry> entries) {
        Objects.requireNonNull(entries, "entries cannot be null");
        List<ImageIndexEntry> snapshot = new ArrayList<>(entries);
        CompletableFuture<Long> future = new CompletableFuture<>();
        try {
            persistenceExecutor.execute(() -> future.complete(measureDiskBytes(snapshot)));
        } catch (RejectedExecutionException e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public IndexManager getIndexManager() {
        return indexManager;
    }

    public LruMemoryCache getLruCache() {
        return lruCache;
    }

    public Path getDataFolder() {
        return dataFolder;
    }

    public Path getImagesFolder() {
        return imagesFolder;
    }

    public DownloadQueue getDownloadQueue() {
        return downloadQueue;
    }
}
