package com.dasannn.framcit.render;

import com.dasannn.framcit.store.ImageIndexEntry;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pruebas unitarias para el contador de mirada prolongada y pausa de animación (F12.1 - F12.10).
 */
class GazeTrackerPauseTest {

    private AnimationManager manager;
    private Map<Integer, ImageIndexEntry> entries;
    private AtomicLong simulatedTick;
    private AtomicLong simulatedClockMs;
    private Map<UUID, Player> onlinePlayers;
    private Map<UUID, List<Integer>> playerSentMaps;
    private Map<Integer, MapView> mapViews;

    @BeforeEach
    void setUp() {
        entries = new HashMap<>();
        simulatedTick = new AtomicLong(100L);
        simulatedClockMs = new AtomicLong(1000L);
        onlinePlayers = new HashMap<>();
        playerSentMaps = new HashMap<>();
        mapViews = new HashMap<>();

        manager = new AnimationManager(null, null) {
            @Override
            public int getFrameCount(int mapId) {
                ImageIndexEntry e = entries.get(mapId);
                return (e != null) ? e.getFrameCount() : 0;
            }

            @Override
            public boolean isAnimated(int mapId) {
                return getFrameCount(mapId) > 1;
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
        manager.setTimeSupplier(simulatedClockMs::get);
    }

    private Player createMockPlayer(UUID uuid, float pitch) {
        World world = (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{ World.class },
            (proxy, method, args) -> {
                if ("getName".equals(method.getName())) return "world";
                return null;
            }
        );

        Location loc = new Location(world, 0, 64, 0, 0, pitch);

        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getUniqueId".equals(name)) return uuid;
                if ("isOnline".equals(name)) return onlinePlayers.containsKey(uuid);
                if ("getLocation".equals(name)) return loc;
                if ("getEyeLocation".equals(name)) return loc;
                if ("getWorld".equals(name)) return world;
                if ("sendMap".equals(name) && args != null && args.length > 0) {
                    MapView mv = (MapView) args[0];
                    playerSentMaps.computeIfAbsent(uuid, k -> new ArrayList<>()).add(mv.getId());
                    return null;
                }
                return null;
            }
        );
    }

    private ItemFrame createMockItemFrame(UUID uuid, int chunkX, int chunkZ) {
        World world = (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{ World.class },
            (proxy, method, args) -> {
                if ("getName".equals(method.getName())) return "world";
                return null;
            }
        );

        Chunk chunk = (Chunk) Proxy.newProxyInstance(
            Chunk.class.getClassLoader(),
            new Class<?>[]{ Chunk.class },
            (proxy, method, args) -> {
                if ("getX".equals(method.getName())) return chunkX;
                if ("getZ".equals(method.getName())) return chunkZ;
                if ("getWorld".equals(method.getName())) return world;
                return null;
            }
        );

        Location loc = new Location(world, chunkX * 16, 64, chunkZ * 16);

        return (ItemFrame) Proxy.newProxyInstance(
            ItemFrame.class.getClassLoader(),
            new Class<?>[]{ ItemFrame.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getUniqueId".equals(name)) return uuid;
                if ("getLocation".equals(name)) return loc;
                if ("getWorld".equals(name)) return world;
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

    @Test
    @DisplayName("F12.2 & F12.3 - Guarda tick de inicio, no lo toca mientras siga mirando y lo borra al dejar de mirar")
    void testGazeStartTickPreservedWhileLookingAndDeletedOnLookAway() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 10;
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 4, List.of(100, 100, 100, 100), 1, false
        ));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player = createMockPlayer(playerUuid, 0.0f);
        onlinePlayers.put(playerUuid, player);

        AtomicReference<ItemFrame> aimedFrame = new AtomicReference<>(frame);
        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> {
            ItemFrame target = aimedFrame.get();
            return (target != null) ? new RayTraceResult(new Vector(0, 0, 0), target) : null;
        };

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return (f != null) ? mapId : -1; }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(manager, null, () -> List.of(player), rayTracer, inspector);
        tracker.setTickSupplier(simulatedTick::get);
        tracker.setPauseThresholdTicks(100L); // 100 ticks = 5 segundos para la prueba

        // Tick 100: Empieza a mirar
        simulatedTick.set(100L);
        tracker.run();

        assertEquals(100L, tracker.getGazeStartTick(playerUuid, mapId, frameUuid), "El tick inicial debe registrarse como 100");
        assertFalse(tracker.isGazePaused(playerUuid, mapId, frameUuid), "No debe estar pausado al inicio");

        // Tick 150: Sigue mirando el mismo marco -> el start tick NO debe modificarse
        simulatedTick.set(150L);
        tracker.run();
        assertEquals(100L, tracker.getGazeStartTick(playerUuid, mapId, frameUuid), "Mientras siga mirando el tick inicial no se toca");
        assertEquals(50L, tracker.getGazeTicks(playerUuid, mapId, frameUuid));
        assertFalse(tracker.isGazePaused(playerUuid, mapId, frameUuid));

        // Tick 180: Aparta la vista (mira al aire / nada)
        aimedFrame.set(null);
        simulatedTick.set(180L);
        tracker.run();

        assertNull(tracker.getGazeStartTick(playerUuid, mapId, frameUuid), "Al dejar de mirar la entrada debe borrarse");
        assertEquals(0L, tracker.getGazeTicks(playerUuid, mapId, frameUuid));
        assertFalse(tracker.isGazePaused(playerUuid, mapId, frameUuid));
    }

    @Test
    @DisplayName("F12.3 & F12.10 - El contador no acumula: volver a mirar arranca desde cero con el nuevo tick")
    void testGazeCounterDoesNotAccumulate() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 10;
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 4, List.of(100, 100, 100, 100), 1, false
        ));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player = createMockPlayer(playerUuid, 0.0f);
        onlinePlayers.put(playerUuid, player);

        AtomicReference<ItemFrame> aimedFrame = new AtomicReference<>(frame);
        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> {
            ItemFrame target = aimedFrame.get();
            return (target != null) ? new RayTraceResult(new Vector(0, 0, 0), target) : null;
        };

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return (f != null) ? mapId : -1; }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(manager, null, () -> List.of(player), rayTracer, inspector);
        tracker.setTickSupplier(simulatedTick::get);
        tracker.setPauseThresholdTicks(100L);

        // 1. Mira de tick 100 a tick 180 (80 ticks de mirada seguida)
        simulatedTick.set(100L);
        tracker.run();
        simulatedTick.set(180L);
        tracker.run();
        assertEquals(80L, tracker.getGazeTicks(playerUuid, mapId, frameUuid));

        // 2. Aparta la vista en tick 185
        aimedFrame.set(null);
        simulatedTick.set(185L);
        tracker.run();
        assertNull(tracker.getGazeStartTick(playerUuid, mapId, frameUuid));

        // 3. Vuelve a mirar en tick 250
        aimedFrame.set(frame);
        simulatedTick.set(250L);
        tracker.run();

        // Debe registrar 250 como nuevo inicio, SIN acumular los 80 ticks anteriores
        assertEquals(250L, tracker.getGazeStartTick(playerUuid, mapId, frameUuid));
        assertEquals(0L, tracker.getGazeTicks(playerUuid, mapId, frameUuid), "No debe acumular el tiempo previo");

        // Avanza 30 ticks (a tick 280): tiempo transcurrido es 30, NO 80 + 30 = 110!
        simulatedTick.set(280L);
        tracker.run();
        assertEquals(30L, tracker.getGazeTicks(playerUuid, mapId, frameUuid));
        assertFalse(tracker.isGazePaused(playerUuid, mapId, frameUuid), "No debe pausarse porque 30 < 100 ticks");
    }

    @Test
    @DisplayName("F12.1 & F12.10 - Umbral en 0 desactiva la pausa por mirada permanentemente")
    void testThresholdZeroDisablesPause() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 10;
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 4, List.of(100, 100, 100, 100), 1, false
        ));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player = createMockPlayer(playerUuid, 0.0f);
        onlinePlayers.put(playerUuid, player);

        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> new RayTraceResult(new Vector(0, 0, 0), frame);
        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return mapId; }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(manager, null, () -> List.of(player), rayTracer, inspector);
        tracker.setTickSupplier(simulatedTick::get);
        tracker.setPauseThresholdMinutes(0); // Desactivado
        assertEquals(0L, tracker.getPauseThresholdTicks());

        simulatedTick.set(100L);
        tracker.run();

        // Avanzar 1.000.000 de ticks
        simulatedTick.set(1_000_100L);
        assertFalse(tracker.isGazePaused(playerUuid, mapId, frameUuid), "Con umbral 0 la pausa nunca debe activarse");
        assertFalse(tracker.isGazePaused(playerUuid, mapId));
    }

    @Test
    @DisplayName("F12.3 - Mirar un marco A y luego cambiar al marco B reinicia el contador de A y arranca B de cero")
    void testSwitchingBetweenFramesResetsOldAndStartsNew() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameA = UUID.randomUUID();
        UUID frameB = UUID.randomUUID();
        int mapA = 10;
        int mapB = 20;

        entries.put(mapA, new ImageIndexEntry(mapA, "uA", "nA", "Aut", "uuid", "d", "GIF", 2, List.of(100, 100), 1, false));
        entries.put(mapB, new ImageIndexEntry(mapB, "uB", "nB", "Aut", "uuid", "d", "GIF", 2, List.of(100, 100), 1, false));

        ItemFrame itemFrameA = createMockItemFrame(frameA, 0, 0);
        ItemFrame itemFrameB = createMockItemFrame(frameB, 1, 1);
        Player player = createMockPlayer(playerUuid, 0.0f);
        onlinePlayers.put(playerUuid, player);

        AtomicReference<ItemFrame> activeFrame = new AtomicReference<>(itemFrameA);
        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> new RayTraceResult(new Vector(0, 0, 0), activeFrame.get());

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) {
                if (f == itemFrameA) return mapA;
                if (f == itemFrameB) return mapB;
                return -1;
            }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(manager, null, () -> List.of(player), rayTracer, inspector);
        tracker.setTickSupplier(simulatedTick::get);
        tracker.setPauseThresholdTicks(50L);

        // Tick 100: Mira Marco A
        simulatedTick.set(100L);
        tracker.run();
        assertEquals(100L, tracker.getGazeStartTick(playerUuid, mapA, frameA));
        assertNull(tracker.getGazeStartTick(playerUuid, mapB, frameB));

        // Tick 130: Cambia la mirada a Marco B
        activeFrame.set(itemFrameB);
        simulatedTick.set(130L);
        tracker.run();

        assertNull(tracker.getGazeStartTick(playerUuid, mapA, frameA), "Marco A debe haberse borrado");
        assertEquals(130L, tracker.getGazeStartTick(playerUuid, mapB, frameB), "Marco B debe arrancar en tick 130");

        // Tick 160: Vuelve a mirar Marco A
        activeFrame.set(itemFrameA);
        simulatedTick.set(160L);
        tracker.run();

        assertEquals(160L, tracker.getGazeStartTick(playerUuid, mapA, frameA), "Marco A debe arrancar de cero en tick 160");
        assertNull(tracker.getGazeStartTick(playerUuid, mapB, frameB), "Marco B debe haberse borrado");
    }

    @Test
    @DisplayName("F12.4 - Desconexión del jugador (onPlayerQuit) limpia por completo su estado de mirada")
    void testPlayerQuitPurgesGazeState() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 15;
        entries.put(mapId, new ImageIndexEntry(mapId, "url", "norm", "Author", "uuid", "date", "GIF", 2, List.of(100, 100), 1, false));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player = createMockPlayer(playerUuid, 0.0f);
        onlinePlayers.put(playerUuid, player);

        GazeTracker tracker = new GazeTracker(
            manager, null, () -> List.of(player),
            (p, dist) -> new RayTraceResult(new Vector(0, 0, 0), frame),
            new GazeTracker.MapInspector() {
                @Override public int getFrameMapId(ItemFrame f) { return mapId; }
                @Override public int getHandMapId(Player p) { return -1; }
            }
        );
        tracker.setTickSupplier(simulatedTick::get);

        simulatedTick.set(100L);
        tracker.run();
        assertNotNull(tracker.getGazeStartTick(playerUuid, mapId, frameUuid));

        // Jugador se desconecta
        tracker.onPlayerQuit(playerUuid);
        assertNull(tracker.getGazeStartTick(playerUuid, mapId, frameUuid), "El estado de mirada debe limpiarse al desconectarse");
    }

    @Test
    @DisplayName("F12.8 - Mapa en mano respeta las mismas reglas de mirada continua y pausa")
    void testHeldMapGazePauseBehavior() {
        UUID playerUuid = UUID.randomUUID();
        int mapId = 30;
        entries.put(mapId, new ImageIndexEntry(mapId, "url", "norm", "Author", "uuid", "date", "GIF", 2, List.of(100, 100), 1, false));

        Player playerLookingNormal = createMockPlayer(playerUuid, 0.0f);
        Player playerLookingUp = createMockPlayer(playerUuid, -60.0f); // cielo: desactiva mapa en mano
        AtomicReference<Player> currentPlayer = new AtomicReference<>(playerLookingNormal);
        onlinePlayers.put(playerUuid, playerLookingNormal);

        GazeTracker tracker = new GazeTracker(
            manager, null, () -> List.of(currentPlayer.get()),
            (p, dist) -> null,
            new GazeTracker.MapInspector() {
                @Override public int getFrameMapId(ItemFrame f) { return -1; }
                @Override public int getHandMapId(Player p) { return mapId; }
            }
        );
        tracker.setTickSupplier(simulatedTick::get);
        tracker.setPauseThresholdTicks(50L);

        // Tick 100: Sostiene mapa y mira al frente
        simulatedTick.set(100L);
        tracker.run();
        assertEquals(100L, tracker.getGazeStartTick(playerUuid, mapId, null));
        assertFalse(tracker.isGazePaused(playerUuid, mapId, null));

        // Tick 150: Llega al umbral (50 ticks seguidos) -> Se pausa
        simulatedTick.set(150L);
        tracker.run();
        assertTrue(tracker.isGazePaused(playerUuid, mapId, null), "Debe pausarse tras alcanzar el umbral");
        assertTrue(tracker.isGazePaused(playerUuid, mapId));

        // Mira al cielo (pitch < -45) -> deja de mirar el mapa en mano
        currentPlayer.set(playerLookingUp);
        simulatedTick.set(155L);
        tracker.run();
        assertNull(tracker.getGazeStartTick(playerUuid, mapId, null), "Al mirar al cielo debe borrarse la entrada");
        assertFalse(tracker.isGazePaused(playerUuid, mapId, null));

        // Vuelve a mirar al frente en tick 200 -> nuevo inicio, no pausado
        currentPlayer.set(playerLookingNormal);
        simulatedTick.set(200L);
        tracker.run();
        assertEquals(200L, tracker.getGazeStartTick(playerUuid, mapId, null));
        assertFalse(tracker.isGazePaused(playerUuid, mapId, null));
    }

    @Test
    @DisplayName("F12.5, F12.6, F12.7 & F12.9 - AnimationScheduler congela frame en pausa, omite sendMap y reanuda al apartar y volver a mirar")
    void testAnimationSchedulerPausesAndResumesOnLookAway() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 100;

        // Mapa de 3 frames: delay 100ms cada uno
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 3, List.of(100, 100, 100), 1, false
        ));
        mapViews.put(mapId, createMockMapView(mapId));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player = createMockPlayer(playerUuid, 0.0f);
        onlinePlayers.put(playerUuid, player);

        AtomicReference<ItemFrame> aimedTarget = new AtomicReference<>(frame);
        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> {
            ItemFrame target = aimedTarget.get();
            return (target != null) ? new RayTraceResult(new Vector(0, 0, 0), target) : null;
        };

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return (f != null) ? mapId : -1; }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(manager, null, () -> List.of(player), rayTracer, inspector);
        tracker.setTickSupplier(simulatedTick::get);
        // Umbral de 60 ticks (3 segundos)
        tracker.setPauseThresholdTicks(60L);

        AnimationScheduler scheduler = new AnimationScheduler(
            manager,
            mapViews::get,
            onlinePlayers::get,
            simulatedClockMs::get,
            tracker::isGazePaused,
            null
        );

        // T0 = Clock 1000ms, Tick 0: Empieza a mirar
        simulatedTick.set(0L);
        simulatedClockMs.set(1000L);
        tracker.run();

        PlayerMapState state = manager.getState(playerUuid, mapId);
        assertNotNull(state);
        assertEquals(0, state.getFrameIndex());

        // Tick 2 (1100ms): Avanza a frame 1
        simulatedTick.set(2L);
        simulatedClockMs.set(1100L);
        scheduler.tick(1100L);
        assertEquals(1, state.getFrameIndex(), "Avanza a frame 1");
        assertEquals(1, playerSentMaps.get(playerUuid).size());

        // Tick 4 (1200ms): Avanza a frame 2
        simulatedTick.set(4L);
        simulatedClockMs.set(1200L);
        scheduler.tick(1200L);
        assertEquals(2, state.getFrameIndex(), "Avanza a frame 2");
        assertEquals(2, playerSentMaps.get(playerUuid).size());

        // Avanzar el tiempo a Tick 60 (3000ms, supera umbral de 60 ticks)
        simulatedTick.set(60L);
        simulatedClockMs.set(4000L);
        tracker.run();
        assertTrue(tracker.isGazePaused(playerUuid, mapId, frameUuid), "Debe estar en pausa por mirada prolongada");

        // Scheduler corre mientras está pausado
        scheduler.tick(4000L);
        assertEquals(2, state.getFrameIndex(), "El frame DEBE quedarse congelado en el frame 2");
        assertEquals(2, playerSentMaps.get(playerUuid).size(), "NO debe haber enviado ningún paquete sendMap adicional");

        // Pasan más ticks con el jugador aún mirando (Tick 70, Clock 4500ms)
        simulatedTick.set(70L);
        simulatedClockMs.set(4500L);
        scheduler.tick(4500L);
        assertEquals(2, state.getFrameIndex(), "Permanece congelado");
        assertEquals(2, playerSentMaps.get(playerUuid).size(), "Sigue sin enviar paquetes");

        // F12.7: Apartar la vista y volver a mirar reanuda la animación
        // 1. Aparta la vista en Tick 80
        aimedTarget.set(null);
        simulatedTick.set(80L);
        simulatedClockMs.set(5000L);
        tracker.run();
        assertFalse(state.isGazing(), "Al apartar la vista ya no está mirando");
        assertEquals(2, state.getFrameIndex(), "El frame sigue conservado en 2 al apartar la vista");

        // 2. Vuelve a mirar en Tick 90
        aimedTarget.set(frame);
        simulatedTick.set(90L);
        simulatedClockMs.set(5500L);
        tracker.run();
        assertTrue(state.isGazing(), "Vuelve a estar mirando");
        assertFalse(tracker.isGazePaused(playerUuid, mapId, frameUuid), "El contador se reinició, ya no está pausado");
        assertEquals(2, state.getFrameIndex(), "Reanuda desde el frame 2 donde se quedó congelado");

        // 3. Scheduler corre tras reanudar: retardo vence en 5500 + 100 = 5600ms
        simulatedClockMs.set(5600L);
        scheduler.tick(5600L);
        assertEquals(0, state.getFrameIndex(), "Avanza al frame 0 (bucle continuo)");
        assertEquals(3, playerSentMaps.get(playerUuid).size(), "Vuelve a enviar paquetes sendMap");
    }

    @Test
    @DisplayName("F12.5 - Dos jugadores mirando el mismo mapa: uno se pausa y el otro sigue animando independientemente")
    void testTwoPlayersOnePausesOtherContinues() {
        UUID p1 = UUID.randomUUID();
        UUID p2 = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 50;

        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 4, List.of(100, 100, 100, 100), 1, false
        ));
        mapViews.put(mapId, createMockMapView(mapId));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player1 = createMockPlayer(p1, 0.0f);
        Player player2 = createMockPlayer(p2, 0.0f);
        onlinePlayers.put(p1, player1);
        onlinePlayers.put(p2, player2);

        GazeTracker tracker = new GazeTracker(
            manager, null, () -> List.of(player1, player2),
            (p, dist) -> new RayTraceResult(new Vector(0, 0, 0), frame),
            new GazeTracker.MapInspector() {
                @Override public int getFrameMapId(ItemFrame f) { return mapId; }
                @Override public int getHandMapId(Player p) { return -1; }
            }
        );
        tracker.setTickSupplier(simulatedTick::get);
        tracker.setPauseThresholdTicks(50L);

        AnimationScheduler scheduler = new AnimationScheduler(
            manager,
            mapViews::get,
            onlinePlayers::get,
            simulatedClockMs::get,
            tracker::isGazePaused,
            null
        );

        // P1 empieza a mirar en Tick 0
        simulatedTick.set(0L);
        simulatedClockMs.set(1000L);
        tracker.trackPlayer(player1);

        // Tick 10 (1100ms): P1 avanza a frame 1
        simulatedTick.set(10L);
        simulatedClockMs.set(1100L);
        scheduler.tick(1100L);
        assertEquals(1, manager.getState(p1, mapId).getFrameIndex());

        // P1 llega a tick 50 (supera umbral de 50 ticks) -> P1 pausado
        simulatedTick.set(50L);
        simulatedClockMs.set(3500L);
        tracker.trackPlayer(player1);
        assertTrue(tracker.isGazePaused(p1, mapId, frameUuid));

        // Ahora P2 empieza a mirar en Tick 50!
        tracker.trackPlayer(player2);
        assertEquals(50L, tracker.getGazeStartTick(p2, mapId, frameUuid));
        assertFalse(tracker.isGazePaused(p2, mapId, frameUuid), "P2 recién empieza, no debe estar pausado");

        // Tick 52 (3600ms): Scheduler corre
        simulatedTick.set(52L);
        simulatedClockMs.set(3600L);
        scheduler.tick(3600L);

        // P1 está pausado: sigue en frame 1
        assertEquals(1, manager.getState(p1, mapId).getFrameIndex(), "P1 debe seguir congelado en frame 1");

        // P2 no está pausado: avanza a frame 1
        assertEquals(1, manager.getState(p2, mapId).getFrameIndex(), "P2 debe avanzar normalmente");
    }
}
