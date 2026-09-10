package com.dasannn.framcit.render;

import org.bukkit.Chunk;
import org.bukkit.entity.ItemFrame;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.hanging.HangingBreakEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Objects;

/**
 * Listener de eventos de Bukkit para limpieza de estado de animación (F6.11).
 *
 * Evita fugas de memoria limpiando el estado inmediatamente cuando:
 * 1. Un jugador se desconecta o es expulsado.
 * 2. Un chunk con marcos animados se descarga del mundo.
 * 3. Un marco se rompe o sufre daño que desprende el item.
 */
public class FraMCitAnimationListener implements Listener {

    private final AnimationManager animationManager;

    public FraMCitAnimationListener(AnimationManager animationManager) {
        this.animationManager = Objects.requireNonNull(animationManager, "animationManager cannot be null");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        animationManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerKick(PlayerKickEvent event) {
        animationManager.onPlayerQuit(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChunkUnload(ChunkUnloadEvent event) {
        Chunk chunk = event.getChunk();
        if (chunk != null && chunk.getWorld() != null) {
            animationManager.onChunkUnload(chunk.getWorld().getName(), chunk.getX(), chunk.getZ());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame) {
            animationManager.onFrameBreak(itemFrame.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof ItemFrame itemFrame) {
            animationManager.onFrameBreak(itemFrame.getUniqueId());
        }
    }
}
