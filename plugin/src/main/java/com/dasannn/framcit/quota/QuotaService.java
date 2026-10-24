package com.dasannn.framcit.quota;

import com.dasannn.framcit.config.FraMCitConfig;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Servicio de control de cupos (slots y frames) por jugador (Spec 003, ARCHITECTURE 3, F11.7).
 *
 * Resuelve:
 * - Si a un jugador le cabe otra imagen (slots).
 * - Cuántos frames puede tener la imagen que pide (control de memoria).
 * - Consultas de posesión y liberación de cupo.
 */
public class QuotaService {

    private final ImageStore imageStore;
    private final Supplier<FraMCitConfig> configSupplier;

    public QuotaService(ImageStore imageStore, Supplier<FraMCitConfig> configSupplier) {
        this.imageStore = Objects.requireNonNull(imageStore, "imageStore cannot be null");
        this.configSupplier = Objects.requireNonNull(configSupplier, "configSupplier cannot be null");
    }

    public QuotaService(ImageStore imageStore, FraMCitConfig config) {
        this(imageStore, () -> config);
    }

    private FraMCitConfig getConfig() {
        return configSupplier.get();
    }

    /**
     * Cuenta cuántas imágenes posee actualmente el jugador.
     */
    public int getUsedSlots(UUID playerUuid) {
        if (playerUuid == null) return 0;
        int count = 0;
        for (ImageIndexEntry entry : imageStore.getIndexManager().getAllEntries()) {
            if (entry != null && entry.hasOwner(playerUuid)) {
                count++;
            }
        }
        return count;
    }

    public int getUsedSlots(Player player) {
        return (player != null) ? getUsedSlots(player.getUniqueId()) : 0;
    }

    /**
     * Obtiene el límite máximo de slots asignado al jugador según sus permisos.
     * Retorna PermissionTier.UNLIMITED si tiene framcit.slots.ilimitado.
     */
    public int getMaxSlots(Player player) {
        FraMCitConfig cfg = getConfig();
        int maxNode = (cfg != null) ? cfg.getMaxSlotsNode() : FraMCitConfig.DEFAULT_SLOTS_MAX_NODE;
        int defaultSlots = (cfg != null) ? cfg.getDefaultSlots() : FraMCitConfig.DEFAULT_SLOTS_DEFAULT;
        return PermissionTier.resolveSlots(player, maxNode, defaultSlots);
    }

    /**
     * Comprueba si el jugador tiene slots ilimitados (framcit.slots.ilimitado).
     */
    public boolean isSlotsUnlimited(Player player) {
        return PermissionTier.hasUnlimitedSlots(player);
    }

    /**
     * Comprueba si el jugador tiene algún slot disponible para una imagen nueva.
     */
    public boolean hasAvailableSlot(Player player) {
        if (player == null) return false;
        if (isSlotsUnlimited(player)) {
            return true;
        }
        int max = getMaxSlots(player);
        if (max <= 0) {
            return false;
        }
        int used = getUsedSlots(player);
        return used < max;
    }

    /**
     * Comprueba si el jugador puede adquirir la imagen de una URL específica.
     * Regla 2.3 de Spec 003: Pedir una URL que YA posees no ocupa un segundo slot,
     * por lo que el cupo no bloquea aunque esté al límite.
     */
    public boolean canAcquireImage(Player player, String normalizedUrl) {
        if (player == null) return false;
        if (normalizedUrl != null && isOwnerOfUrl(player.getUniqueId(), normalizedUrl)) {
            return true;
        }
        return hasAvailableSlot(player);
    }

    /**
     * Comprueba si el jugador ya posee la imagen con la URL normalizada dada.
     */
    public boolean isOwnerOfUrl(UUID playerUuid, String normalizedUrl) {
        if (playerUuid == null || normalizedUrl == null) return false;
        ImageIndexEntry entry = imageStore.findByNormalizedUrl(normalizedUrl);
        return entry != null && entry.hasOwner(playerUuid);
    }

    /**
     * Comprueba si el jugador es poseedor de la imagen por id de mapa.
     */
    public boolean isOwner(UUID playerUuid, int mapId) {
        if (playerUuid == null) return false;
        ImageIndexEntry entry = imageStore.findByMapId(mapId);
        return entry != null && entry.hasOwner(playerUuid);
    }

    /**
     * Resuelve el cupo efectivo de frames para la petición del jugador (F11.4, F11.8).
     * Calculado en el hilo principal antes de despachar al pool.
     * NUNCA supera el límite global de config.yml (Constitution 6.6).
     */
    public int getEffectiveMaxFrames(Player player) {
        FraMCitConfig cfg = getConfig();
        int maxNode = (cfg != null) ? cfg.getMaxFramesNode() : FraMCitConfig.DEFAULT_FRAMES_MAX_NODE;
        int defaultFrames = (cfg != null) ? cfg.getDefaultFrames() : FraMCitConfig.DEFAULT_FRAMES_DEFAULT;
        int globalLimit = (cfg != null) ? cfg.getMaxFrames() : FraMCitConfig.DEFAULT_MAX_FRAMES;
        return PermissionTier.resolveEffectiveFrames(player, maxNode, defaultFrames, globalLimit);
    }

    /**
     * Retorna todas las imágenes que posee el jugador.
     */
    public List<ImageIndexEntry> getOwnedImages(UUID playerUuid) {
        if (playerUuid == null) return List.of();
        List<ImageIndexEntry> list = new ArrayList<>();
        for (ImageIndexEntry entry : imageStore.getIndexManager().getAllEntries()) {
            if (entry != null && !imageStore.isPendingMap(entry.getMapId()) && entry.hasOwner(playerUuid)) {
                list.add(entry);
            }
        }
        return list;
    }

    /**
     * Registra al jugador como poseedor de la imagen.
     */
    public boolean addOwnership(int mapId, UUID playerUuid) {
        if (playerUuid == null) return false;
        ImageIndexEntry entry = imageStore.findByMapId(mapId);
        if (entry != null) {
            boolean added = entry.addOwner(playerUuid);
            if (added) {
                imageStore.saveIndexAsync();
            }
            return added;
        }
        return false;
    }

    /**
     * Libera la posesión de una imagen por parte de un jugador (Spec 003 Sección 5.2).
     * Retorna true si era poseedor y fue eliminado de la lista.
     */
    public boolean removeOwnership(int mapId, UUID playerUuid) {
        if (playerUuid == null) return false;
        ImageIndexEntry entry = imageStore.findByMapId(mapId);
        if (entry != null && entry.hasOwner(playerUuid)) {
            boolean removed = entry.removeOwner(playerUuid);
            if (removed) {
                imageStore.saveIndexAsync();
            }
            return removed;
        }
        return false;
    }

    /**
     * Comprueba si quedan otros poseedores además del jugador indicado.
     */
    public boolean hasOtherOwners(int mapId, UUID playerUuid) {
        ImageIndexEntry entry = imageStore.findByMapId(mapId);
        if (entry == null) return false;
        for (String owner : entry.getOwners()) {
            if (playerUuid == null || !owner.equalsIgnoreCase(playerUuid.toString())) {
                return true;
            }
        }
        return false;
    }

    public ImageStore getImageStore() {
        return imageStore;
    }
}
