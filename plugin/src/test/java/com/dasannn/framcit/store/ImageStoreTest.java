package com.dasannn.framcit.store;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.net.DownloadQueue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ImageStoreTest {

    @TempDir
    Path tempDir;

    private DownloadQueue downloadQueue;
    private ImageStore imageStore;
    private static final Logger LOGGER = Logger.getLogger(ImageStoreTest.class.getName());

    @BeforeEach
    void setUp() {
        downloadQueue = new DownloadQueue(2, 10);
        imageStore = new ImageStore(tempDir, downloadQueue, LOGGER);
        imageStore.loadIndex();
    }

    @AfterEach
    void tearDown() {
        if (imageStore != null) imageStore.flushAndClosePersistence(5000L);
        if (downloadQueue != null) {
            downloadQueue.close();
        }
    }

    private ConvertedImage createDummyImage(byte fill) {
        byte[] bytes = new byte[ConvertedFrame.EXPECTED_BYTES];
        Arrays.fill(bytes, fill);
        ConvertedFrame frame = new ConvertedFrame(bytes, 0);
        return new ConvertedImage(ImageFormat.PNG, List.of(frame));
    }

    @Test
    @DisplayName("F5.5 - Acierto de cache: la segunda petición de la misma URL devuelve la imagen ya almacenada sin descargar")
    void testCacheHitSecondRequestReturnsStoredEntry() throws ExecutionException, InterruptedException, TimeoutException {
        String rawUrl = "https://example.com/assets/banner.png";
        String normalizedUrl = UrlNormalizer.normalize(rawUrl);

        ConvertedImage image = createDummyImage((byte) 55);
        ImageIndexEntry entry = new ImageIndexEntry(
            42,
            rawUrl,
            normalizedUrl,
            "Steve",
            "00000000-0000-0000-0000-000000000001",
            "2026-09-10",
            "PNG",
            1,
            List.of(0),
            1,
            false
        );

        // Guardar de forma asíncrona
        CompletableFuture<Void> saveFuture = imageStore.saveImageAsync(42, image, entry);
        saveFuture.get(5, TimeUnit.SECONDS);

        // 1. Búsqueda con la URL exacta
        ImageIndexEntry found = imageStore.findByUrl(rawUrl);
        assertNotNull(found, "Debe encontrar la entrada almacenada por URL original");
        assertEquals(42, found.getMapId());
        assertEquals("Steve", found.getAuthorName());

        // 2. Búsqueda con una URL equivalente (mayúsculas en host, barra final)
        String equivalentUrl = "HTTPS://EXAMPLE.COM/assets/banner.png/";
        ImageIndexEntry foundEq = imageStore.findByUrl(equivalentUrl);
        assertNotNull(foundEq, "Debe encontrar la entrada con URL equivalente gracias a la normalización");
        assertEquals(42, foundEq.getMapId());

        // 3. Verificar que la imagen está disponible de inmediato en cache de memoria
        ConvertedImage cached = imageStore.getIfPresent(42);
        assertNotNull(cached);
        assertArrayEquals(image.getFrames().getFirst().getMapBytes(), cached.getFrames().getFirst().getMapBytes());

        // 4. Verificar que el archivo .fmc y el index.json existen en disco
        assertTrue(Files.exists(tempDir.resolve("images").resolve("42.fmc")));
        assertTrue(Files.exists(tempDir.resolve("index.json")));
    }

    @Test
    @DisplayName("Una escritura fallida completa el future excepcionalmente y revierte la entrada y el LRU")
    void testFailedSaveIsExceptionalAndRollsBackMemoryState() throws Exception {
        Files.writeString(imageStore.getImagesFolder(), "archivo que impide crear images/");
        ConvertedImage image = createDummyImage((byte) 17);
        ImageIndexEntry entry = new ImageIndexEntry(
            777, "https://example.com/fail.png", "https://example.com/fail.png", "Test", "uuid-777",
            "2026-10-24", "PNG", 1, List.of(0), 1, false
        );

        CompletableFuture<Void> save = imageStore.saveImageAsync(777, image, entry);

        assertThrows(ExecutionException.class, () -> save.get(5, TimeUnit.SECONDS));
        assertNull(imageStore.getIndexManager().getByMapId(777));
        assertNull(imageStore.getIfPresent(777));
        assertFalse(imageStore.isPendingMap(777));
        assertFalse(imageStore.isPendingUrl("https://example.com/fail.png"));
    }

    @Test
    @DisplayName("Una entrega pendiente sobrevive al apagado y se puede completar tras cargar el índice")
    void testPendingDeliverySurvivesShutdownAndCanBeRecovered() throws Exception {
        int mapId = 779;
        String ownerUuid = "00000000-0000-0000-0000-000000000779";
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId, "https://example.com/recover.png", "https://example.com/recover.png", "Test", ownerUuid,
            "2026-10-24", "PNG", 1, List.of(0), 1, false, List.of(ownerUuid)
        );
        entry.addPendingDelivery(ownerUuid);

        imageStore.saveImagePendingDeliveryAsync(mapId, createDummyImage((byte) 19), entry).get(5, TimeUnit.SECONDS);
        assertTrue(imageStore.isPendingMap(mapId));
        assertNull(imageStore.findByUrl(entry.getOriginalUrl()), "Una entrega pendiente no debe servirse como cache lista");

        imageStore.flushAndClosePersistence(5000L);
        assertTrue(Files.exists(tempDir.resolve("images").resolve(mapId + ".fmc")), "El apagado conserva la imagen recuperable");

        imageStore = new ImageStore(tempDir, downloadQueue, LOGGER);
        imageStore.loadIndex();
        assertTrue(imageStore.isPendingMap(mapId));
        assertEquals(List.of(entry), imageStore.getPendingDeliveries(java.util.UUID.fromString(ownerUuid)));
        assertEquals(entry.getPendingDeliveryToken(ownerUuid),
            imageStore.getPendingDeliveries(java.util.UUID.fromString(ownerUuid)).getFirst().getPendingDeliveryToken(ownerUuid));

        imageStore.markImageReady(mapId, java.util.UUID.fromString(ownerUuid)).get(5, TimeUnit.SECONDS);
        assertFalse(imageStore.isPendingMap(mapId));
        assertNotNull(imageStore.findByUrl(entry.getOriginalUrl()));

        IndexManager reloaded = new IndexManager(tempDir, LOGGER);
        reloaded.load();
        assertFalse(reloaded.getByMapId(mapId).hasPendingDeliveries(), "El cierre de entrega se persiste en el índice");
    }

    @Test
    @DisplayName("Una compra desde cache registra dueño y destinatario antes de completar la entrega")
    void testCachedDeliveryPersistsPendingRecipient() throws Exception {
        int mapId = 780;
        String authorUuid = "00000000-0000-0000-0000-000000000780";
        java.util.UUID recipient = java.util.UUID.fromString("00000000-0000-0000-0000-000000000781");
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId, "https://example.com/cached-recover.png", "https://example.com/cached-recover.png", "Test", authorUuid,
            "2026-10-24", "PNG", 1, List.of(0), 1, false
        );
        imageStore.saveImageAsync(mapId, createDummyImage((byte) 20), entry).get(5, TimeUnit.SECONDS);

        imageStore.preparePendingDeliveryAsync(mapId, recipient).get(5, TimeUnit.SECONDS);

        assertTrue(entry.hasOwner(recipient));
        assertTrue(imageStore.isPendingMap(mapId));
        assertEquals(List.of(entry), imageStore.getPendingDeliveries(recipient));
        String firstDeliveryToken = entry.getPendingDeliveryToken(recipient.toString());
        imageStore.markImageReady(mapId, recipient).get(5, TimeUnit.SECONDS);
        assertFalse(imageStore.isPendingMap(mapId));
        assertFalse(entry.hasPendingDeliveries());

        imageStore.preparePendingDeliveryAsync(mapId, recipient).get(5, TimeUnit.SECONDS);
        assertNotEquals(firstDeliveryToken, entry.getPendingDeliveryToken(recipient.toString()),
            "Cada copia comprada necesita un token distinto para no confundirla con una anterior");
    }

    @Test
    @DisplayName("Un borrado encolado después de guardar no permite que el save recree el .fmc")
    void testQueuedDeleteWinsOverEarlierSave() throws Exception {
        int mapId = 778;
        ConvertedImage image = createDummyImage((byte) 18);
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId, "https://example.com/delete.png", "https://example.com/delete.png", "Test", "uuid-778",
            "2026-10-24", "PNG", 1, List.of(0), 1, false
        );
        CompletableFuture<Void> save = imageStore.saveImageAsync(mapId, image, entry);

        // Simula que el último propietario borra la imagen antes de que el .fmc termine de escribirse.
        imageStore.getIndexManager().removeEntry(mapId);
        imageStore.getLruCache().remove(mapId);
        CompletableFuture<Void> delete = imageStore.deleteImageAsync(mapId);

        try {
            save.get(5, TimeUnit.SECONDS);
        } catch (ExecutionException expected) {
            // El save puede detectar que la entrada ya fue retirada antes de su turno en la cola.
        }
        delete.get(5, TimeUnit.SECONDS);
        assertFalse(Files.exists(imageStore.getImagesFolder().resolve(mapId + ".fmc")));
        assertNull(imageStore.getIndexManager().getByMapId(mapId));
        assertFalse(imageStore.isPendingMap(mapId));
    }

    @Test
    @DisplayName("F5.10 - Carga perezosa desde disco en el pool de hilos fuera del hilo principal")
    void testLazyLoadFromDiskAsync() throws ExecutionException, InterruptedException, TimeoutException {
        int mapId = 88;
        ConvertedImage original = createDummyImage((byte) 77);
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId,
            "https://example.com/pic.png",
            "https://example.com/pic.png",
            "Alex",
            "uuid-1",
            "2026-09-10",
            "PNG",
            1,
            List.of(0),
            1,
            false
        );

        // Guardar
        imageStore.saveImageAsync(mapId, original, entry).get(5, TimeUnit.SECONDS);

        // Limpiar el cache en memoria para simular reinicio o desalojo LRU
        imageStore.getLruCache().clear();
        assertNull(imageStore.getIfPresent(mapId), "La imagen no debe estar en memoria antes de la carga");

        // Solicitar carga perezosa asíncrona
        CompletableFuture<ConvertedImage> loadFuture = imageStore.requestLoadAsync(mapId);
        ConvertedImage loaded = loadFuture.get(5, TimeUnit.SECONDS);

        assertNotNull(loaded);
        assertArrayEquals(original.getFrames().getFirst().getMapBytes(), loaded.getFrames().getFirst().getMapBytes());

        // Tras la carga, ahora debe estar en el cache LRU en memoria
        assertNotNull(imageStore.getIfPresent(mapId));
    }

    @Test
    @DisplayName("Las cargas concurrentes del mismo mapa comparten un future sin actualizaciones recursivas")
    void testConcurrentLoadsForSameMapShareFuture() throws Exception {
        int mapId = 889;
        ConvertedImage original = createDummyImage((byte) 89);
        Files.createDirectories(imageStore.getImagesFolder());
        FmcFormat.writeToFile(imageStore.getImagesFolder().resolve(mapId + ".fmc"), original, 1);
        imageStore.getIndexManager().addEntry(new ImageIndexEntry(
            mapId, "https://example.com/concurrent.png", "https://example.com/concurrent.png", "Test", "uuid-889",
            "2026-10-24", "PNG", 1, List.of(0), 1, false
        ));

        List<CompletableFuture<ConvertedImage>> loads = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            loads.add(imageStore.requestLoadAsync(mapId));
        }
        CompletableFuture.allOf(loads.toArray(CompletableFuture[]::new)).get(5, TimeUnit.SECONDS);

        ConvertedImage first = loads.getFirst().getNow(null);
        assertNotNull(first);
        for (CompletableFuture<ConvertedImage> load : loads) {
            assertSame(first, load.getNow(null), "Todas las solicitudes deben compartir la carga en curso");
        }
    }

    @Test
    @DisplayName("F5.9 - .fmc ausente no revienta: retorna null (mapa en blanco), loguea y el plugin sigue")
    void testMissingFmcReturnsNullWithoutCrashing() throws ExecutionException, InterruptedException, TimeoutException {
        int nonExistentMapId = 999;
        // No existe el archivo 999.fmc en disco
        CompletableFuture<ConvertedImage> future = imageStore.requestLoadAsync(nonExistentMapId);
        ConvertedImage result = future.get(5, TimeUnit.SECONDS);

        assertNull(result, "Debe retornar null para dejar el mapa en blanco");
        assertNull(imageStore.getIfPresent(nonExistentMapId));
    }

    @Test
    @DisplayName("F5.9 - .fmc corrupto no revienta: retorna null (mapa en blanco), loguea y el plugin sigue")
    void testCorruptedFmcReturnsNullWithoutCrashing() throws IOException, ExecutionException, InterruptedException, TimeoutException {
        int corruptMapId = 500;
        Path imagesDir = tempDir.resolve("images");
        Files.createDirectories(imagesDir);
        Path corruptFile = imagesDir.resolve(corruptMapId + ".fmc");

        // Escribir bytes basura
        Files.writeString(corruptFile, "ESTO NO ES UN FMC VALIDO", StandardCharsets.UTF_8);

        ImageIndexEntry entry = new ImageIndexEntry(
            corruptMapId,
            "https://example.com/bad.png",
            "https://example.com/bad.png",
            "Alex",
            "uuid-2",
            "2026-09-10",
            "PNG",
            1,
            List.of(0),
            1,
            false
        );
        imageStore.getIndexManager().addEntry(entry);

        CompletableFuture<ConvertedImage> future = imageStore.requestLoadAsync(corruptMapId);
        ConvertedImage result = future.get(5, TimeUnit.SECONDS);

        assertNull(result, "Debe retornar null ante archivo corrupto");
        assertNull(imageStore.getIfPresent(corruptMapId));
    }

    @Test
    @DisplayName("F5.6 & F5.7 - relinkMaps re-vincula renderers y marca mapas inexistentes como huérfanos sin abortar")
    void testRelinkMapsWithValidAndOrphanMaps() {
        ImageIndexEntry validEntry = new ImageIndexEntry(
            1, "https://example.com/1.png", "https://example.com/1.png",
            "Player1", "uuid-1", "2026-09-10", "PNG", 1, List.of(0), 1, false
        );
        ImageIndexEntry orphanEntry = new ImageIndexEntry(
            2, "https://example.com/2.png", "https://example.com/2.png",
            "Player2", "uuid-2", "2026-09-10", "PNG", 1, List.of(0), 1, false
        );

        imageStore.getIndexManager().addEntry(validEntry);
        imageStore.getIndexManager().addEntry(orphanEntry);

        List<org.bukkit.map.MapRenderer> attachedRenderers = new java.util.ArrayList<>();
        org.bukkit.map.MapView mockView = (org.bukkit.map.MapView) java.lang.reflect.Proxy.newProxyInstance(
            org.bukkit.map.MapView.class.getClassLoader(),
            new Class<?>[]{ org.bukkit.map.MapView.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getId".equals(name)) return 1;
                if ("getRenderers".equals(name)) return new java.util.ArrayList<>(attachedRenderers);
                if ("removeRenderer".equals(name)) {
                    attachedRenderers.remove((org.bukkit.map.MapRenderer) args[0]);
                    return true;
                }
                if ("addRenderer".equals(name)) {
                    attachedRenderers.add((org.bukkit.map.MapRenderer) args[0]);
                    return null;
                }
                if ("setTrackingPosition".equals(name) || "setUnlimitedTracking".equals(name)) {
                    return null;
                }
                return null;
            }
        );

        // id=1 existe (devuelve mockView), id=2 no existe (devuelve null)
        imageStore.relinkMaps(id -> id == 1 ? mockView : null);

        // id=1 debe tener FraMCitRenderer re-vinculado y no ser huérfano
        assertFalse(validEntry.isOrphan());
        assertEquals(1, attachedRenderers.size());
        assertTrue(attachedRenderers.getFirst() instanceof com.dasannn.framcit.render.FraMCitRenderer);

        // id=2 debe haber sido marcado como huérfano
        assertTrue(orphanEntry.isOrphan(), "El mapa id=2 que no existe debe ser marcado como huérfano");

        // El índice en disco debe haberse guardado con el estado huérfano
        imageStore.awaitPersistenceIdle();
        IndexManager reloaded = new IndexManager(tempDir, LOGGER);
        reloaded.load();
        assertTrue(reloaded.getByMapId(2).isOrphan());
    }

    @Test
    @DisplayName("F5.5 - Simulación de flujo de descarga: petición 2 de URL equivalente no descarga")
    void testCacheHitBypassesDownloadInvocation() throws ExecutionException, InterruptedException, TimeoutException {
        java.util.concurrent.atomic.AtomicInteger downloadCount = new java.util.concurrent.atomic.AtomicInteger(0);

        String url1 = "https://EXAMPLE.COM/banner.png/";
        String url2 = "https://example.com/banner.png";

        // Función simulada que representa el flujo de descarga si hay cache miss
        java.util.function.Function<String, ImageIndexEntry> getOrDownload = requestUrl -> {
            String norm = UrlNormalizer.normalize(requestUrl);
            ImageIndexEntry cached = imageStore.findByNormalizedUrl(norm);
            if (cached != null) {
                return cached;
            }
            // Simular descarga
            downloadCount.incrementAndGet();
            ConvertedImage img = createDummyImage((byte) 1);
            ImageIndexEntry newEntry = new ImageIndexEntry(
                100, requestUrl, norm, "Tester", "uuid", "2026-09-10", "PNG", 1, List.of(0), 1, false
            );
            try {
                imageStore.saveImageAsync(100, img, newEntry).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            return newEntry;
        };

        // 1ª petición: cache miss -> descarga
        ImageIndexEntry first = getOrDownload.apply(url1);
        assertEquals(1, downloadCount.get());
        assertEquals(100, first.getMapId());

        // 2ª petición con URL equivalente: cache hit -> NO descarga
        ImageIndexEntry second = getOrDownload.apply(url2);
        assertEquals(1, downloadCount.get(), "La segunda petición de la misma URL no debe descargar");
        assertEquals(100, second.getMapId(), "Debe retornar la misma entrada de mapa");
    }
}
