package com.dasannn.framcit.net;

import com.dasannn.framcit.command.Messages;

/**
 * Representa los errores posibles durante la validación y descarga de una URL.
 * Cada variante encapsula los detalles del fallo y provee el mensaje de usuario
 * exacto según la sección 9 del spec, sin filtrar IPs internas, rutas ni trazas.
 *
 * Utiliza Messages para permitir la personalización de textos sin recompilar (F7.3).
 */
public sealed interface DownloadError {

    /**
     * Mensaje en español para mostrar al jugador en el chat.
     */
    String userMessage();

    /**
     * La URL proporcionada no tiene una sintaxis válida o carece de host.
     */
    record MalformedUrl(String rawUrl) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_MALFORMED_URL;
        }
    }

    /**
     * El esquema de la URL no es http ni https (ej. file:, ftp:, jar:, data:).
     */
    record DisallowedScheme(String scheme) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_DISALLOWED_SCHEME;
        }
    }

    /**
     * El destino resuelto es loopback, link-local, privado o no público (SSRF bloqueado).
     */
    record BlockedDestination(String host) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_BLOCKED_DESTINATION;
        }
    }

    /**
     * No se pudo resolver el host por DNS o el host no existe.
     */
    record UnresolvableHost(String host) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_UNRESOLVABLE_HOST;
        }
    }

    /**
     * Fallo al establecer o mantener la conexión de red.
     */
    record ConnectionFailed(String reason) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_CONNECTION_FAILED;
        }
    }

    /**
     * El servidor remoto respondió con un código de error HTTP (4xx, 5xx, etc.).
     */
    record HttpError(int statusCode) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.httpError(statusCode);
        }
    }

    /**
     * Se superó el tiempo de espera de conexión o de lectura.
     */
    record Timeout(String phase) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_TIMEOUT;
        }
    }

    /**
     * El contenido descargado supera el límite de descarga configurado.
     */
    record TooLarge(long maxBytes) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_TOO_LARGE;
        }
    }

    /**
     * Se superó el límite configurado de redirecciones continuas.
     */
    record TooManyRedirects(int maxRedirects) implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.genericHttpError();
        }
    }

    /**
     * La cola de descargas está llena; se rechaza la petición limpiamente.
     */
    record QueueFull() implements DownloadError {
        @Override
        public String userMessage() {
            return Messages.ERROR_QUEUE_FULL;
        }
    }
}
