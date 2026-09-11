package com.dasannn.framcit.quota;

import com.dasannn.framcit.config.FraMCitConfig;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class QuotaServiceTest {

    @TempDir
    Path tempDir;

    private DownloadQueue downloadQueue;
    private ImageStore imageStore;
    private final Logger logger = Logger.getLogger("QuotaServiceTest");

    @BeforeEach
    void setUp() {
        this.downloadQueue = new DownloadQueue(2, 5);
        this.imageStore = new ImageStore(tempDir, downloadQueue, logger);
        this.imageStore.loadIndex();
    }

    @AfterEach
    void tearDown() {
        if (downloadQueue != null) {
            downloadQueue.close();
        }
    }

    private Player createPlayer(UUID uuid, String name, Set<String> permissions) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getUniqueId".equals(mName)) return uuid;
                if ("getName".equals(mName)) return name;
                if ("isOnline".equals(mName)) return true;
                if ("isOp".equals(mName)) return false;
                if ("hasPermission".equals(mName) && args != null && args.length > 0) {
                    return permissions.contains((String) args[0]);
                }
                return null;
            }
        );
    }

    @Test
    @DisplayName("F11.5 & F11.7 - Conteo de slots usados y soporte de posesión compartida")
    void testUsedSlotsAndSharedOwnership() {
        UUID playerA = UUID.randomUUID();
        UUID playerB = UUID.randomUUID();

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        assertEquals(0, quota.getUsedSlots(playerA));
        assertEquals(0, quota.getUsedSlots(playerB));

        // Entrada 1 poseída solo por A
        ImageIndexEntry e1 = new ImageIndexEntry(
            1, "https://example.com/1.png", "https://example.com/1.png",
            "A", playerA.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerA.toString()))
        );
        imageStore.getIndexManager().addEntry(e1);

        // Entrada 2 compartida por A y B
        ImageIndexEntry e2 = new ImageIndexEntry(
            2, "https://example.com/shared.png", "https://example.com/shared.png",
            "A", playerA.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerA.toString(), playerB.toString()))
        );
        imageStore.getIndexManager().addEntry(e2);

        // A posee 2 slots (1 exclusiva + 1 compartida)
        assertEquals(2, quota.getUsedSlots(playerA));
        // B posee 1 slot (1 compartida)
        assertEquals(1, quota.getUsedSlots(playerB));

        assertTrue(quota.hasOtherOwners(2, playerA));
        assertTrue(quota.hasOtherOwners(2, playerB));
        assertFalse(quota.hasOtherOwners(1, playerA));
    }

    @Test
    @DisplayName("F11.7 & Spec 003 2.3 - Regla 2.3: Pedir URL que ya posees no consume otro slot")
    void testRule23UrlAlreadyOwnedDoesNotConsumeSlot() {
        UUID playerUuid = UUID.randomUUID();
        Player player = createPlayer(playerUuid, "User1", Set.of("framcit.slots.1"));

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        // Tiene 1 slot maximo
        assertEquals(1, quota.getMaxSlots(player));

        // Crea una imagen
        ImageIndexEntry e1 = new ImageIndexEntry(
            1, "https://example.com/my.png", "https://example.com/my.png",
            "User1", playerUuid.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
            new ArrayList<>(List.of(playerUuid.toString()))
        );
        imageStore.getIndexManager().addEntry(e1);

        assertEquals(1, quota.getUsedSlots(playerUuid));
        // Ya no tiene slots disponibles para una imagen nueva
        assertFalse(quota.hasAvailableSlot(player));

        // Pero SI puede adquirir la misma URL que ya posee (Regla 2.3, Criterio 7)
        assertTrue(quota.canAcquireImage(player, "https://example.com/my.png"));

        // Y NO puede adquirir una URL distinta
        assertFalse(quota.canAcquireImage(player, "https://example.com/other.png"));
    }

    @Test
    @DisplayName("F11.4 & F11.8 - getEffectiveMaxFrames nunca supera el límite global de config.yml")
    void testEffectiveMaxFramesCappedAtGlobalLimit() {
        UUID playerUuid = UUID.randomUUID();
        // Jugador con framcit.frames.500
        Player player = createPlayer(playerUuid, "VipUser", Set.of("framcit.frames.500"));

        // Config con límite global de 100 frames y maximo-nodo 500
        String yaml = """
            frames:
              por-defecto: 50
              maximo-nodo: 500
            limites:
              max-frames: 100
            """;
        FraMCitConfig config = FraMCitConfig.load(new java.io.StringReader(yaml), logger);

        QuotaService quota = new QuotaService(imageStore, config);

        // A pesar de tener nodo 500, queda topado en 100 (Constitution 6.6, Criterio 21)
        assertEquals(100, quota.getEffectiveMaxFrames(player));
    }

    @Test
    @DisplayName("F11.22 & Criterio 10 - Bajar el cupo por debajo de lo que un jugador tiene NO borra nada")
    void testLoweringQuotaDoesNotDeleteImages() {
        UUID playerUuid = UUID.randomUUID();
        // Inicialmente con framcit.slots.3
        Set<String> perms = new HashSet<>(Set.of("framcit.slots.3"));
        Player player = createPlayer(playerUuid, "Collector", perms);

        QuotaService quota = new QuotaService(imageStore, FraMCitConfig.defaults());

        // Posee 3 imágenes
        for (int i = 1; i <= 3; i++) {
            ImageIndexEntry e = new ImageIndexEntry(
                i, "https://example.com/" + i + ".png", "https://example.com/" + i + ".png",
                "Collector", playerUuid.toString(), "2026-09-10", "PNG", 1, List.of(0), 1, false,
                new ArrayList<>(List.of(playerUuid.toString()))
            );
            imageStore.getIndexManager().addEntry(e);
        }

        assertEquals(3, quota.getUsedSlots(playerUuid));
        assertEquals(3, quota.getMaxSlots(player));

        // Administrador baja el cupo a 1 (quita framcit.slots.3 y añade framcit.slots.1)
        perms.clear();
        perms.add("framcit.slots.1");

        // El cupo máximo ahora es 1
        assertEquals(1, quota.getMaxSlots(player));

        // Sigue teniendo 3 imágenes registradas (ninguna fue borrada)
        assertEquals(3, quota.getUsedSlots(playerUuid));
        assertEquals(3, imageStore.getIndexManager().size());
        assertFalse(quota.hasAvailableSlot(player));
        assertFalse(quota.canAcquireImage(player, "https://example.com/new.png"));
    }
}
