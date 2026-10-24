package com.dasannn.framcit.command;

import com.dasannn.framcit.FraMCitPlugin;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.World;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;

/** Restores persisted map deliveries without charging the player a second time. */
public final class PendingDeliveryListener implements Listener {

    private final FraMCitPlugin plugin;
    private final ImageStore imageStore;

    public PendingDeliveryListener(FraMCitPlugin plugin, ImageStore imageStore) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.imageStore = Objects.requireNonNull(imageStore, "imageStore cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> recoverPlayer(event.getPlayer()));
    }

    public void recoverOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            recoverPlayer(player);
        }
    }

    public void recoverPlayer(Player player) {
        if (player == null || !player.isOnline()) return;

        List<ImageIndexEntry> pendingDeliveries = imageStore.getPendingDeliveries(player.getUniqueId());
        if (pendingDeliveries.isEmpty()) return;
        Set<String> existingDeliveryTokens = collectExistingDeliveryTokens(player);

        for (ImageIndexEntry entry : pendingDeliveries) {
            if (!player.isOnline()) return;
            if (!player.hasPermission("framcit.imagen")) {
                // La entrega queda pendiente hasta que el permiso vuelva a concederse.
                continue;
            }

            MapView mapView = Bukkit.getMap(entry.getMapId());
            if (mapView == null) {
                plugin.getLogger().warning("No se pudo recuperar la entrega pendiente del mapa id=" + entry.getMapId()
                    + " para " + player.getUniqueId() + ": el MapView no existe.");
                continue;
            }

            String deliveryToken = entry.getPendingDeliveryToken(player.getUniqueId().toString());
            if (deliveryToken != null && existingDeliveryTokens.contains(deliveryToken)) {
                completeDelivery(entry, player, false);
                continue;
            }

            try {
                URI uri = null;
                try {
                    uri = URI.create(entry.getOriginalUrl());
                } catch (IllegalArgumentException ignored) {}

                ItemStack item = MapItemBuilder.buildMapItem(
                    mapView,
                    uri,
                    entry.getOriginalUrl(),
                    entry.getAuthorName(),
                    entry.getAuthorUuid(),
                    entry.getCreatedAt(),
                    deliveryToken
                );
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack dropped : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                    }
                    player.sendMessage(Messages.component(Messages.INVENTORY_FULL));
                }

                completeDelivery(entry, player, true);
            } catch (Throwable error) {
                plugin.getLogger().log(Level.SEVERE, "No se pudo recuperar la entrega pendiente del mapa id="
                    + entry.getMapId() + " para " + player.getUniqueId() + "; se reintentará en otra conexión.", error);
            }
        }
    }

    private Set<String> collectExistingDeliveryTokens(Player player) {
        Set<String> tokens = new HashSet<>();
        for (ItemStack item : player.getInventory().getContents()) {
            addDeliveryToken(item, tokens);
        }
        // Inventory-full deliveries are dropped into the world. Search already-loaded entities
        // so a restart between the drop and index commit doesn't create a second copy.
        for (World world : Bukkit.getWorlds()) {
            for (Item dropped : world.getEntitiesByClass(Item.class)) {
                addDeliveryToken(dropped.getItemStack(), tokens);
            }
        }
        return tokens;
    }

    private void addDeliveryToken(ItemStack item, Set<String> tokens) {
        if (item == null || item.getType() != Material.FILLED_MAP || !(item.getItemMeta() instanceof MapMeta meta)) {
            return;
        }
        String token = meta.getPersistentDataContainer().get(MapItemBuilder.KEY_DELIVERY_TOKEN, PersistentDataType.STRING);
        if (token != null && !token.isBlank()) tokens.add(token);
    }

    private void completeDelivery(ImageIndexEntry entry, Player player, boolean deliveredNow) {
        imageStore.markImageReady(entry.getMapId(), player.getUniqueId()).whenComplete((ignored, error) -> {
            if (error != null) {
                plugin.getLogger().log(Level.SEVERE, "No se pudo guardar el cierre de la entrega recuperada del mapa id="
                    + entry.getMapId() + "; podría repetirse tras otro reinicio.", error);
                return;
            }
            if (deliveredNow && player.isOnline()) {
                player.sendMessage(Messages.component(Messages.DELIVERY_RECOVERED));
                plugin.getLogger().info("Entrega pendiente recuperada sin recobro: jugador=" + player.getName()
                    + ", uuid=" + player.getUniqueId() + ", id=" + entry.getMapId());
            }
        });
    }
}
