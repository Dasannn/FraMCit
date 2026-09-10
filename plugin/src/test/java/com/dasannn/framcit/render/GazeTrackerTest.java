package com.dasannn.framcit.render;

import com.dasannn.framcit.store.ImageIndexEntry;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GazeTrackerTest {

    private AnimationManager manager;
    private Map<Integer, ImageIndexEntry> entries;

    @BeforeEach
    void setUp() {
        entries = new HashMap<>();
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
        };
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
                if ("isOnline".equals(name)) return true;
                if ("getLocation".equals(name)) return loc;
                if ("getEyeLocation".equals(name)) return loc;
                if ("getWorld".equals(name)) return world;
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

    @Test
    @DisplayName("F6.3 & F6.4 - Raytrace detecta ItemFrame con mapa animado y ejecuta un unico rayo por jugador")
    void testRayTraceFindsAnimatedItemFrame() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int mapId = 5;

        // Mapa animado de 5 frames
        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 5, List.of(100, 100, 100, 100, 100), 1, false
        ));

        ItemFrame frame = createMockItemFrame(frameUuid, 2, 3);
        Player player = createMockPlayer(playerUuid, 0.0f);

        AtomicInteger rayTraceCalls = new AtomicInteger(0);
        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> {
            rayTraceCalls.incrementAndGet();
            return new RayTraceResult(new Vector(0, 0, 0), frame);
        };

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override
            public int getFrameMapId(ItemFrame f) {
                return (f == frame) ? mapId : -1;
            }

            @Override
            public int getHandMapId(Player p) {
                return -1;
            }
        };

        GazeTracker tracker = new GazeTracker(
            manager,
            null,
            () -> List.of(player),
            rayTracer,
            inspector
        );

        tracker.run();

        // 1 unico raytrace por jugador
        assertEquals(1, rayTraceCalls.get(), "Debe ejecutar exactamente 1 raytrace por jugador");

        PlayerMapState state = manager.getState(playerUuid, mapId);
        assertNotNull(state);
        assertTrue(state.isGazing());
        assertEquals(frameUuid, state.getFrameUuid());
        assertTrue(state.isInChunk("world", 2, 3));
    }

    @Test
    @DisplayName("F6.4 - Bloque solido intermedio bloquea el raytrace y no activa la animacion")
    void testSolidBlockObstructionBlocksGaze() {
        UUID playerUuid = UUID.randomUUID();
        Player player = createMockPlayer(playerUuid, 0.0f);

        Block mockBlock = (Block) Proxy.newProxyInstance(
            Block.class.getClassLoader(),
            new Class<?>[]{ Block.class },
            (proxy, method, args) -> null
        );

        // El raytrace impacta un bloque sólido antes que cualquier entidad
        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> {
            return new RayTraceResult(new Vector(0, 0, 0), mockBlock, null);
        };

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return 1; }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(
            manager,
            null,
            () -> List.of(player),
            rayTracer,
            inspector
        );

        tracker.run();

        assertEquals(0, manager.getStateCount(), "Un bloque solido bloquea la mirada, no debe crearse estado de animacion");
    }

    @Test
    @DisplayName("F6.4 - ItemFrame con imagen estatica no activa ciclo de animacion")
    void testStaticMapDoesNotAnimate() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();
        int staticMapId = 8;

        // frameCount = 1 (estatica)
        entries.put(staticMapId, new ImageIndexEntry(
            staticMapId, "url", "norm", "Author", "uuid", "date", "PNG", 1, List.of(0), 1, false
        ));

        ItemFrame frame = createMockItemFrame(frameUuid, 0, 0);
        Player player = createMockPlayer(playerUuid, 0.0f);

        GazeTracker.RayTraceFunction rayTracer = (p, dist) -> new RayTraceResult(new Vector(0, 0, 0), frame);

        GazeTracker.MapInspector inspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return staticMapId; }
            @Override public int getHandMapId(Player p) { return -1; }
        };

        GazeTracker tracker = new GazeTracker(
            manager,
            null,
            () -> List.of(player),
            rayTracer,
            inspector
        );

        tracker.run();

        assertEquals(0, manager.getStateCount(), "Mapas estaticos (frameCount=1) no deben registrarse en el ciclo de animacion");
    }

    @Test
    @DisplayName("F6.10 & spec 4.3 - Mapa animado en mano principal: pitch normal anima, pitch hacia arriba no")
    void testHeldMapInMainHandWithPitchCheck() {
        UUID playerUuid = UUID.randomUUID();
        int mapId = 12;

        entries.put(mapId, new ImageIndexEntry(
            mapId, "url", "norm", "Author", "uuid", "date", "GIF", 4, List.of(100, 100, 100, 100), 1, false
        ));

        GazeTracker.MapInspector handInspector = new GazeTracker.MapInspector() {
            @Override public int getFrameMapId(ItemFrame f) { return -1; }
            @Override public int getHandMapId(Player p) { return mapId; }
        };

        // Caso 1: Jugador mirando al cielo (pitch = -60.0f < -45.0f)
        Player playerLookingUp = createMockPlayer(playerUuid, -60.0f);

        GazeTracker.RayTraceFunction emptyRayTracer = (p, dist) -> null;

        GazeTracker tracker1 = new GazeTracker(
            manager,
            null,
            () -> List.of(playerLookingUp),
            emptyRayTracer,
            handInspector
        );

        tracker1.run();
        assertEquals(0, manager.getStateCount(), "Mirar hacia arriba no debe activar la vista de mapa en mano");

        // Caso 2: Jugador con vista normal/horizontal (pitch = 0.0f >= -45.0f)
        Player playerNormalPitch = createMockPlayer(playerUuid, 0.0f);

        GazeTracker tracker2 = new GazeTracker(
            manager,
            null,
            () -> List.of(playerNormalPitch),
            emptyRayTracer,
            handInspector
        );

        tracker2.run();
        PlayerMapState state = manager.getState(playerUuid, mapId);
        assertNotNull(state);
        assertTrue(state.isGazing(), "Con pitch normal (0.0f), el mapa en mano debe animarse");
        assertNull(state.getFrameUuid(), "El mapa en mano no tiene ItemFrame asociado");
    }

    @Test
    @DisplayName("F6.4 - defaultExtractFraMCitMapId maneja items nulos de forma segura")
    void testDefaultExtractorSafeHandling() {
        assertEquals(-1, GazeTracker.extractFraMCitMapId(null));
    }
}
