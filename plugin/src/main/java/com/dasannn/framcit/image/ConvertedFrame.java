package com.dasannn.framcit.image;

import java.util.Objects;

/**
 * Representa un frame convertido a bytes de la paleta de mapa de Minecraft (128x128 = 16384 bytes).
 */
public final class ConvertedFrame {

    public static final int EXPECTED_BYTES = 128 * 128; // 16384 bytes

    private final byte[] mapBytes;
    private final int delayMs;

    public ConvertedFrame(byte[] mapBytes, int delayMs) {
        Objects.requireNonNull(mapBytes, "mapBytes cannot be null");
        if (mapBytes.length != EXPECTED_BYTES) {
            throw new IllegalArgumentException("mapBytes must be exactly " + EXPECTED_BYTES + " bytes, got " + mapBytes.length);
        }
        this.mapBytes = mapBytes.clone();
        this.delayMs = delayMs;
    }

    public byte[] getMapBytes() {
        return mapBytes.clone();
    }

    public int getDelayMs() {
        return delayMs;
    }
}
