package com.dasannn.framcit.net;

import java.net.URI;
import java.util.Objects;
import java.util.Optional;

/**
 * Representa el resultado de una operacion de descarga.
 * Lleva el fallo como un valor tipado inmutable (DownloadError) hasta el invocador
 * en lugar de lanzar excepciones sueltas, cumpliendo con F2.7.
 */
public sealed interface DownloadResult permits DownloadResult.Success, DownloadResult.Failure {

    boolean isSuccess();

    default Optional<byte[]> data() {
        return this instanceof Success s ? Optional.of(s.bytes()) : Optional.empty();
    }

    default Optional<DownloadError> error() {
        return this instanceof Failure f ? Optional.of(f.err()) : Optional.empty();
    }

    record Success(byte[] bytes, String contentType, URI finalUri) implements DownloadResult {
        public Success {
            Objects.requireNonNull(bytes, "bytes cannot be null");
            Objects.requireNonNull(finalUri, "finalUri cannot be null");
            bytes = bytes.clone();
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public boolean isSuccess() {
            return true;
        }
    }

    record Failure(DownloadError err) implements DownloadResult {
        public Failure {
            Objects.requireNonNull(err, "error cannot be null");
        }

        @Override
        public boolean isSuccess() {
            return false;
        }
    }

    static DownloadResult success(byte[] bytes, String contentType, URI finalUri) {
        return new Success(bytes, contentType, finalUri);
    }

    static DownloadResult failure(DownloadError error) {
        return new Failure(error);
    }
}
