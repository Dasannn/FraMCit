package com.dasannn.framcit.render;

import java.util.Objects;
import java.util.UUID;

/**
 * Estado de animación por par (jugador, mapa) (F6.5, F6.8, F6.11).
 * Guarda si el jugador está mirando el mapa, el índice de frame actual,
 * la marca de tiempo de su propio reloj, y la ubicación/entidad del marco (si está en marco)
 * para la limpieza libre de fugas de memoria.
 */
public class PlayerMapState {

    private final UUID playerUuid;
    private final int mapId;

    private int frameIndex;
    private long nextAdvanceTimeMs;
    private boolean gazing;

    // Metadatos para limpieza de estado (F6.11)
    // frameUuid es null si el mapa se sostiene en mano
    private UUID frameUuid;
    private String worldName;
    private int chunkX;
    private int chunkZ;
    private boolean hasChunkInfo;

    public PlayerMapState(UUID playerUuid, int mapId) {
        this.playerUuid = Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
        this.mapId = mapId;
        this.frameIndex = 0;
        this.nextAdvanceTimeMs = 0L;
        this.gazing = false;
        this.hasChunkInfo = false;
    }

    public UUID getPlayerUuid() {
        return playerUuid;
    }

    public int getMapId() {
        return mapId;
    }

    public int getFrameIndex() {
        return frameIndex;
    }

    public void setFrameIndex(int frameIndex) {
        this.frameIndex = frameIndex;
    }

    public long getNextAdvanceTimeMs() {
        return nextAdvanceTimeMs;
    }

    public void setNextAdvanceTimeMs(long nextAdvanceTimeMs) {
        this.nextAdvanceTimeMs = nextAdvanceTimeMs;
    }

    public boolean isGazing() {
        return gazing;
    }

    public void setGazing(boolean gazing) {
        this.gazing = gazing;
    }

    public UUID getFrameUuid() {
        return frameUuid;
    }

    public void setFrameUuid(UUID frameUuid) {
        this.frameUuid = frameUuid;
    }

    public void setChunkInfo(String worldName, int chunkX, int chunkZ) {
        this.worldName = worldName;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.hasChunkInfo = (worldName != null);
    }

    public void clearFrameAndChunkInfo() {
        this.frameUuid = null;
        this.worldName = null;
        this.hasChunkInfo = false;
    }

    public boolean isInChunk(String world, int x, int z) {
        if (!hasChunkInfo || world == null || this.worldName == null) {
            return false;
        }
        return this.worldName.equals(world) && this.chunkX == x && this.chunkZ == z;
    }

    public boolean matchesFrame(UUID frameUuid) {
        if (this.frameUuid == null || frameUuid == null) {
            return false;
        }
        return this.frameUuid.equals(frameUuid);
    }
}
