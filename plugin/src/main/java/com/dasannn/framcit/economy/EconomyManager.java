package com.dasannn.framcit.economy;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.ServicesManager;

import java.util.logging.Logger;

/**
 * Gestor y resolutor central de economía de FraMCit (F10.4, F10.5).
 * Resuelve el servicio de Vault al arrancar y tolera las tres ausencias:
 * sin Vault, con Vault sin proveedor, o precio 0.
 */
public final class EconomyManager {

    private EconomyManager() {}

    public static EconomyService resolve(Plugin plugin, double price, Logger logger) {
        PluginManager pm = null;
        ServicesManager sm = null;
        if (plugin != null && plugin.getServer() != null) {
            pm = plugin.getServer().getPluginManager();
            sm = plugin.getServer().getServicesManager();
        }
        return resolve(pm, sm, price, logger);
    }

    public static EconomyService resolve(PluginManager pluginManager, ServicesManager servicesManager, double price, Logger logger) {
        // Tolerancia 1: Precio cero o negativo (F10.4, F10.6)
        if (price <= 0.0) {
            if (logger != null) {
                // F10.5: Una sola línea informativa
                logger.info("Cobro de imágenes desactivado: el precio configurado es 0 (gratis).");
            }
            return NoOpEconomyService.INSTANCE;
        }

        // Tolerancia 2: Sin Vault instalado en el servidor (F10.3, F10.4)
        if (pluginManager == null) {
            try {
                pluginManager = Bukkit.getPluginManager();
            } catch (Throwable ignored) {}
        }

        Plugin vaultPlugin = null;
        if (pluginManager != null) {
            try {
                vaultPlugin = pluginManager.getPlugin("Vault");
            } catch (Throwable ignored) {}
        }

        if (vaultPlugin == null || !vaultPlugin.isEnabled()) {
            if (logger != null) {
                // F10.5: Una sola línea informativa
                logger.info("Cobro de imágenes desactivado: Vault no está instalado en el servidor. Las imágenes son gratis.");
            }
            return NoOpEconomyService.INSTANCE;
        }

        // Verificar disponibilidad de la clase de Vault
        try {
            Class.forName("net.milkbowl.vault.economy.Economy");
        } catch (Throwable t) {
            if (logger != null) {
                logger.info("Cobro de imágenes desactivado: clases de Vault no disponibles en runtime. Las imágenes son gratis.");
            }
            return NoOpEconomyService.INSTANCE;
        }

        if (servicesManager == null) {
            try {
                servicesManager = Bukkit.getServicesManager();
            } catch (Throwable ignored) {}
        }

        return VaultHook.resolve(servicesManager, price, logger);
    }
}
