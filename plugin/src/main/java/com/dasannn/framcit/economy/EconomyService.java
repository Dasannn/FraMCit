package com.dasannn.framcit.economy;

import org.bukkit.entity.Player;

/**
 * Capa de abstracción de economía que aísla Vault del resto del plugin (F10.4).
 *
 * El resto del plugin (comandos, servicios) no necesita saber si Vault existe o no:
 * solo pregunta si el jugador puede pagar (checkCanPay) y cobra al entregar (charge).
 */
public interface EconomyService {

    /**
     * Comprueba si el cobro de imágenes está activado (precio > 0 con proveedor disponible).
     */
    boolean isEnabled();

    /**
     * Retorna el precio configurado por imagen.
     */
    double getPrice();

    /**
     * Retorna el precio formateado por el plugin de economía, o "gratis" si no hay cobro activo.
     */
    String formatPrice();

    /**
     * Formatea un importe numérico con la moneda del servidor.
     */
    String format(double amount);

    /**
     * Comprueba si el jugador está exento de pago (nodo framcit.gratis, F10.12).
     * Los operadores NO están exentos por defecto.
     */
    boolean isExempt(Player player);

    /**
     * Comprueba ANTES de descargar si el jugador tiene saldo suficiente (F10.7, F10.11).
     * Si no hay cobro activo o el jugador está exento, devuelve Allowed(isFree = true).
     */
    EconomyCheckResult checkCanPay(Player player);

    /**
     * Cobra al jugador AL ENTREGAR el mapa (F10.8, F10.9, F10.10).
     * Si no hay cobro activo o el jugador está exento, devuelve Success(isFree = true, "gratis").
     */
    ChargeResult charge(Player player);
}
