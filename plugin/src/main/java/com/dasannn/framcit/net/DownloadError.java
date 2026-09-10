package com.dasannn.framcit.net;

/**
 * Representa los errores posibles durante la validacion y descarga de una URL.
 * Cada variante encapsula los detalles del fallo y provee el mensaje de usuario
 * exacto segun la seccion 9 del spec, sin filtrar IPs internas, rutas ni trazas.
 */
public sealed interface DownloadError {

    /**
     * Mensaje en espanol para mostrar al jugador en el chat.
     */
    String userMessage();

    /**
     * La URL proporcionada no tiene una sintaxis valida o carece de host.
     */
    record MalformedUrl(String rawUrl) implements DownloadError {
        @Override
        public String userMessage() {
            return "Esa URL no es valida.";
        }
    }

    /**
     * El esquema de la URL no es http ni https (ej. file:, ftp:, jar:, data:).
     */
    record DisallowedScheme(String scheme) implements DownloadError {
        @Override
        public String userMessage() {
            return "Solo se aceptan enlaces http y https.";
        }
    }

    /**
     * El destino resuelto es loopback, link-local, privado o no publico (SSRF bloqueado).
     */
    record BlockedDestination(String host) implements DownloadError {
        @Override
        public String userMessage() {
            return "URL no permitida.";
        }
    }

    /**
     * No se pudo resolver el host por DNS o el host no existe.
     */
    record UnresolvableHost(String host) implements DownloadError {
        @Override
        public String userMessage() {
            return "No se pudo conectar con ese sitio.";
        }
    }

    /**
     * Fallo al establecer o mantener la conexion de red.
     */
    record ConnectionFailed(String reason) implements DownloadError {
        @Override
        public String userMessage() {
            return "No se pudo conectar con ese sitio.";
        }
    }

    /**
     * El servidor remoto respondio con un codigo de error HTTP (4xx, 5xx, etc.).
     */
    record HttpError(int statusCode) implements DownloadError {
        @Override
        public String userMessage() {
            return "El sitio respondio con error (" + statusCode + "). El enlace puede haber caducado.";
        }
    }

    /**
     * Se supero el tiempo de espera de conexion (5s) o de lectura (15s).
     */
    record Timeout(String phase) implements DownloadError {
        @Override
        public String userMessage() {
            return "El sitio tardo demasiado en responder.";
        }
    }

    /**
     * El contenido descargado supera el limite duro de 5 MB.
     */
    record TooLarge(long maxBytes) implements DownloadError {
        @Override
        public String userMessage() {
            return "La imagen supera el limite de 5 MB.";
        }
    }

    /**
     * Se supero el limite configurado de redirecciones continuas (3).
     */
    record TooManyRedirects(int maxRedirects) implements DownloadError {
        @Override
        public String userMessage() {
            return "El sitio respondio con error. El enlace puede haber caducado.";
        }
    }

    /**
     * La cola de descargas esta llena; se rechaza la peticion limpiamente.
     */
    record QueueFull() implements DownloadError {
        @Override
        public String userMessage() {
            return "Hay varias descargas en curso, espera un momento.";
        }
    }
}
