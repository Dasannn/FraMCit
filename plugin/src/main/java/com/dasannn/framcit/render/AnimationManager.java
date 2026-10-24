package com.dasannn.framcit.render;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.entity.Player;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor central de estados de animación para FraMCit (F6.1, F6.4 - F6.12).
 *
 * Mantiene el estado por par (jugador, mapa), coordina el avance de frames,
 * la limpieza de memoria estricta (desconexión, descarga de chunk, rotura de marco),
 * el congelado/reanudado y la desactivación aislada de mapas ante excepciones.
 *
 * Además del mapa completo de estados (incluye los congelados, que conservan su frame),
 * mantiene un índice de estados activos por jugador (como máximo uno por jugador), que
 * AnimationScheduler recorre cada tick sin pagar el coste del historial de la sesión.
 */
public class AnimationManager implements FraMCitRenderer.FrameProvider {

    public static final int MIN_FRAME_DELAY_MS = 100; // Tope de 10 fps (spec 4.2, F6.7)
    private static volatile int minFrameDelayMs = MIN_FRAME_DELAY_MS;

    public static void setMinFrameDelayMs(int minDelay) {
        minFrameDelayMs = minDelay;
    }

    public static int getMinFrameDelayMs() {
        return minFrameDelayMs;
    }

    public static void resetDefaults() {
        minFrameDelayMs = MIN_FRAME_DELAY_MS;
    }

    private final ImageStore imageStore;
    private final Logger logger;
    private final ConcurrentHashMap<PlayerMapKey, PlayerMapState> states = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, PlayerMapState> activeStates = new ConcurrentHashMap<>();
    private final Set<Integer> disabledMaps = ConcurrentHashMap.newKeySet();
    private LongSupplier timeSupplier = System::currentTimeMillis;

    public AnimationManager(ImageStore imageStore, Logger logger) {
        this.imageStore = imageStore;
        this.logger = logger;
    }

    public void setTimeSupplier(LongSupplier timeSupplier) {
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier cannot be null");
    }

    public long getCurrentTimeMs() {
        return timeSupplier.getAsLong();
    }

    @Override
    public int getFrameIndex(Player player, int mapId) {
        if (player == null) {
            return 0;
        }
        return getFrameIndex(player.getUniqueId(), mapId);
    }

    public int getFrameIndex(UUID playerUuid, int mapId) {
        if (playerUuid == null) {
            return 0;
        }
        PlayerMapState state = states.get(new PlayerMapKey(playerUuid, mapId));
        return (state != null) ? state.getFrameIndex() : 0;
    }

    public PlayerMapState getState(UUID playerUuid, int mapId) {
        if (playerUuid == null) return null;
        return states.get(new PlayerMapKey(playerUuid, mapId));
    }

    public Collection<PlayerMapState> getAllStates() {
        return Collections.unmodifiableCollection(states.values());
    }

    /** Snapshot de los pares que están mirando ahora mismo; el scheduler solo recorre este índice. */
    public Collection<PlayerMapState> getActiveStates() {
        return List.copyOf(activeStates.values());
    }

    public int getStateCount() {
        return states.size();
    }

    /**
     * Actualiza el objetivo de mirada de un jugador (F6.4, F6.5, F6.8, F6.10).
     * Garantiza que cada jugador solo tenga un mapa activo como máximo (ancho de banda acotado).
     */
    public void updatePlayerGaze(
        UUID playerUuid,
        int mapId,
        UUID frameUuid,
        String worldName,
        int chunkX,
        int chunkZ
    ) {
        if (playerUuid == null || mapId < 0 || isMapDisabled(mapId)) {
            return;
        }

        // 1. Congelar el mapa activo anterior de este jugador en O(1).
        PlayerMapState previousActive = activeStates.get(playerUuid);
        if (previousActive != null && previousActive.getMapId() != mapId) {
            previousActive.setGazing(false);
            activeStates.remove(playerUuid, previousActive);
        }

        // 2. Obtener o crear estado para este par (jugador, mapa)
        PlayerMapKey key = new PlayerMapKey(playerUuid, mapId);
        PlayerMapState state = states.computeIfAbsent(key, k -> new PlayerMapState(playerUuid, mapId));

        if (!state.isGazing()) {
            // Reanudando o comenzando a mirar
            state.setGazing(true);
            int currentFrame = state.getFrameIndex();
            int delayMs = getEffectiveDelayMs(getFrameDelayMs(mapId, currentFrame));
            state.setNextAdvanceTimeMs(getCurrentTimeMs() + delayMs);
        }
        activeStates.put(playerUuid, state);

        // Actualizar metadatos de ubicación/marco para la limpieza de fugas (F6.11)
        state.setFrameUuid(frameUuid);
        state.setChunkInfo(worldName, chunkX, chunkZ);
    }

    /**
     * Marca al jugador como no mirando ningún mapa animado (F6.8).
     * Congela y conserva el frameIndex en su valor actual para cuando vuelva a mirar.
     */
    public void clearPlayerGaze(UUID playerUuid) {
        if (playerUuid == null) return;
        PlayerMapState active = activeStates.remove(playerUuid);
        if (active != null) {
            active.setGazing(false);
        }
    }

    /**
     * Limpieza por desconexión de jugador (F6.11).
     * Elimina todo estado del jugador, dejando el mapa limpio sin fugas.
     */
    public void onPlayerQuit(UUID playerUuid) {
        if (playerUuid == null) return;
        states.entrySet().removeIf(entry -> entry.getKey().playerUuid().equals(playerUuid));
        activeStates.remove(playerUuid);
    }

    /**
     * Limpieza por descarga de chunk (F6.11).
     * Elimina cualquier estado asociado a un marco que resida en el chunk descargado.
     */
    public void onChunkUnload(String worldName, int chunkX, int chunkZ) {
        if (worldName == null) return;
        states.entrySet().removeIf(entry -> entry.getValue().isInChunk(worldName, chunkX, chunkZ));
        activeStates.entrySet().removeIf(entry -> entry.getValue().isInChunk(worldName, chunkX, chunkZ));
    }

    /**
     * Limpieza por rotura de marco (F6.11).
     * Elimina cualquier estado asociado al marco roto.
     */
    public void onFrameBreak(UUID frameUuid) {
        if (frameUuid == null) return;
        states.entrySet().removeIf(entry -> entry.getValue().matchesFrame(frameUuid));
        activeStates.values().removeIf(state -> state.matchesFrame(frameUuid));
    }

    /**
     * Desactiva un mapa ante una excepción inesperada (F6.12).
     * Un mapa roto nunca tumba el ciclo de los demás mapas.
     */
    public void disableMap(int mapId, Throwable cause) {
        disabledMaps.add(mapId);
        states.entrySet().removeIf(entry -> entry.getKey().mapId() == mapId);
        activeStates.values().removeIf(state -> state.getMapId() == mapId);
        if (logger != null) {
            logger.log(Level.SEVERE, "Mapa animado id=" + mapId + " desactivado por error en su ciclo de animación.", cause);
        }
    }

    public boolean isMapDisabled(int mapId) {
        return disabledMaps.contains(mapId);
    }

    public void enableMap(int mapId) {
        disabledMaps.remove(mapId);
    }

    public Set<Integer> getDisabledMaps() {
        return Collections.unmodifiableSet(disabledMaps);
    }

    /**
     * Determina si un mapa es animado sin cargar el .fmc desde disco (F6.4).
     */
    public boolean isAnimated(int mapId) {
        return getFrameCount(mapId) > 1;
    }

    /**
     * Obtiene el número total de frames del mapa.
     */
    public int getFrameCount(int mapId) {
        if (imageStore != null) {
            ImageIndexEntry entry = imageStore.findByMapId(mapId);
            if (entry != null) {
                return entry.getFrameCount();
            }
            ConvertedImage cached = imageStore.getIfPresent(mapId);
            if (cached != null) {
                return cached.getFrameCount();
            }
        }
        return 0;
    }

    /**
     * Obtiene el retardo declarado de un frame concreto.
     */
    public int getFrameDelayMs(int mapId, int frameIndex) {
        if (imageStore != null) {
            ImageIndexEntry entry = imageStore.findByMapId(mapId);
            if (entry != null && entry.getDelays() != null) {
                List<Integer> delays = entry.getDelays();
                if (frameIndex >= 0 && frameIndex < delays.size()) {
                    return delays.get(frameIndex);
                }
            }
            ConvertedImage cached = imageStore.getIfPresent(mapId);
            if (cached != null && frameIndex >= 0 && frameIndex < cached.getFrameCount()) {
                ConvertedFrame frame = cached.getFrames().get(frameIndex);
                return frame.getDelayMs();
            }
        }
        return MIN_FRAME_DELAY_MS;
    }

    public void removeMap(int mapId) {
        disabledMaps.remove(mapId);
        states.entrySet().removeIf(entry -> entry.getKey().mapId() == mapId);
        activeStates.values().removeIf(state -> state.getMapId() == mapId);
    }

    /**
     * Aplica el tope de velocidad configurado (mínimo de retardo) y corrige retardos inválidos o cero (spec 4.2, F6.7).
     */
    public static int getEffectiveDelayMs(int rawDelayMs) {
        if (rawDelayMs < minFrameDelayMs) {
            return minFrameDelayMs;
        }
        return rawDelayMs;
    }

    public void close() {
        states.clear();
        activeStates.clear();
        disabledMaps.clear();
    }
}
