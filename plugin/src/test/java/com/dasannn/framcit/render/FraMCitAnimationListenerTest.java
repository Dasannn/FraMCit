package com.dasannn.framcit.render;

import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraMCitAnimationListenerTest {

    private AnimationManager manager;
    private FraMCitAnimationListener listener;

    @BeforeEach
    void setUp() {
        manager = new AnimationManager(null, null);
        listener = new FraMCitAnimationListener(manager);
    }

    private Player createMockPlayer(UUID uuid) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                if ("getUniqueId".equals(method.getName())) return uuid;
                return null;
            }
        );
    }

    private ItemFrame createMockItemFrame(UUID uuid) {
        return (ItemFrame) Proxy.newProxyInstance(
            ItemFrame.class.getClassLoader(),
            new Class<?>[]{ ItemFrame.class },
            (proxy, method, args) -> {
                if ("getUniqueId".equals(method.getName())) return uuid;
                return null;
            }
        );
    }

    private Chunk createMockChunk(String worldName, int x, int z) {
        World world = (World) Proxy.newProxyInstance(
            World.class.getClassLoader(),
            new Class<?>[]{ World.class },
            (proxy, method, args) -> {
                if ("getName".equals(method.getName())) return worldName;
                return null;
            }
        );

        return (Chunk) Proxy.newProxyInstance(
            Chunk.class.getClassLoader(),
            new Class<?>[]{ Chunk.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getWorld".equals(name)) return world;
                if ("getX".equals(name)) return x;
                if ("getZ".equals(name)) return z;
                return null;
            }
        );
    }

    @Test
    @SuppressWarnings("deprecation")
    @DisplayName("F6.11 - Evento PlayerQuit limpia el estado del jugador")
    void testPlayerQuitEventCleansState() {
        UUID playerUuid = UUID.randomUUID();
        manager.updatePlayerGaze(playerUuid, 1, UUID.randomUUID(), "world", 0, 0);
        assertEquals(1, manager.getStateCount());

        Player player = createMockPlayer(playerUuid);
        PlayerQuitEvent event = new PlayerQuitEvent(player, (net.kyori.adventure.text.Component) null);

        listener.onPlayerQuit(event);
        assertEquals(0, manager.getStateCount());
    }

    @Test
    @DisplayName("F6.11 - Evento ChunkUnload limpia los estados de marcos en ese chunk")
    void testChunkUnloadEventCleansState() {
        UUID playerUuid = UUID.randomUUID();
        UUID frame1 = UUID.randomUUID();
        UUID frame2 = UUID.randomUUID();

        manager.updatePlayerGaze(playerUuid, 1, frame1, "world_nether", 7, 8);
        manager.updatePlayerGaze(playerUuid, 2, frame2, "world_nether", 9, 9);
        assertEquals(2, manager.getStateCount());

        Chunk chunk = createMockChunk("world_nether", 7, 8);
        ChunkUnloadEvent event = new ChunkUnloadEvent(chunk);

        listener.onChunkUnload(event);

        assertNull(manager.getState(playerUuid, 1));
        assertNotNull(manager.getState(playerUuid, 2));
    }

    @Test
    @DisplayName("F6.11 - Evento HangingBreak limpia el estado del marco roto")
    void testHangingBreakEventCleansState() {
        UUID playerUuid = UUID.randomUUID();
        UUID frameUuid = UUID.randomUUID();

        manager.updatePlayerGaze(playerUuid, 3, frameUuid, "world", 1, 1);
        assertEquals(1, manager.getStateCount());

        ItemFrame itemFrame = createMockItemFrame(frameUuid);
        HangingBreakEvent event = new HangingBreakEvent(itemFrame, HangingBreakEvent.RemoveCause.PHYSICS);

        listener.onHangingBreak(event);
        assertEquals(0, manager.getStateCount());
    }
}
