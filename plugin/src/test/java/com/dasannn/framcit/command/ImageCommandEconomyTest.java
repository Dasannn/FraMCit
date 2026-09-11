package com.dasannn.framcit.command;

import com.dasannn.framcit.economy.EconomyService;
import com.dasannn.framcit.economy.NoOpEconomyService;
import com.dasannn.framcit.economy.TestEconomy;
import com.dasannn.framcit.economy.VaultEconomyService;
import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.service.ImageProcessResult;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class ImageCommandEconomyTest {

    @TempDir
    Path tempDir;

    private DownloadQueue downloadQueue;
    private ImageStore imageStore;
    private final Logger logger = Logger.getLogger("ImageCommandEconomyTest");

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
        if (downloadQueue != null) {
            downloadQueue.close();
        }
    }

    private Player createMockPlayer(UUID uuid, String name, boolean isOp, boolean hasGratis, List<String> receivedMessages) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getUniqueId".equals(mName)) return uuid;
                if ("getName".equals(mName)) return name;
                if ("isOnline".equals(mName)) return true;
                if ("isOp".equals(mName)) return isOp;
                if ("hasPermission".equals(mName) && args != null && args.length > 0) {
                    String perm = (String) args[0];
                    if ("framcit.imagen".equals(perm)) return true;
                    if ("framcit.gratis".equals(perm)) return hasGratis;
                    if ("framcit.admin".equals(perm)) return isOp;
                    return false;
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

    static class StubImageService extends ImageService {
        private final AtomicInteger calls = new AtomicInteger(0);

        StubImageService(DownloadQueue queue) {
            super(new com.dasannn.framcit.net.Downloader(), queue);
        }

        @Override
        public CompletableFuture<ImageProcessResult> process(String rawUrl) {
            calls.incrementAndGet();
            return new CompletableFuture<>();
        }

        public int getCalls() {
            return calls.get();
        }
    }

    @Test
    @DisplayName("F10.7 & F10.16 - Saldo insuficiente antes de descargar: no se descarga nada y se avisa al jugador")
    void testInsufficientBalanceDoesNotDownload() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "JugadorPobre", false, false, messages);

        TestEconomy testEco = new TestEconomy();
        testEco.setBalance(player, 5.0);
        VaultEconomyService ecoService = new VaultEconomyService(testEco, 20.0, logger);

        StubImageService mockService = new StubImageService(downloadQueue);

        ImageCommand.handleImage(player, "https://example.com/test.png", null, mockService, imageStore, ecoService);

        // Debe haber rechazado y enviado mensaje de saldo insuficiente
        assertEquals(1, messages.size());
        assertEquals("No tienes suficiente dinero. Cuesta 20.00 $ y tienes 5.00 $.", messages.getFirst());

        // La descarga NUNCA debió encolarse ni ejecutarse
        assertEquals(0, mockService.getCalls(), "No debe descargarse nada si no hay saldo");
        assertEquals(0, testEco.getWithdrawCalls(), "No se debe cobrar");
    }

    @Test
    @DisplayName("F10.12 & F10.16 - Jugador con framcit.gratis no paga y no se le comprueba saldo")
    void testPlayerWithFramcitGratisDoesNotPayAndNoBalanceChecked() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "AdminExento", true, true, messages);

        TestEconomy testEco = new TestEconomy();
        testEco.setBalance(player, 0.0); // Saldo cero
        VaultEconomyService ecoService = new VaultEconomyService(testEco, 50.0, logger);

        StubImageService mockService = new StubImageService(downloadQueue);

        ImageCommand.handleImage(player, "https://example.com/test.png", null, mockService, imageStore, ecoService);

        // Debe haber iniciado la descarga sin quejarse del dinero
        assertEquals(1, mockService.getCalls(), "Debe encolar la descarga");
        assertEquals(1, messages.size());
        assertEquals(Messages.DOWNLOADING, messages.getFirst());

        // Ni se comprobó saldo ni se cobró
        assertEquals(0, testEco.getHasCalls(), "No se debe consultar has()");
        assertEquals(0, testEco.getWithdrawCalls(), "No se debe llamar a withdraw()");
    }

    @Test
    @DisplayName("F10.12 & Spec 002 6 - Operador sin framcit.gratis NO está exento y debe pagar")
    void testOperatorWithoutFramcitGratisMustPay() {
        List<String> messages = new ArrayList<>();
        // Operador pero sin framcit.gratis
        Player opPlayer = createMockPlayer(UUID.randomUUID(), "OpSinGratis", true, false, messages);

        TestEconomy testEco = new TestEconomy();
        testEco.setBalance(opPlayer, 2.0); // Solo tiene 2.0, el precio es 10.0
        VaultEconomyService ecoService = new VaultEconomyService(testEco, 10.0, logger);

        ImageCommand.handleImage(opPlayer, "https://example.com/test.png", null, null, imageStore, ecoService);

        assertEquals(1, messages.size());
        assertEquals("No tienes suficiente dinero. Cuesta 10.00 $ y tienes 2.00 $.", messages.getFirst());
    }

    @Test
    @DisplayName("F10.3 & F10.4 - Sin Vault o con precio 0: las imágenes son gratis y no se menciona dinero")
    void testNoVaultImagesAreFreeAndNoMoneyMentioned() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "JugadorFree", false, false, messages);

        EconomyService noOpEco = NoOpEconomyService.INSTANCE;

        StubImageService mockService = new StubImageService(downloadQueue);

        ImageCommand.handleImage(player, "https://example.com/free.png", null, mockService, imageStore, noOpEco);

        assertEquals(1, mockService.getCalls());
        assertEquals(1, messages.size());
        assertEquals(Messages.DOWNLOADING, messages.getFirst());
        assertFalse(messages.getFirst().contains("$"), "No debe mencionar dinero");
    }

    @Test
    @DisplayName("F10.9 & F10.16 - Saldo perdido entre comprobación y cobro: no se entrega el mapa")
    void testFundsLostBetweenCheckAndChargeFailsDelivery() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "JugadorGastoso", false, false, messages);

        TestEconomy testEco = new TestEconomy();
        testEco.setBalance(player, 20.0);
        VaultEconomyService ecoService = new VaultEconomyService(testEco, 20.0, logger);

        // 1. Precomprobación pasa
        assertTrue(ecoService.checkCanPay(player) instanceof com.dasannn.framcit.economy.EconomyCheckResult.Allowed);

        // 2. Jugador gasta el dinero mientras se descargaba
        testEco.setBalance(player, 5.0);

        // 3. Llega handleResult con éxito de descarga
        byte[] frameBytes = new byte[16384];
        ConvertedFrame frame = new ConvertedFrame(frameBytes, 0);
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG, List.of(frame));
        ImageProcessResult.Success success = new ImageProcessResult.Success(
            image,
            URI.create("https://example.com/test.png"),
            100
        );

        ImageCommand.handleResult(null, ecoService, player, "https://example.com/test.png", "https://example.com/test.png", success, imageStore);

        // Mensaje al jugador explicando que ya no tenía dinero y no se entregó
        assertEquals(1, messages.size());
        assertEquals("Ya no tienes suficiente dinero: la imagen no se entrego.", messages.getFirst());
        assertEquals(5.0, testEco.getBalance(player), 0.001);
    }

    @Test
    @DisplayName("F10.10 & F10.16 - Fallo del plugin de economía al cobrar: no se entrega el mapa y avisa al staff")
    void testEconomyPluginFailureDoesNotDeliverMap() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "JugadorError", false, false, messages);

        TestEconomy testEco = new TestEconomy();
        testEco.setBalance(player, 50.0);
        testEco.setThrowOnWithdraw(true); // Lanza excepción al cobrar
        VaultEconomyService ecoService = new VaultEconomyService(testEco, 20.0, logger);

        byte[] frameBytes = new byte[16384];
        ConvertedFrame frame = new ConvertedFrame(frameBytes, 0);
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG, List.of(frame));
        ImageProcessResult.Success success = new ImageProcessResult.Success(
            image,
            URI.create("https://example.com/test.png"),
            100
        );

        ImageCommand.handleResult(null, ecoService, player, "https://example.com/test.png", "https://example.com/test.png", success, imageStore);

        assertEquals(1, messages.size());
        assertEquals("No se pudo procesar el pago. Avisa al staff.", messages.getFirst());
    }

    @Test
    @DisplayName("F10.11 & F10.16 - Acierto de cache: se comprueba saldo antes y si no alcanza no se entrega")
    void testCacheHitInsufficientFundsDoesNotDeliver() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(UUID.randomUUID(), "JugadorCache", false, false, messages);

        TestEconomy testEco = new TestEconomy();
        testEco.setBalance(player, 5.0);
        VaultEconomyService ecoService = new VaultEconomyService(testEco, 20.0, logger);

        // Agregar entrada a la caché
        ImageIndexEntry entry = new ImageIndexEntry(
            1,
            "https://example.com/cached.png",
            "https://example.com/cached.png",
            "Otro",
            UUID.randomUUID().toString(),
            "2026-09-10",
            "PNG",
            1,
            List.of(0),
            1,
            false
        );
        imageStore.getIndexManager().addEntry(entry);

        ImageCommand.handleImage(player, "https://example.com/cached.png", null, null, imageStore, ecoService);

        // Debe ser rechazado antes de entregar nada
        assertEquals(1, messages.size());
        assertEquals("No tienes suficiente dinero. Cuesta 20.00 $ y tienes 5.00 $.", messages.getFirst());
        assertEquals(0, testEco.getWithdrawCalls(), "No se debe cobrar");
    }
}
