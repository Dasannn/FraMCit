package com.dasannn.framcit.economy;

import org.bukkit.entity.Player;

/**
 * Implementación no operativa cuando no hay economía (sin Vault, sin proveedor o precio 0).
 * En todos los casos responde que sí de forma gratuita y sin tocar saldos (F10.4).
 */
public final class NoOpEconomyService implements EconomyService {

    public static final NoOpEconomyService INSTANCE = new NoOpEconomyService();

    public NoOpEconomyService() {}

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public double getPrice() {
        return 0.0;
    }

    @Override
    public String formatPrice() {
        return "gratis";
    }

    @Override
    public String format(double amount) {
        return String.valueOf(amount);
    }

    @Override
    public boolean isExempt(Player player) {
        return true;
    }

    @Override
    public EconomyCheckResult checkCanPay(Player player) {
        return new EconomyCheckResult.Allowed(true);
    }

    @Override
    public ChargeResult charge(Player player) {
        return new ChargeResult.Success(true, "gratis");
    }
}
