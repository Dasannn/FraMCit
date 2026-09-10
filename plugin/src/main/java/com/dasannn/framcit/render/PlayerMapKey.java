package com.dasannn.framcit.render;

import java.util.Objects;
import java.util.UUID;

/**
 * Clave para identificar el estado de animación de un jugador sobre un mapa concreto (F6.5).
 */
public record PlayerMapKey(UUID playerUuid, int mapId) {
    public PlayerMapKey {
        Objects.requireNonNull(playerUuid, "playerUuid cannot be null");
    }
}
