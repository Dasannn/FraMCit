package com.dasannn.framcit.service;

import com.dasannn.framcit.image.ConvertedImage;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Resultado unificado del pipeline de procesamiento de imagenes.
 * Sirve como puente de errores tipado, unificando fallos de red (DownloadError)
 * y de decodificacion/conversion (ImageValidationException) antes de volver al hilo principal.
 */
public sealed interface ImageProcessResult permits ImageProcessResult.Success, ImageProcessResult.Failure {

    boolean isSuccess();

    default Optional<ConvertedImage> convertedImage() {
        return this instanceof Success s ? Optional.of(s.image()) : Optional.empty();
    }

    default Optional<String> errorMessage() {
        return this instanceof Failure f ? Optional.of(f.userMessage()) : Optional.empty();
    }

    record Success(
        ConvertedImage image,
        URI finalUri,
        int byteCount
    ) implements ImageProcessResult {
        public Success {
            Objects.requireNonNull(image, "image cannot be null");
            Objects.requireNonNull(finalUri, "finalUri cannot be null");
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    record Failure(
        String userMessage,
        Throwable cause
    ) implements ImageProcessResult {
        public Failure {
            Objects.requireNonNull(userMessage, "userMessage cannot be null");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    static ImageProcessResult success(ConvertedImage image, URI finalUri, int byteCount) {
        return new Success(image, finalUri, byteCount);
    }

    static ImageProcessResult failure(String userMessage) {
        return new Failure(userMessage, null);
    }

    static ImageProcessResult failure(String userMessage, Throwable cause) {
        return new Failure(userMessage, cause);
    }
}
