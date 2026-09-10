package com.dasannn.framcit.render;

import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AnimationSchedulerTest {

    private AnimationManager manager;
    private AtomicLong clock;
    private Map<UUID, Player> onlinePlayers;
    private Map<UUID, List<Integer>> playerSentMaps;
    private Map<Integer, MapView> mapViews;
    private Map<Integer, ImageIndexEntry> entries;

    private Player createMockPlayer(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getUniqueId".equals(mName)) return uuid;
                if ("getName".equals(mName)) return name;
                if ("isOnline".equals(mName)) return onlinePlayers.containsKey(uuid);
                if ("sendMap".equals(mName) && args != null && args.length > 0) {
                    MapView mv = (MapView) args[0];
                    playerSentMaps.computeIfAbsent(uuid, k -> new ArrayList<>()).add(mv.getId());
                    return null;
                }
                return null;
            }
        );
    }

    private MapView createMockMapView(int id) {
        return (MapView) Proxy.newProxyInstance(
            MapView.class.getClassLoader(),
            new Class<?>[]{ MapView.class },
            (proxy, method, args) -> {
                if ("getId".equals(method.getName())) return id;
                return null;
            }
        );
    }

    @BeforeEach
    void setUp() {
        clock = new AtomicLong(1000L);
        onlinePlayers = new HashMap<>();
        playerSentMaps = new HashMap<>();
        mapViews = new HashMap<>();
        entries = new HashMap<>();

        // Subclase anónima de AnimationManager para responder delays y frame count sin disco
        manager = new AnimationManager(null, null) {
            @Override
            public int getFrameCount(int mapId) {
                ImageIndexEntry entry = entries.get(mapId);
                return (entry != null) ? entry.getFrameCount() : 0;
            }

            @Override
            public int getFrameDelayMs(int mapId, int frameIndex) {
                ImageIndexEntry entry = entries.get(mapId);
                if (entry != null && entry.getDelays() != null && frameIndex < entry.getDelays().size()) {
                    return entry.getDelays().get(frameIndex);
                }
                return MIN_FRAME_DELAY_MS;
            }
        };
        manager.setTimeSupplier(clock::get);
    }

    @Test
    @DisplayName("F6.6 & F6.7 & F6.9 - El indice de frame avanza segun el retardo, hace bucle al final y no avanza si no vence")
    void testFrameAdvanceTimingAndLooping() {
        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer(playerUuid, "TestPlayer");
        onlinePlayers.put(playerUuid, player);

        // Mapa con 3 frames: delays 200ms, 300ms, 100ms
        int mapId = 1;
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 3, List.of(200, 300, 100), 1, false
        ));
        mapViews.put(mapId, createMockMapView(mapId));

        AnimationScheduler scheduler = new AnimationScheduler(
            manager,
            mapViews::get,
            onlinePlayers::get,
            clock::get,
            null
        );

        // T0 = 1000: Jugador mira el mapa. Frame actual = 0. Retardo de frame 0 es 200ms -> expira en 1200ms
        manager.updatePlayerGaze(playerUuid, mapId, UUID.randomUUID(), "world", 0, 0);
        PlayerMapState state = manager.getState(playerUuid, mapId);
        assertEquals(0, state.getFrameIndex());
        assertEquals(1200L, state.getNextAdvanceTimeMs());

        // Tick a T = 1100 (< 1200): Retardo no vencido -> NO debe avanzar
        clock.set(1100L);
        scheduler.tick(1100L);
        assertEquals(0, state.getFrameIndex(), "Retardo no vencido no debe avanzar el frame");
        assertNull(playerSentMaps.get(playerUuid), "No debe haber enviado paquete sendMap todavia");

        // Tick a T = 1200 (>= 1200): Retardo vencido -> avanza a frame 1
        clock.set(1200L);
        scheduler.tick(1200L);
        assertEquals(1, state.getFrameIndex(), "Debe avanzar al frame 1");
        assertEquals(1, playerSentMaps.get(playerUuid).size());
        // Siguiente delay es el de frame 1 (300ms) -> expira en 1200 + 300 = 1500
        assertEquals(1500L, state.getNextAdvanceTimeMs());

        // Tick a T = 1400 (< 1500): No avanza
        clock.set(1400L);
        scheduler.tick(1400L);
        assertEquals(1, state.getFrameIndex());
        assertEquals(1, playerSentMaps.get(playerUuid).size());

        // Tick a T = 1500: Avanza a frame 2
        clock.set(1500L);
        scheduler.tick(1500L);
        assertEquals(2, state.getFrameIndex(), "Debe avanzar al frame 2");
        assertEquals(2, playerSentMaps.get(playerUuid).size());
        // Siguiente delay es el de frame 2 (100ms) -> expira en 1500 + 100 = 1600
        assertEquals(1600L, state.getNextAdvanceTimeMs());

        // Tick a T = 1600: Bucle infinito! Vuelve a frame 0
        clock.set(1600L);
        scheduler.tick(1600L);
        assertEquals(0, state.getFrameIndex(), "Al llegar al final debe hacer bucle infinito y volver al frame 0");
        assertEquals(3, playerSentMaps.get(playerUuid).size());
    }

    @Test
    @DisplayName("F6.1 & F6.6 - Dos jugadores sobre el mismo mapa mantienen indices independientes")
    void testTwoPlayersIndependentFrames() {
        UUID p1Uuid = UUID.randomUUID();
        UUID p2Uuid = UUID.randomUUID();

        Player p1 = createMockPlayer(p1Uuid, "Player1");
        Player p2 = createMockPlayer(p2Uuid, "Player2");
        onlinePlayers.put(p1Uuid, p1);
        onlinePlayers.put(p2Uuid, p2);

        int mapId = 1;
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 4, List.of(100, 100, 100, 100), 1, false
        ));
        mapViews.put(mapId, createMockMapView(mapId));

        AnimationScheduler scheduler = new AnimationScheduler(
            manager, mapViews::get, onlinePlayers::get, clock::get, null
        );

        // P1 mira en T = 1000 -> expira en 1100
        clock.set(1000L);
        manager.updatePlayerGaze(p1Uuid, mapId, UUID.randomUUID(), "world", 0, 0);

        // T = 1100: P1 avanza a frame 1
        clock.set(1100L);
        scheduler.tick(1100L);
        assertEquals(1, manager.getState(p1Uuid, mapId).getFrameIndex());

        // Ahora en T = 1100, P2 comienza a mirar -> frame 0 para P2, expira en 1200
        manager.updatePlayerGaze(p2Uuid, mapId, UUID.randomUUID(), "world", 0, 0);
        assertEquals(0, manager.getState(p2Uuid, mapId).getFrameIndex());

        // T = 1200:
        // P1 estaba en 1 (expiraba en 1200) -> avanza a 2
        // P2 estaba en 0 (expiraba en 1200) -> avanza a 1
        clock.set(1200L);
        scheduler.tick(1200L);

        assertEquals(2, manager.getState(p1Uuid, mapId).getFrameIndex(), "P1 debe estar en frame 2");
        assertEquals(1, manager.getState(p2Uuid, mapId).getFrameIndex(), "P2 debe estar en frame 1");

        // P1 aparta la vista en T = 1250
        manager.clearPlayerGaze(p1Uuid);

        // T = 1300: Solo P2 avanza a frame 2; P1 se queda congelado en 2
        clock.set(1300L);
        scheduler.tick(1300L);

        assertEquals(2, manager.getState(p1Uuid, mapId).getFrameIndex(), "P1 congelado en frame 2");
        assertEquals(2, manager.getState(p2Uuid, mapId).getFrameIndex(), "P2 avanzo a frame 2");
    }

    @Test
    @DisplayName("F6.12 - Una excepcion en un mapa no impide que los demas avancen")
    void testExceptionInOneMapDoesNotBlockOthers() {
        UUID p1Uuid = UUID.randomUUID();
        UUID p2Uuid = UUID.randomUUID();

        Player p1 = createMockPlayer(p1Uuid, "Player1");
        Player p2 = createMockPlayer(p2Uuid, "Player2");
        onlinePlayers.put(p1Uuid, p1);
        onlinePlayers.put(p2Uuid, p2);

        // Mapa 1 (roto: mapLookup lanza excepcion)
        int brokenMapId = 1;
        entries.put(brokenMapId, new ImageIndexEntry(
            brokenMapId, "url", "norm", "Author", "uuid", "date", "GIF", 2, List.of(100, 100), 1, false
        ));

        // Mapa 2 (sano)
        int healthyMapId = 2;
        entries.put(healthyMapId, new ImageIndexEntry(
            healthyMapId, "url", "norm", "Author", "uuid", "date", "GIF", 2, List.of(100, 100), 1, false
        ));
        mapViews.put(healthyMapId, createMockMapView(healthyMapId));

        AnimationScheduler scheduler = new AnimationScheduler(
            manager,
            id -> {
                if (id == brokenMapId) {
                    throw new RuntimeException("Simulated exception when fetching MapView for map 1");
                }
                return mapViews.get(id);
            },
            onlinePlayers::get,
            clock::get,
            null
        );

        // Ambos jugadores miran sus mapas en T=1000
        clock.set(1000L);
        manager.updatePlayerGaze(p1Uuid, brokenMapId, UUID.randomUUID(), "world", 0, 0);
        manager.updatePlayerGaze(p2Uuid, healthyMapId, UUID.randomUUID(), "world", 0, 0);

        // T = 1100: Scheduler corre
        clock.set(1100L);
        assertDoesNotThrow(() -> scheduler.tick(1100L), "El ciclo del scheduler no debe lanzar excepcion");

        // Mapa 1 debe haberse desactivado
        assertTrue(manager.isMapDisabled(brokenMapId), "El mapa roto debe quedar desactivado");
        assertFalse(manager.isMapDisabled(healthyMapId), "El mapa sano no debe ser desactivado");

        // El mapa 2 del jugador 2 DEBE haber avanzado normalmente
        assertEquals(1, manager.getState(p2Uuid, healthyMapId).getFrameIndex());
        assertEquals(1, playerSentMaps.get(p2Uuid).size());
    }
}
