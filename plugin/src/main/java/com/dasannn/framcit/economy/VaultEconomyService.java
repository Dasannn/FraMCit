package com.dasannn.framcit.economy;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.entity.Player;

import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Adaptador de Vault para el servicio de economía de FraMCit (F10.4, F10.8, F10.15).
 */
public final class VaultEconomyService implements EconomyService {

    public static final String PERMISSION_GRATIS = "framcit.gratis";

    private final Economy economy;
    private final double price;
    private final Logger logger;

    public VaultEconomyService(Economy economy, double price, Logger logger) {
        this.economy = Objects.requireNonNull(economy, "economy provider cannot be null");
        this.price = Math.max(0.0, price);
        this.logger = logger;
    }

    public Economy getVaultEconomy() {
        return economy;
    }

    @Override
    public boolean isEnabled() {
        return price > 0.0;
    }

    @Override
    public double getPrice() {
        return price;
    }

    @Override
    public String formatPrice() {
        return format(price);
    }

    @Override
    public String format(double amount) {
        try {
            return economy.format(amount);
        } catch (Throwable t) {
            return String.valueOf(amount);
        }
    }

    @Override
    public boolean isExempt(Player player) {
        if (player == null) {
            return false;
        }
        // F10.12 / Spec 002 6: framcit.gratis exime del pago.
        // Los operadores NO están exentos automáticamente (default: false).
        return player.hasPermission(PERMISSION_GRATIS);
    }

    @Override
    public EconomyCheckResult checkCanPay(Player player) {
        if (price <= 0.0 || isExempt(player)) {
            return new EconomyCheckResult.Allowed(true);
        }

        try {
            if (economy.has(player, price)) {
                return new EconomyCheckResult.Allowed(false);
            }

            double balance = 0.0;
            try {
                balance = economy.getBalance(player);
            } catch (Throwable t) {
                if (logger != null) {
                    logger.log(Level.WARNING, "Error al consultar saldo de " + player.getName(), t);
                }
            }

            return new EconomyCheckResult.InsufficientFunds(format(price), format(balance));
        } catch (Throwable t) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Excepción al comprobar saldo de " + player.getName(), t);
            }
            double balance = 0.0;
            try {
                balance = economy.getBalance(player);
            } catch (Throwable ignored) {}
            return new EconomyCheckResult.InsufficientFunds(format(price), format(balance));
        }
    }

    @Override
    public ChargeResult charge(Player player) {
        if (price <= 0.0 || isExempt(player)) {
            return new ChargeResult.Success(true, "gratis");
        }

        try {
            // Hueco temporal (F10.9): comprobar si aún le alcanza antes de retirar
            if (!economy.has(player, price)) {
                return new ChargeResult.InsufficientFunds();
            }

            EconomyResponse response = economy.withdrawPlayer(player, price);
            if (response == null) {
                return new ChargeResult.Failure("Respuesta nula del proveedor de economía", null);
            }

            if (!response.transactionSuccess()) {
                // Verificar si falló específicamente porque ya no tenía saldo suficiente
                if (!economy.has(player, price)) {
                    return new ChargeResult.InsufficientFunds();
                }
                String err = response.errorMessage != null ? response.errorMessage : "Transacción rechazada";
                return new ChargeResult.Failure(err, null);
            }

            return new ChargeResult.Success(false, format(price));
        } catch (Throwable t) {
            // F10.10: Excepción en el plugin de economía al cobrar
            return new ChargeResult.Failure("Excepción lanzada por el plugin de economía: " + t.getMessage(), t);
        }
    }
}
