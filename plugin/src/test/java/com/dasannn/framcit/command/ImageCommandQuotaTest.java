package com.dasannn.framcit.command;

import com.dasannn.framcit.config.FraMCitConfig;
import com.dasannn.framcit.economy.ChargeResult;
import com.dasannn.framcit.economy.EconomyCheckResult;
import com.dasannn.framcit.economy.EconomyService;
import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.quota.QuotaService;
import com.dasannn.framcit.service.ImageProcessResult;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ImageCommandQuotaTest {

    @TempDir
    Path tempDir;

    private DownloadQueue downloadQueue;
    private ImageStore imageStore;
    private final Logger logger = Logger.getLogger("ImageCommandQuotaTest");

    @BeforeEach
    void setUp() {
        Messages.resetDefaults();
        this.downloadQueue = new DownloadQueue(2, 5);
        this.imageStore = new ImageStore(tempDir, downloadQueue, logger);
        this.imageStore.loadIndex();
    }

    @AfterEach
    void tearDown() {
        Messages.resetDefaults();
        if (imageStore != null) imageStore.flushAndClosePersistence(5000L);
        if (downloadQueue != null) {
            downloadQueue.close();
        }
    }

    private Player createMockPlayer(UUID uuid, String name, Set<String> permissions, List<String> receivedMessages) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getUniqueId".equals(mName)) return uuid;
                if ("getName".equals(mName)) return name;
                if ("isOnline".equals(mName)) return true;
                if ("isOp".equals(mName)) return permissions.contains("op");
                if ("hasPermission".equals(mName) && args != null && args.length > 0) {
                    String permission = (String) args[0];
                    return permissions.contains(permission)
                        || ("framcit.imagen".equals(permission) && permissions.contains("framcit.admin"));
                }
                if ("sendMessage".equals(mName) && args != null && args.length > 0) {
                    if (args[0] instanceof Component comp) {
                        receivedMessages.add(PlainTextComponentSerializer.plainText().serialize(comp));
                    } else if (args[0] instanceof String s) {
                        receivedMessages.add(s);
                    }
                    return null;
                }
                return null;
            }
        );
    }

    private CommandSender createMockAdmin(String name, List<String> receivedMessages) {
        return (CommandSender) Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(),
            new Class<?>[]{ CommandSender.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getName".equals(mName)) return name;
                if ("hasPermission".equals(mName)) return true;
                if ("sendMessage".equals(mName) && args != null && args.length > 0) {
                    if (args[0] instanceof Component comp) {
                        receivedMessages.add(PlainTextComponentSerializer.plainText().serialize(comp));
                    } else if (args[0] instanceof String s) {
                        receivedMessages.add(s);
                    }
                    return null;
                }
                return null;
            }
        );
    }

    private static MapView createMockMapView(int id) {
        return (MapView) Proxy.newProxyInstance(
            MapView.class.getClassLoader(),
            new Class<?>[]{ MapView.class },
            (proxy, method, args) -> {
                if ("getId".equals(method.getName())) return id;
                if ("getRenderers".equals(method.getName())) return new ArrayList<MapRenderer>();
                return null;
            }
        );
    }

    static class MockEconomyService implements EconomyService {
        final AtomicInteger checkCalls = new AtomicInteger(0);
        final AtomicInteger chargeCalls = new AtomicInteger(0);
        boolean canPay = true;
        boolean chargeSuccess = true;

        @Override
        public boolean isEnabled() {
            return true;
        }

        @Override
        public double getPrice() {
            return 10.0;
        }

        @Override
        public String formatPrice() {
            return "10 $";
        }

        @Override
        public String format(double amount) {
            return amount + " $";
        }

        @Override
        public boolean isExempt(Player player) {
            return false;
        }

        @Override
        public EconomyCheckResult checkCanPay(Player player) {
            checkCalls.incrementAndGet();
            return canPay ? new EconomyCheckResult.Allowed(true) : new EconomyCheckResult.InsufficientFunds("10 $", "0 $");
        }

        @Override
        public ChargeResult charge(Player player) {
            chargeCalls.incrementAndGet();
            return chargeSuccess ? new ChargeResult.Success(false, "10 $") : new ChargeResult.InsufficientFunds();
        }
    }

    static class MockImageService extends ImageService {
        final AtomicInteger calls = new AtomicInteger(0);

        MockImageService(DownloadQueue queue) {
            super(new com.dasannn.framcit.net.Downloader(), queue);
        }

        @Override
        public CompletableFuture<ImageProcessResult> process(String rawUrl, int maxFrames) {
            calls.incrementAndGet();
            return new CompletableFuture<>();
        }
    }

    @Test
    @DisplayName("F11.10 & Criterio 1 - Sin slot no se mira el saldo ni se descarga nada")
    void testNoSlotRejectionDoesNotCheckBalanceOrDownload() {
        UUID playerUuid = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(playerUuid, "Alice", Set.of("framcit.imagen", "framcit.slots.1"), messages);

        // Configuración con cupo 1 y 1 imagen ya registrada
        ImageIndexEntry existing = new ImageIndexEntry(
            1, "https://example.com/one.png", "https://example.com/one.png",
            "Alice", playerUuid.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerUuid.toString()))
        );
        imageStore.getIndexManager().addEntry(existing);

        MockEconomyService eco = new MockEconomyService();
        eco.canPay = false; // Si llegara a comprobar saldo, fallaría por saldo
        MockImageService imageService = new MockImageService(downloadQueue);
        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        ImageCommand.handleImage(player, "https://example.com/two.png", null, imageService, imageStore, eco, quota);

        // Debe rechazar por cupo lleno
        assertEquals(1, messages.size());
        assertEquals(Messages.quotaFullRequest(1, 1), messages.getFirst());

        // NO debe haber comprobado saldo ni descargado
        assertEquals(0, eco.checkCalls.get(), "No debe comprobar saldo si no hay slot");
        assertEquals(0, eco.chargeCalls.get(), "No debe cobrar");
        assertEquals(0, imageService.calls.get(), "No debe iniciar descarga");
    }

    @Test
    @DisplayName("F11.11 & Criterio 22 - Acierto de cache con demasiados frames se rechaza antes de mirar saldo")
    void testCacheHitTooManyFramesRejectsBeforeBalanceCheck() {
        UUID playerUuid = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        // Jugador con cupo de 25 frames
        Player player = createMockPlayer(playerUuid, "Bob", Set.of("framcit.imagen", "framcit.slots.5", "framcit.frames.25"), messages);

        // Imagen en caché con 80 frames
        ImageIndexEntry cached80 = new ImageIndexEntry(
            1, "https://example.com/80frames.gif", "https://example.com/80frames.gif",
            "Other", UUID.randomUUID().toString(), "2026-09-10", "GIF", 80, List.of(100), 1, false,
            new ArrayList<>(List.of(UUID.randomUUID().toString()))
        );
        imageStore.getIndexManager().addEntry(cached80);

        MockEconomyService eco = new MockEconomyService();
        MockImageService imageService = new MockImageService(downloadQueue);
        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        ImageCommand.handleImage(player, "https://example.com/80frames.gif", null, imageService, imageStore, eco, quota);

        // Rechaza por frames
        assertEquals(1, messages.size());
        assertEquals(Messages.quotaTooManyFrames(80, 25), messages.getFirst());

        // Ni se miró el saldo ni se cobró ni se descargó
        assertEquals(0, eco.checkCalls.get(), "No debe mirar saldo");
        assertEquals(0, eco.chargeCalls.get(), "No debe cobrar");
        assertEquals(0, imageService.calls.get(), "No debe descargar");
    }

    @Test
    @DisplayName("F11.13 & Criterio 17 - Slot lleno durante descarga no entrega y no cobra")
    void testSlotFilledDuringDownloadCancelsDeliveryWithoutCharging() {
        UUID playerUuid = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(playerUuid, "Charlie", Set.of("framcit.imagen", "framcit.slots.1"), messages);

        MockEconomyService eco = new MockEconomyService();
        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        // Simulamos que durante la descarga se llenó su cupo registrando otra imagen
        ImageIndexEntry concurrent = new ImageIndexEntry(
            99, "https://example.com/concurrent.png", "https://example.com/concurrent.png",
            "Charlie", playerUuid.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerUuid.toString()))
        );
        imageStore.getIndexManager().addEntry(concurrent);

        byte[] dummyFrame = new byte[16384];
        ConvertedImage img = new ConvertedImage(ImageFormat.PNG, List.of(new ConvertedFrame(dummyFrame, 0)));
        ImageProcessResult.Success result = new ImageProcessResult.Success(img, URI.create("https://example.com/pending.png"), 100);

        ImageCommand.handleResult(null, eco, quota, player, "https://example.com/pending.png", "https://example.com/pending.png", result, imageStore);

        // Debe avisar que el cupo se llenó al entregar
        assertEquals(1, messages.size());
        assertEquals(Messages.QUOTA_FULL_DELIVERY, messages.getFirst());

        // NO debe haber cobrado
        assertEquals(0, eco.chargeCalls.get(), "No debe cobrar si el cupo se llenó al entregar");
    }

    @Test
    @DisplayName("F11.15 - /imagen mias lista las imágenes del jugador y su cupo")
    void testSubcommandMias() {
        UUID playerUuid = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(playerUuid, "Dave", Set.of("framcit.imagen", "framcit.slots.3"), messages);

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        // Sin imágenes -> lista vacía
        ImageCommand.handleMias(player, null, imageStore, quota);
        assertEquals(1, messages.size());
        assertEquals(Messages.QUOTA_MY_LIST_EMPTY, messages.getFirst());

        messages.clear();

        // Con 2 imágenes
        ImageIndexEntry e1 = new ImageIndexEntry(
            1, "https://example.com/img1.png", "https://example.com/img1.png",
            "Dave", playerUuid.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerUuid.toString()))
        );
        ImageIndexEntry e2 = new ImageIndexEntry(
            2, "https://example.com/anim.gif", "https://example.com/anim.gif",
            "Dave", playerUuid.toString(), "2026-09-10", "GIF", 10, List.of(100), 1, false,
            new ArrayList<>(List.of(playerUuid.toString()))
        );
        imageStore.getIndexManager().addEntry(e1);
        imageStore.getIndexManager().addEntry(e2);

        ImageCommand.handleMias(player, null, imageStore, quota);

        assertEquals(3, messages.size());
        assertTrue(messages.get(0).contains("Cupo: 2/3"));
        assertTrue(messages.get(1).contains("#1") && messages.get(1).contains("estática"));
        assertTrue(messages.get(2).contains("#2") && messages.get(2).contains("animada (10 frames)"));
    }

    @Test
    @DisplayName("F11.16 & F11.17 & Criterios 8 y 9 - /imagen borrar libera slot y respeta posesión compartida")
    void testSubcommandBorrarSharedVsSoleOwner() throws IOException {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        List<String> messagesA = new ArrayList<>();
        Player pA = createMockPlayer(playerA, "PlayerA", Set.of("framcit.imagen", "framcit.slots.3"), messagesA);

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        // Crear archivo .fmc en disco para el mapa 1
        Path fmc1 = imageStore.getImagesFolder().resolve("1.fmc");
        Files.createDirectories(imageStore.getImagesFolder());
        Files.writeString(fmc1, "dummy fmc content");

        // Entrada compartida entre A y B
        ImageIndexEntry e1 = new ImageIndexEntry(
            1, "https://example.com/shared.png", "https://example.com/shared.png",
            "PlayerA", playerA.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerA.toString(), playerB.toString()))
        );
        imageStore.getIndexManager().addEntry(e1);

        // 1. Borrar sin confirmar muestra aviso compartido
        ImageCommand.handleBorrar(pA, 1, false, null, imageStore, quota);
        assertEquals(2, messagesA.size());
        assertEquals(Messages.quotaDeleteConfirmShared(1), messagesA.getLast());
        assertTrue(Files.exists(fmc1), "No debe borrarse de disco sin confirmar");
        assertEquals(1, quota.getUsedSlots(playerA));

        messagesA.clear();

        // 2. Borrar con confirmar siendo compartido -> libera cupo de A pero conserva archivo y entrada para B
        ImageCommand.handleBorrar(pA, 1, true, null, imageStore, quota);
        assertEquals(1, messagesA.size());
        assertEquals("✓ " + Messages.quotaDeleted(1, 0, 3), messagesA.getFirst());
        assertTrue(Files.exists(fmc1), "El archivo en disco debe conservarse porque B sigue siendo poseedor");
        assertEquals(0, quota.getUsedSlots(playerA));
        assertEquals(1, quota.getUsedSlots(playerB));
        assertNotNull(imageStore.findByMapId(1));

        // 3. Ahora B borra la imagen (es el último poseedor)
        List<String> messagesB = new ArrayList<>();
        Player pB = createMockPlayer(playerB, "PlayerB", Set.of("framcit.imagen", "framcit.slots.3"), messagesB);

        // Sin confirmar muestra aviso de último poseedor
        ImageCommand.handleBorrar(pB, 1, false, null, imageStore, quota);
        assertEquals(Messages.quotaDeleteConfirm(1), messagesB.getLast());

        messagesB.clear();

        // Con confirmar -> último poseedor: borra archivo de disco y remueve de index.json
        ImageCommand.handleBorrar(pB, 1, true, null, imageStore, quota);
        assertEquals("✓ " + Messages.quotaDeleted(1, 0, 3), messagesB.getFirst());
        assertFalse(Files.exists(fmc1), "El archivo .fmc debe eliminarse del disco al borrarlo el último poseedor");
        assertNull(imageStore.findByMapId(1), "La entrada debe removerse de index.json");
    }

    @Test
    @DisplayName("/imagen mias pagina las imágenes propias con ITEMS_PER_PAGE")
    void testMiasPagination() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(owner, "Pages", Set.of("framcit.imagen", "framcit.slots.10"), messages);
        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());
        for (int id = 1; id <= ImageCommand.ITEMS_PER_PAGE + 1; id++) {
            imageStore.getIndexManager().addEntry(new ImageIndexEntry(id, "https://example.com/" + id + ".png",
                "https://example.com/" + id + ".png", "Pages", owner.toString(), "2026-10-23", "PNG", 1,
                List.of(0), 1, false, new ArrayList<>(List.of(owner.toString()))));
        }
        ImageCommand.handleMias(player, null, imageStore, quota, 1);
        assertEquals(1 + ImageCommand.ITEMS_PER_PAGE + 1, messages.size());
        assertTrue(messages.getFirst().contains("1/2"));
        assertTrue(messages.getLast().contains("/imagen mias 2"));

        messages.clear();
        ImageCommand.handleMias(player, null, imageStore, quota, 2);
        assertEquals(2, messages.size());
        assertTrue(messages.get(1).contains("#6"));
    }

    @Test
    @DisplayName("/imagen obtener rechaza IDs que el jugador no posee")
    void testObtenerRejectsNonOwner() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "Visitor", Set.of("framcit.admin"), messages);
        imageStore.getIndexManager().addEntry(new ImageIndexEntry(77, "https://example.com/77.png",
            "https://example.com/77.png", "Owner", owner.toString(), "2026-10-23", "PNG", 1,
            List.of(0), 1, false, new ArrayList<>(List.of(owner.toString()))));
        ImageCommand.createCommandNode(null, null, imageStore); // verifica la construcción del árbol con las rutas nuevas
        // El rechazo por propiedad ocurre antes de consultar Bukkit para el MapView.
        assertEquals(1, ImageCommand.handleObtener(player, 77, null, imageStore, null, null));
        assertEquals(Messages.quotaIdNotOwned(77), messages.getFirst());
    }

    @Test
    @DisplayName("/imagen obtener con solo framcit.admin entrega el mapa propio no huérfano y conserva el chequeo de economía")
    void testAdminOnlyCanObtenerOwnedNonOrphanMap() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(owner, "AdminOwner", Set.of("framcit.admin"), messages);
        ImageIndexEntry entry = new ImageIndexEntry(781, "https://example.com/owned.png",
            "https://example.com/owned.png", "AdminOwner", owner.toString(), "2026-10-23", "PNG", 1,
            List.of(0), 1, false, List.of(owner.toString()));
        imageStore.getIndexManager().addEntry(entry);

        MockEconomyService economy = new MockEconomyService();
        MapView mapView = createMockMapView(781);
        AtomicInteger deliveries = new AtomicInteger();
        int result = ImageCommand.handleObtener(player, 781, null, imageStore, economy, null,
            mapId -> mapId == 781 ? mapView : null,
            (plugin, usedEconomy, quota, recipient, rawUrl, cached, resolvedMap, store) -> {
                assertSame(player, recipient);
                assertSame(entry, cached);
                assertSame(mapView, resolvedMap);
                assertSame(imageStore, store);
                deliveries.incrementAndGet();
            });

        assertEquals(1, result);
        assertEquals(1, economy.checkCalls.get(), "Obtener debe conservar el chequeo de saldo");
        assertEquals(1, deliveries.get(), "El mapa propio, existente y no huérfano debe llegar a la entrega");
        assertTrue(messages.isEmpty());
    }

    @Test
    @DisplayName("/imagen obtener sin framcit.imagen ni framcit.admin rechaza antes de buscar el mapa")
    void testObtenerWithoutPermissionsRejectsBeforeLookup() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(owner, "NoPermission", Set.of(), messages);
        AtomicInteger lookups = new AtomicInteger();
        MockEconomyService economy = new MockEconomyService();

        ImageCommand.handleObtener(player, 782, null, imageStore, economy, null,
            id -> {
                lookups.incrementAndGet();
                return createMockMapView(id);
            },
            (plugin, usedEconomy, quota, recipient, rawUrl, cached, mapView, store) -> fail("No debe entregar"));

        assertEquals(List.of(Messages.NO_PERMISSION), messages);
        assertEquals(0, lookups.get());
        assertEquals(0, economy.checkCalls.get());
    }

    @Test
    @DisplayName("/imagen obtener rechaza un id inexistente antes de consultar economía")
    void testObtenerRejectsMissingId() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(owner, "Owner", Set.of("framcit.imagen"), messages);
        MockEconomyService economy = new MockEconomyService();
        AtomicInteger deliveries = new AtomicInteger();

        ImageCommand.handleObtener(player, 783, null, imageStore, economy, null,
            ImageCommandQuotaTest::createMockMapView,
            (plugin, usedEconomy, quota, recipient, rawUrl, cached, mapView, store) -> deliveries.incrementAndGet());

        assertEquals(List.of(Messages.quotaIdNotFound(783)), messages);
        assertEquals(0, economy.checkCalls.get());
        assertEquals(0, deliveries.get());
    }

    @Test
    @DisplayName("/imagen obtener rechaza mapas huérfanos sin consultar economía ni entregar")
    void testObtenerRejectsOrphanMap() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(owner, "Owner", Set.of("framcit.imagen"), messages);
        ImageIndexEntry orphan = new ImageIndexEntry(784, "https://example.com/orphan.png",
            "https://example.com/orphan.png", "Owner", owner.toString(), "2026-10-23", "PNG", 1,
            List.of(0), 1, true, List.of(owner.toString()));
        imageStore.getIndexManager().addEntry(orphan);
        MockEconomyService economy = new MockEconomyService();
        AtomicInteger deliveries = new AtomicInteger();

        ImageCommand.handleObtener(player, 784, null, imageStore, economy, null,
            ImageCommandQuotaTest::createMockMapView,
            (plugin, usedEconomy, quota, recipient, rawUrl, cached, mapView, store) -> deliveries.incrementAndGet());

        assertEquals(List.of(Messages.quotaIdNotFound(784)), messages);
        assertEquals(0, economy.checkCalls.get());
        assertEquals(0, deliveries.get());
    }

    @Test
    @DisplayName("El permiso se revalida en el callback antes de cobrar o crear el mapa")
    void testPermissionRevokedBeforeAsyncDelivery() {
        UUID owner = UUID.randomUUID();
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(owner, "Revoked", Set.of(), messages);
        MockEconomyService economy = new MockEconomyService();
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG,
            List.of(new ConvertedFrame(new byte[16384], 0)));
        ImageProcessResult.Success result = new ImageProcessResult.Success(image,
            URI.create("https://example.com/revoked.png"), 100);
        ImageCommand.handleResult(null, economy, null, player, "https://example.com/revoked.png",
            "https://example.com/revoked.png", result, imageStore);
        assertEquals(List.of(Messages.NO_PERMISSION), messages);
        assertEquals(0, economy.chargeCalls.get());
        assertTrue(imageStore.getIndexManager().getAllEntries().isEmpty());
    }

    @Test
    @DisplayName("Un fallo de persistencia no cobra ni entrega una imagen")
    void testPersistenceFailureDoesNotChargeOrDeliver() throws IOException {
        Path blockedDataPath = tempDir.resolve("blocked-data");
        Files.writeString(blockedDataPath, "esto es un archivo, no un directorio");
        ImageStore failingStore = new ImageStore(blockedDataPath, downloadQueue, logger);
        List<String> messages = new ArrayList<>();
        UUID owner = UUID.randomUUID();
        Player player = createMockPlayer(owner, "NoDisk", Set.of("framcit.imagen"), messages);
        MockEconomyService economy = new MockEconomyService();
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG,
            List.of(new ConvertedFrame(new byte[16384], 0)));
        ImageProcessResult.Success result = new ImageProcessResult.Success(image,
            URI.create("https://example.com/no-disk.png"), 100);

        ImageCommand.handleResult(null, economy, null, player, "https://example.com/no-disk.png",
            "https://example.com/no-disk.png", result, failingStore, world -> createMockMapView(987));
        failingStore.awaitPersistenceIdle();

        assertEquals(0, economy.chargeCalls.get(), "No se cobra si el archivo no se pudo persistir");
        assertEquals(List.of(Messages.SAVE_FAILED), messages, "No se confirma ni entrega el mapa");
        assertNull(failingStore.findByMapId(987), "La entrada fallida debe retirarse del índice en memoria");
        failingStore.flushAndClosePersistence(5000L);
    }

    @Test
    @DisplayName("F11.19 & Criterios 11 y 12 - /imagen uso informa consumo medido real")
    void testSubcommandUsoReportsRealMeasuredMetrics() throws IOException {
        UUID playerA = UUID.randomUUID();
        List<String> adminMessages = new ArrayList<>();
        CommandSender admin = createMockAdmin("Admin", adminMessages);

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        // Crear 2 archivos .fmc en disco con tamaños conocidos
        Files.createDirectories(imageStore.getImagesFolder());
        Path fmc1 = imageStore.getImagesFolder().resolve("10.fmc");
        Path fmc2 = imageStore.getImagesFolder().resolve("20.fmc");
        Files.write(fmc1, new byte[5000]);
        Files.write(fmc2, new byte[7000]);

        ImageIndexEntry e1 = new ImageIndexEntry(
            10, "https://example.com/10.png", "https://example.com/10.png",
            "PlayerA", playerA.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerA.toString()))
        );
        ImageIndexEntry e2 = new ImageIndexEntry(
            20, "https://example.com/20.png", "https://example.com/20.png",
            "PlayerB", UUID.randomUUID().toString(), "2026-09-10", "GIF", 5, List.of(100), 1, false,
            new ArrayList<>(List.of(UUID.randomUUID().toString()))
        );
        imageStore.getIndexManager().addEntry(e1);
        imageStore.getIndexManager().addEntry(e2);

        // Cargar mapa 10 en cache LRU
        byte[] dummy = new byte[16384];
        ConvertedImage img10 = new ConvertedImage(ImageFormat.PNG, List.of(new ConvertedFrame(dummy, 0)));
        imageStore.getLruCache().put(10, img10);

        // 1. /imagen uso global
        ImageCommand.handleUso(admin, null, null, imageStore, quota);
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("Global")));
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("Imágenes: 2") && m.contains("Frames totales: 6")));
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("12000 bytes"))); // 5000 + 7000 medidos
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("1 imágenes cargadas (1 solo en disco)")));

        adminMessages.clear();

        // 2. /imagen uso PlayerA
        ImageCommand.handleUso(admin, "PlayerA", null, imageStore, quota);
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("PlayerA")));
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("Imágenes: 1") && m.contains("Frames totales: 1")));
        assertTrue(adminMessages.stream().anyMatch(m -> m.contains("5000 bytes")));
    }

    @Test
    @DisplayName("F11.20 & Criterios 13 y 14 - /imagen purgar-jugador respeta posesión compartida y confirma")
    void testSubcommandPurgarJugador() throws IOException {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();
        List<String> adminMessages = new ArrayList<>();
        CommandSender admin = createMockAdmin("Admin", adminMessages);

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        Files.createDirectories(imageStore.getImagesFolder());
        Path fmcA = imageStore.getImagesFolder().resolve("100.fmc");
        Path fmcShared = imageStore.getImagesFolder().resolve("200.fmc");
        Files.write(fmcA, new byte[4000]);
        Files.write(fmcShared, new byte[6000]);

        // 100 es exclusiva de A
        ImageIndexEntry e100 = new ImageIndexEntry(
            100, "https://example.com/100.png", "https://example.com/100.png",
            "TargetUser", playerA.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerA.toString()))
        );
        // 200 es compartida entre A y B
        ImageIndexEntry e200 = new ImageIndexEntry(
            200, "https://example.com/200.png", "https://example.com/200.png",
            "TargetUser", playerA.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerA.toString(), playerB.toString()))
        );
        imageStore.getIndexManager().addEntry(e100);
        imageStore.getIndexManager().addEntry(e200);

        // 1. Sin confirmar: no borra nada y muestra preview
        ImageCommand.handlePurgarJugador(admin, "TargetUser", false, null, imageStore, quota);
        assertEquals(1, adminMessages.size());
        assertTrue(adminMessages.getFirst().contains("posee 2 imágenes"));
        assertTrue(adminMessages.getFirst().contains("Se borrarían 1 archivos"));
        assertTrue(adminMessages.getFirst().contains("1 se conservarán"));
        assertTrue(Files.exists(fmcA));
        assertTrue(Files.exists(fmcShared));

        adminMessages.clear();

        // 2. Con confirmar: purga exclusiva, conserva compartida
        ImageCommand.handlePurgarJugador(admin, "TargetUser", true, null, imageStore, quota);
        assertEquals(1, adminMessages.size());
        assertTrue(adminMessages.getFirst().contains("Se purgaron 2 imágenes"));
        assertFalse(Files.exists(fmcA), "La exclusiva debe borrarse del disco");
        assertTrue(Files.exists(fmcShared), "La compartida debe conservarse en disco para el otro jugador");
        assertNull(imageStore.findByMapId(100));
        assertNotNull(imageStore.findByMapId(200));
        assertEquals(0, quota.getUsedSlots(playerA));
        assertEquals(1, quota.getUsedSlots(playerB));
    }
}
