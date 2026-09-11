package com.dasannn.framcit.economy;

/**
 * Resultado del intento de cobro al entregar el mapa (F10.8, F10.9, F10.10).
 */
public sealed interface ChargeResult {

    /**
     * El cobro se realizó con éxito o la imagen fue gratuita.
     *
     * @param isFree true si no hubo cobro (precio 0, sin economía o framcit.gratis).
     * @param formattedAmount Importe formateado por el plugin de economía, o "gratis".
     */
    record Success(boolean isFree, String formattedAmount) implements ChargeResult {}

    /**
     * El jugador perdió el saldo entre la comprobación previa y el momento de entrega (F10.9).
     */
    record InsufficientFunds() implements ChargeResult {}

    /**
     * El plugin de economía devolvió error o lanzó una excepción al intentar cobrar (F10.10).
     *
     * @param errorMessage Descripción del fallo.
     * @param cause Causa de la excepción si la hubo, o null.
     */
    record Failure(String errorMessage, Throwable cause) implements ChargeResult {}
}
