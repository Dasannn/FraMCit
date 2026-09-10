package com.dasannn.framcit.image;

/**
 * Excepcion que representa fallos de validacion, limites o formato en el procesamiento de imagenes.
 * Los mensajes corresponden a los definidos en spec.md seccion 9.
 */
public class ImageValidationException extends RuntimeException {

    public ImageValidationException(String message) {
        super(message);
    }

    public ImageValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}
