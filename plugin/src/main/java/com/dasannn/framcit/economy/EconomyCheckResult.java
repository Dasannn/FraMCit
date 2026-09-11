package com.dasannn.framcit.economy;

/**
 * Resultado de la comprobación previa de saldo (F10.7, F10.11).
 */
public sealed interface EconomyCheckResult {

    /**
     * El jugador puede proceder: o la imagen es gratis (sin cobro / exento) o tiene saldo suficiente.
     *
     * @param isFree true si la imagen es gratuita o el jugador está exento; false si tendrá que pagar al entregar.
     */
    record Allowed(boolean isFree) implements EconomyCheckResult {}

    /**
     * El jugador no tiene saldo suficiente para pagar la imagen.
     *
     * @param formattedPrice Precio formateado por el plugin de economía.
     * @param formattedBalance Saldo actual formateado del jugador.
     */
    record InsufficientFunds(String formattedPrice, String formattedBalance) implements EconomyCheckResult {}
}
