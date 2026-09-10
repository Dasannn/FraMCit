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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Almacén principal de persistencia y cache de FraMCit (F5.1 - F5.14).
 * Coordina el índice en index.json, los archivos binarios .fmc en images/,
 * el cache LRU en memoria acotado en megabytes y la carga perezosa asíncrona.
 *
 * Cumple estrictamente la regla 1 de la Constitution: NUNCA realiza I/O de disco
 * en el hilo principal del servidor. Toda lectura y escritura ocurre en el pool
 * de DownloadQueue.
 */
public class ImageStore {

    private final Path dataFolder;
    private final Path imagesFolder;
    private final IndexManager indexManager;
    private final LruMemoryCache lruCache;
    private final DownloadQueue downloadQueue;
    private final Logger logger;

    private final ConcurrentHashMap<Integer, CompletableFuture<ConvertedImage>> inFlightLoads = new ConcurrentHashMap<>();

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
    }

    /**
     * Carga el archivo index.json al arrancar el servidor (F5.8).
     */
    public void loadIndex() {
        indexManager.load();
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
            saveIndex();
        }
    }

    /**
     * Busca una entrada en el índice por su URL normalizada (F5.4, F5.5).
     */
    public ImageIndexEntry findByNormalizedUrl(String normalizedUrl) {
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
        return indexManager.getByMapId(mapId);
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

        return inFlightLoads.computeIfAbsent(mapId, id -> {
            Executor executor = getAsyncExecutor();
            return CompletableFuture.supplyAsync(() -> {
                ImageIndexEntry entry = indexManager.getByMapId(id);
                Path fmcPath = imagesFolder.resolve(id + ".fmc");

                if (!Files.exists(fmcPath)) {
                    // F5.9: .fmc ausente: mapa en blanco, log, el resto sigue
                    if (logger != null) {
                        logger.warning(String.format("Archivo .fmc ausente para el mapa id=%d (%s). El mapa permanecerá en blanco.", id, fmcPath));
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
                    lruCache.put(id, loaded);
                    return loaded;
                } catch (Throwable t) {
                    // F5.9: .fmc corrupto: mapa en blanco, log, el resto sigue
                    if (logger != null) {
                        logger.warning(String.format("Archivo .fmc corrupto para el mapa id=%d: %s. El mapa permanecerá en blanco.", id, t.getMessage()));
                    }
                    return null;
                }
            }, executor).whenComplete((res, err) -> {
                inFlightLoads.remove(id);
            });
        });
    }

    /**
     * Guarda una imagen en disco (.fmc) y actualiza index.json de forma asíncrona (F5.2, F5.11).
     * Coloca inmediatamente la imagen en el cache LRU en memoria.
     * La escritura de disco y el renombrado atómico se ejecutan en el pool asíncrono.
     *
     * @param mapId id del mapa
     * @param image imagen convertida
     * @param entry entrada a agregar al índice
     * @return CompletableFuture que se completa al finalizar la escritura a disco
     */
    public CompletableFuture<Void> saveImageAsync(int mapId, ConvertedImage image, ImageIndexEntry entry) {
        Objects.requireNonNull(image, "image cannot be null");
        Objects.requireNonNull(entry, "entry cannot be null");

        // Disponible inmediatamente en memoria
        lruCache.put(mapId, image);
        indexManager.addEntry(entry);

        Executor executor = getAsyncExecutor();
        return CompletableFuture.runAsync(() -> {
            try {
                Path fmcPath = imagesFolder.resolve(mapId + ".fmc");
                FmcFormat.writeToFile(fmcPath, image, entry.getPaletteVersion());
                indexManager.save();
            } catch (Throwable t) {
                if (logger != null) {
                    logger.log(Level.SEVERE, "Error al persistir la imagen/índice para el mapa id=" + mapId, t);
                }
            }
        }, executor);
    }

    /**
     * Guarda síncronamente el estado de index.json a disco (F5.11).
     * Usado al apagar el plugin o al detectar mapas huérfanos.
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

    private Executor getAsyncExecutor() {
        return (downloadQueue != null) ? downloadQueue.getExecutor() : ForkJoinPool.commonPool();
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
