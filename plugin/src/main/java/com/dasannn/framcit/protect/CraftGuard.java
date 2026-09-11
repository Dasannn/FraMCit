package com.dasannn.framcit.protect;

import com.dasannn.framcit.command.MapItemBuilder;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import io.papermc.paper.event.player.CartographyItemEvent;
import org.bukkit.Material;
import org.bukkit.block.Crafter;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Protección de mapas de FraMCit contra crafteo y manipulación en mesas (spec 001 sección 2.4, F12.12 - F12.17).
 *
 * Un mapa creado por FraMCit es un item inerte para el crafteo: no entra en ninguna
 * receta de la mesa de crafteo ni en ninguna operación de la mesa de cartografía
 * (copiar, ampliar, bloquear).
 *
 * Se bloquea la categoría entera, no una lista de recetas (F12.15).
 * El resultado sale siempre vacío (null). Los items nunca se consumen ni destruyen (F12.14).
 * Al retirar el plugin, los mapas vuelven a comportarse normalmente (F12.17).
 */
public class CraftGuard implements Listener {

    /**
     * Determina si un ItemStack corresponde a un mapa creado por FraMCit mediante su PDC.
     */
    public static boolean isFraMCitMap(ItemStack item) {
        if (item == null || item.getType() != Material.FILLED_MAP) {
            return false;
        }
        if (!item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc == null) {
            return false;
        }
        return pdc.has(MapItemBuilder.KEY_FRAMCIT, PersistentDataType.BYTE);
    }

    /**
     * Mesa de crafteo (F12.12): si cualquier ingrediente de la matriz de crafteo es un mapa
     * de FraMCit, el resultado se vacía (null).
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareItemCraft(PrepareItemCraftEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }
        for (ItemStack item : inventory.getMatrix()) {
            if (isFraMCitMap(item)) {
                inventory.setResult(null);
                return;
            }
        }
    }

    /**
     * Bloqueo definitivo de crafteo en mesa de crafteo para evitar cualquier bypass o glitch.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        CraftingInventory inventory = event.getInventory();
        if (inventory == null) {
            return;
        }
        for (ItemStack item : inventory.getMatrix()) {
            if (isFraMCitMap(item)) {
                event.setCancelled(true);
                event.setResult(Event.Result.DENY);
                return;
            }
        }
    }

    /**
     * Mesa de cartografía (F12.11, F12.13, F12.14, F12.15):
     * Evento verificado contra Paper 26.2 (com.destroystokyo.paper.event.inventory.PrepareResultEvent).
     * Si un ingrediente en la mesa de cartografía lleva la marca de FraMCit, el resultado se vacía (null).
     * Cubre copiar, ampliar (zoom) y bloquear con panel de cristal.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareResultEvent event) {
        Inventory inventory = event.getInventory();
        if (inventory instanceof CartographyInventory cartographyInventory) {
            if (hasFraMCitMap(cartographyInventory) || isFraMCitMap(event.getResult())) {
                event.setResult(null);
            }
        }
    }

    /**
     * Evento de Paper 26.2 disparado al completar una receta en mesa de cartografía (F12.13).
     * Si alguno de los ingredientes es de FraMCit, se cancela para evitar que se tome el item.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCartographyItem(CartographyItemEvent event) {
        CartographyInventory inventory = event.getInventory();
        if (hasFraMCitMap(inventory) || isFraMCitMap(event.getCurrentItem())) {
            event.setCancelled(true);
            event.setResult(Event.Result.DENY);
        }
    }

    /**
     * Previene interacción por click sobre el slot de resultado de la mesa de cartografía si hay un mapa de FraMCit.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory() instanceof CartographyInventory cartographyInventory) {
            if (event.getSlotType() == InventoryType.SlotType.RESULT || event.getRawSlot() == 2 || event.getSlot() == 2) {
                if (hasFraMCitMap(cartographyInventory) || isFraMCitMap(event.getCurrentItem())) {
                    event.setCancelled(true);
                    event.setResult(Event.Result.DENY);
                }
            }
        }
    }

    /**
     * Soporte para el bloque Fabricador (Crafter) de Minecraft 1.21+:
     * Si un dispensador/crafter intenta usar un mapa de FraMCit, se cancela la operación.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof Crafter crafter) {
            for (ItemStack item : crafter.getInventory().getContents()) {
                if (isFraMCitMap(item)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private static boolean hasFraMCitMap(CartographyInventory inventory) {
        if (inventory == null) {
            return false;
        }
        for (ItemStack item : inventory.getContents()) {
            if (isFraMCitMap(item)) {
                return true;
            }
        }
        return false;
    }
}
