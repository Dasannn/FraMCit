package com.dasannn.framcit.store;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

/**
 * Normalizador de URLs para la clave de cache de FraMCit (F5.4).
 * - Host en minúsculas.
 * - Esquema en minúsculas (solo http y https permitidos).
 * - Puertos estándar eliminados (80 para http, 443 para https).
 * - Barra final eliminada para que dos URLs equivalentes produzcan la misma clave.
 * - Fragmentos eliminados (#ancla).
 * - Parámetros de consulta preservados.
 */
public final class UrlNormalizer {

    private UrlNormalizer() {}

    /**
     * Normaliza una URL para ser usada como clave única en el cache.
     *
     * @param rawUrl URL en texto sin procesar
     * @return URL normalizada
     * @throws IllegalArgumentException si la URL es nula, vacía o con sintaxis inválida
     */
    public static String normalize(String rawUrl) {
        if (rawUrl == null) {
            throw new IllegalArgumentException("URL cannot be null");
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("URL cannot be empty");
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL syntax: " + rawUrl, e);
        }

        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new IllegalArgumentException("URL must have a scheme: " + rawUrl);
        }
        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw new IllegalArgumentException("Disallowed scheme: " + scheme);
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("URL must have a valid host: " + rawUrl);
        }
        host = host.toLowerCase(Locale.ROOT);

        int port = uri.getPort();
        if ((scheme.equals("http") && port == 80) || (scheme.equals("https") && port == 443)) {
            port = -1;
        }

        String path = uri.getPath();
        if (path == null || path.isEmpty() || path.equals("/")) {
            path = "";
        } else {
            // Eliminar barras diagonales finales
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            if (path.equals("/")) {
                path = "";
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != -1) {
            sb.append(":").append(port);
        }
        sb.append(path);

        String query = uri.getRawQuery();
        if (query != null && !query.isBlank()) {
            sb.append("?").append(query);
        }

        return sb.toString();
    }
}
