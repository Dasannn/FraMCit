package com.dasannn.framcit.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.util.logging.Logger;

/**
 * Aislamiento estricto de llamadas a la API de Vault.
 * Esta clase SOLO se carga en la JVM si Vault está presente en el servidor.
 */
final class VaultHook {

    private VaultHook() {}

    static EconomyService resolve(ServicesManager servicesManager, double price, Logger logger) {
        if (servicesManager == null) {
            if (logger != null) {
                logger.info("Cobro de imágenes desactivado: gestor de servicios no disponible. Las imágenes son gratis.");
            }
            return NoOpEconomyService.INSTANCE;
        }

        // Tolerancia: Con Vault pero sin plugin de economía registrado (F10.4)
        RegisteredServiceProvider<Economy> rsp = servicesManager.getRegistration(Economy.class);
        if (rsp == null || rsp.getProvider() == null) {
            if (logger != null) {
                logger.info("Cobro de imágenes desactivado: no hay ningún plugin de economía registrado en Vault. Las imágenes son gratis.");
            }
            return NoOpEconomyService.INSTANCE;
        }

        Economy econ = rsp.getProvider();
        if (logger != null) {
            // F10.5: Una sola línea en el log al arrancar diciendo si el cobro está activo y por qué
            logger.info(String.format("Cobro de imágenes activo: %s por imagen (proveedor: %s).", econ.format(price), econ.getName()));
        }
        return new VaultEconomyService(econ, price, logger);
    }
}
