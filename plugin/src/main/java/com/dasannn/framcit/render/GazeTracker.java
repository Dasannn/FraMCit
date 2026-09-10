package com.dasannn.framcit.render;

import com.dasannn.framcit.command.MapItemBuilder;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import java.util.Collection;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Rastreador de la mirada de los jugadores (F6.3, F6.4, F6.10).
 *
 * Corre cada 5 ticks: ejecuta UN ÚNICO raytrace por jugador conectado desde sus ojos.
 * El coste escala con el número de jugadores conectados, NUNCA con los cuadros del mundo.
 * Detecta si el jugador mira un ItemFrame con mapa animado sin bloques sólidos intermedios,
 * o si sostiene un mapa animado en la mano principal (spec 4.3).
 */
public class GazeTracker implements Runnable {

    public static final double DEFAULT_MAX_DISTANCE = 24.0;
    public static final int DEFAULT_PERIOD_TICKS = 5;

    @FunctionalInterface
    public interface RayTraceFunction {
        RayTraceResult rayTrace(Player player, double maxDistance);
    }

    public interface MapInspector {
        int getFrameMapId(ItemFrame frame);
        int getHandMapId(Player player);
    }

    public static class DefaultMapInspector implements MapInspector {
        @Override
        public int getFrameMapId(ItemFrame frame) {
            if (frame == null) return -1;
            try {
                return extractFraMCitMapId(frame.getItem());
            } catch (Throwable t) {
                return -1;
            }
        }

        @Override
        public int getHandMapId(Player player) {
            if (player == null || player.getInventory() == null) return -1;
            try {
                return extractFraMCitMapId(player.getInventory().getItemInMainHand());
            } catch (Throwable t) {
                return -1;
            }
        }
    }

    private final AnimationManager animationManager;
    private final ImageStore imageStore;
    private final Supplier<Collection<? extends Player>> playersSupplier;
    private final RayTraceFunction rayTracer;
    private final MapInspector mapInspector;
    private double maxDistance = DEFAULT_MAX_DISTANCE;

    public GazeTracker(AnimationManager animationManager, ImageStore imageStore) {
        this(animationManager, imageStore, Bukkit::getOnlinePlayers, GazeTracker::defaultRayTrace, new DefaultMapInspector());
    }

    public GazeTracker(
        AnimationManager animationManager,
        ImageStore imageStore,
        Supplier<Collection<? extends Player>> playersSupplier,
        RayTraceFunction rayTracer
    ) {
        this(animationManager, imageStore, playersSupplier, rayTracer, new DefaultMapInspector());
    }

    public GazeTracker(
        AnimationManager animationManager,
        ImageStore imageStore,
        Supplier<Collection<? extends Player>> playersSupplier,
        RayTraceFunction rayTracer,
        MapInspector mapInspector
    ) {
        this.animationManager = Objects.requireNonNull(animationManager, "animationManager cannot be null");
        this.imageStore = imageStore;
        this.playersSupplier = Objects.requireNonNull(playersSupplier, "playersSupplier cannot be null");
        this.rayTracer = Objects.requireNonNull(rayTracer, "rayTracer cannot be null");
        this.mapInspector = Objects.requireNonNull(mapInspector, "mapInspector cannot be null");
    }

    public void setMaxDistance(double maxDistance) {
        this.maxDistance = maxDistance;
    }

    public double getMaxDistance() {
        return maxDistance;
    }

    @Override
    public void run() {
        Collection<? extends Player> players;
        try {
            players = playersSupplier.get();
        } catch (Throwable t) {
            return;
        }
        if (players == null || players.isEmpty()) {
            return;
        }

        for (Player player : players) {
            if (player == null || !player.isOnline()) {
                continue;
            }
            trackPlayer(player);
        }
    }

    /**
     * Evalúa la mirada o el mapa en mano para un jugador concreto.
     * Garantiza como máximo UN mapa animado por jugador simultáneamente (ARCHITECTURE 5.1).
     */
    public void trackPlayer(Player player) {
        UUID playerUuid = player.getUniqueId();

        // 1. Raytrace: busca un ItemFrame apuntado directamente, sin bloques sólidos intermedios (F6.3, F6.4)
        GazeTarget frameTarget = findAimedItemFrame(player);
        if (frameTarget != null) {
            animationManager.updatePlayerGaze(
                playerUuid,
                frameTarget.mapId(),
                frameTarget.frameUuid(),
                frameTarget.worldName(),
                frameTarget.chunkX(),
                frameTarget.chunkZ()
            );
            return;
        }

        // 2. Si no apunta a ningún marco animado, comprobar si sostiene un mapa animado en mano (F6.10, spec 4.3)
        GazeTarget handTarget = findHeldMap(player);
        if (handTarget != null) {
            animationManager.updatePlayerGaze(
                playerUuid,
                handTarget.mapId(),
                null,
                null,
                0,
                0
            );
            return;
        }

        // 3. Ni mira marco animado ni sostiene mapa animado activo: congelar y conservar frame actual (F6.8)
        animationManager.clearPlayerGaze(playerUuid);
    }

    private GazeTarget findAimedItemFrame(Player player) {
        RayTraceResult hit;
        try {
            hit = rayTracer.rayTrace(player, maxDistance);
        } catch (Throwable t) {
            return null;
        }

        if (hit == null || !(hit.getHitEntity() instanceof ItemFrame itemFrame)) {
            return null;
        }

        // Bloque sólido intermedio? Si el raytrace golpeó un bloque antes del marco,
        // hit.getHitEntity() es null (hit.getHitBlock() != null), así que la condición anterior ya lo descarta.

        int mapId = mapInspector.getFrameMapId(itemFrame);
        if (mapId < 0) {
            return null;
        }

        if (!animationManager.isAnimated(mapId) || animationManager.isMapDisabled(mapId)) {
            return null;
        }

        Location loc = itemFrame.getLocation();
        String worldName = (loc != null && loc.getWorld() != null) ? loc.getWorld().getName() : null;
        Chunk chunk = (loc != null) ? loc.getChunk() : null;
        int chunkX = (chunk != null) ? chunk.getX() : ((loc != null) ? loc.getBlockX() >> 4 : 0);
        int chunkZ = (chunk != null) ? chunk.getZ() : ((loc != null) ? loc.getBlockZ() >> 4 : 0);

        return new GazeTarget(mapId, itemFrame.getUniqueId(), worldName, chunkX, chunkZ);
    }

    private GazeTarget findHeldMap(Player player) {
        int mapId = mapInspector.getHandMapId(player);
        if (mapId < 0) {
            return null;
        }

        if (!animationManager.isAnimated(mapId) || animationManager.isMapDisabled(mapId)) {
            return null;
        }

        // Spec 4.3: vista de mapa activa. El jugador no debe estar mirando hacia arriba (cielo)
        Location loc = player.getLocation();
        if (loc != null && loc.getPitch() < -45.0f) {
            return null;
        }

        return new GazeTarget(mapId, null, null, 0, 0);
    }

    public static int extractFraMCitMapId(ItemStack item) {
        if (item == null) {
            return -1;
        }
        try {
            if (item.getType() != Material.FILLED_MAP) {
                return -1;
            }
            ItemMeta meta = item.getItemMeta();
            if (!(meta instanceof MapMeta mapMeta)) {
                return -1;
            }
            PersistentDataContainer pdc = mapMeta.getPersistentDataContainer();
            if (!pdc.has(MapItemBuilder.KEY_FRAMCIT, PersistentDataType.BYTE)) {
                return -1;
            }

            if (mapMeta.hasMapView() && mapMeta.getMapView() != null) {
                return mapMeta.getMapView().getId();
            }
            if (mapMeta.hasMapId()) {
                return mapMeta.getMapId();
            }
        } catch (Throwable ignored) {}
        return -1;
    }

    private static RayTraceResult defaultRayTrace(Player player, double maxDist) {
        Location eye = player.getEyeLocation();
        return player.getWorld().rayTrace(
            eye,
            eye.getDirection(),
            maxDist,
            FluidCollisionMode.NEVER,
            true, // ignora bloques atravesables como hierba o flores
            0.0,
            entity -> entity instanceof ItemFrame
        );
    }

    private record GazeTarget(int mapId, UUID frameUuid, String worldName, int chunkX, int chunkZ) {}
}
