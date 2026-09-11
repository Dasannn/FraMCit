package com.dasannn.framcit.quota;

import org.bukkit.permissions.Permissible;

/**
 * Traduce familias de nodos de permisos escalonados (framcit.slots.<n>, framcit.frames.<n>)
 * al número más alto concedido (Spec 003 Sección 3, F11.1, F11.4).
 *
 * Reglas:
 * 1. Gana el mayor, no se suman.
 * 2. Sin ningún nodo, se aplica el valor por defecto configurado.
 * 3. En slots, si cuenta con 'framcit.slots.ilimitado', no tiene límite (UNLIMITED).
 * 4. En frames, el cupo efectivo NUNCA supera el límite global de config.yml (Constitution 6.6, F11.4).
 */
public final class PermissionTier {

    public static final String SLOTS_PREFIX = "framcit.slots.";
    public static final String SLOTS_UNLIMITED = "framcit.slots.ilimitado";
    public static final String FRAMES_PREFIX = "framcit.frames.";

    public static final int UNLIMITED = Integer.MAX_VALUE;

    private PermissionTier() {}

    /**
     * Resuelve el cupo numérico para una familia de permisos con prefijo dado.
     * Busca desde 1 hasta maxNode inclusive y retorna el número más alto concedido.
     * Si no tiene ningún nodo, retorna defaultValue.
     *
     * @param permissible entidad con permisos (jugador, etc.)
     * @param prefix prefijo del nodo (ej: "framcit.slots.")
     * @param maxNode límite superior de búsqueda
     * @param defaultValue valor por defecto si no tiene ningún nodo
     * @return el número más alto concedido, o defaultValue
     */
    public static int resolveTier(Permissible permissible, String prefix, int maxNode, int defaultValue) {
        if (permissible == null) {
            return defaultValue;
        }
        int highest = -1;
        for (int i = 1; i <= maxNode; i++) {
            if (permissible.hasPermission(prefix + i)) {
                highest = i;
            }
        }
        return (highest != -1) ? highest : defaultValue;
    }

    /**
     * Comprueba si el jugador tiene slots ilimitados.
     */
    public static boolean hasUnlimitedSlots(Permissible permissible) {
        return permissible != null && permissible.hasPermission(SLOTS_UNLIMITED);
    }

    /**
     * Resuelve el cupo de slots de un jugador.
     * Si tiene framcit.slots.ilimitado retorna UNLIMITED.
     */
    public static int resolveSlots(Permissible permissible, int maxNode, int defaultSlots) {
        if (hasUnlimitedSlots(permissible)) {
            return UNLIMITED;
        }
        return resolveTier(permissible, SLOTS_PREFIX, maxNode, defaultSlots);
    }

    /**
     * Resuelve el cupo efectivo de frames de un jugador (F11.4, SEC).
     * NUNCA supera globalLimit (el límite global de config.yml).
     */
    public static int resolveEffectiveFrames(Permissible permissible, int maxNode, int defaultFrames, int globalLimit) {
        int tier = resolveTier(permissible, FRAMES_PREFIX, maxNode, defaultFrames);
        return Math.min(tier, globalLimit);
    }
}
