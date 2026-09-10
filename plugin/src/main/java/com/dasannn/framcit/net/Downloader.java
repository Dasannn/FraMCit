package com.dasannn.framcit.net;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Cliente de descarga HTTP seguro segun Constitution 6 y Spec 5.
 *
 * Caracteristicas:
 * - Emplea java.net.http.HttpClient con timeouts separados de conexion y lectura.
 * - Sigue redirecciones a mano (Redirect.NEVER) revalidando con UrlGuard en CADA salto.
 * - Corte estricto por bytes contando en el stream; nunca confia en Content-Length.
 * - Devuelve el resultado como valor (DownloadResult), nunca como excepcion suelta.
 */
public class Downloader {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(15);
    public static final long MAX_DOWNLOAD_SIZE_BYTES = 5L * 1024L * 1024L; // 5 MB
    public static final int MAX_REDIRECTS = 3;

    private final UrlGuard urlGuard;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final long maxSizeBytes;
    private final int maxRedirects;
    private final HttpClient httpClient;

    public Downloader() {
        this(new UrlGuard());
    }

    public Downloader(UrlGuard urlGuard) {
        this(urlGuard, DEFAULT_CONNECT_TIMEOUT, DEFAULT_READ_TIMEOUT, MAX_DOWNLOAD_SIZE_BYTES, MAX_REDIRECTS);
    }

    public Downloader(UrlGuard urlGuard, Duration connectTimeout, Duration readTimeout, long maxSizeBytes, int maxRedirects) {
        this.urlGuard = Objects.requireNonNull(urlGuard, "urlGuard cannot be null");
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout cannot be null");
        this.readTimeout = Objects.requireNonNull(readTimeout, "readTimeout cannot be null");
        this.maxSizeBytes = maxSizeBytes;
        this.maxRedirects = maxRedirects;

        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(connectTimeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /**
     * Descarga una imagen desde la URL en texto.
     *
     * @param rawUrl la URL a descargar.
     * @return DownloadResult con los bytes descargados o el error correspondiente.
     */
    public DownloadResult download(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return DownloadResult.failure(new DownloadError.MalformedUrl(rawUrl));
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            return DownloadResult.failure(new DownloadError.MalformedUrl(rawUrl));
        }

        return download(uri);
    }

    /**
     * Descarga una imagen desde el URI indicado.
     *
     * @param initialUri el URI inicial a descargar.
     * @return DownloadResult con los bytes descargados o el error correspondiente.
     */
    public DownloadResult download(URI initialUri) {
        if (initialUri == null) {
            return DownloadResult.failure(new DownloadError.MalformedUrl(null));
        }

        // 1. Validar URI inicial con UrlGuard
        Optional<DownloadError> guardError = urlGuard.validate(initialUri);
        if (guardError.isPresent()) {
            return DownloadResult.failure(guardError.get());
        }

        URI currentUri = initialUri;
        int redirectCount = 0;
        long deadlineMillis = System.currentTimeMillis() + readTimeout.toMillis();

        while (true) {
            long remainingMillis = deadlineMillis - System.currentTimeMillis();
            if (remainingMillis <= 0) {
                return DownloadResult.failure(new DownloadError.Timeout("read"));
            }

            Duration requestTimeout = Duration.ofMillis(remainingMillis);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(currentUri)
                .timeout(requestTimeout)
                .header("User-Agent", "FraMCit/1.0")
                .GET()
                .build();

            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            } catch (HttpConnectTimeoutException e) {
                return DownloadResult.failure(new DownloadError.Timeout("connect"));
            } catch (HttpTimeoutException e) {
                return DownloadResult.failure(new DownloadError.Timeout("read"));
            } catch (UnknownHostException e) {
                return DownloadResult.failure(new DownloadError.UnresolvableHost(currentUri.getHost()));
            } catch (ConnectException e) {
                return DownloadResult.failure(new DownloadError.ConnectionFailed(e.getMessage()));
            } catch (IOException e) {
                if (e.getCause() instanceof HttpTimeoutException || (e.getMessage() != null && e.getMessage().contains("timed out"))) {
                    return DownloadResult.failure(new DownloadError.Timeout("read"));
                }
                return DownloadResult.failure(new DownloadError.ConnectionFailed(e.getMessage()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return DownloadResult.failure(new DownloadError.ConnectionFailed("Descarga interrumpida."));
            }

            int statusCode = response.statusCode();

            // Gestion manual de redirecciones (301, 302, 303, 307, 308)
            if (isRedirectStatus(statusCode)) {
                try {
                    response.body().close();
                } catch (IOException ignored) {}

                redirectCount++;
                if (redirectCount > maxRedirects) {
                    return DownloadResult.failure(new DownloadError.TooManyRedirects(maxRedirects));
                }

                Optional<String> locationOpt = response.headers().firstValue("Location");
                if (locationOpt.isEmpty() || locationOpt.get().isBlank()) {
                    return DownloadResult.failure(new DownloadError.HttpError(statusCode));
                }

                URI nextUri;
                try {
                    nextUri = currentUri.resolve(locationOpt.get().trim());
                } catch (IllegalArgumentException e) {
                    return DownloadResult.failure(new DownloadError.MalformedUrl(locationOpt.get()));
                }

                // REQUISITO DE SEGURIDAD F2.3: Revalidar destino en cada salto con UrlGuard
                Optional<DownloadError> redirectGuardError = urlGuard.validate(nextUri);
                if (redirectGuardError.isPresent()) {
                    return DownloadResult.failure(redirectGuardError.get());
                }

                currentUri = nextUri;
                continue;
            }

            // Validar codigo de estado HTTP exitoso (2xx)
            if (statusCode < 200 || statusCode >= 300) {
                try {
                    response.body().close();
                } catch (IOException ignored) {}
                return DownloadResult.failure(new DownloadError.HttpError(statusCode));
            }

            // Lectura del stream con corte estricto por bytes contados (F2.5).
            // Segun Constitution 6.3, no se confia en Content-Length; el corte se aplica durante el stream.
            String contentType = response.headers().firstValue("Content-Type").orElse(null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            long totalBytes = 0;

            try (InputStream is = response.body()) {
                int read;
                while ((read = is.read(buffer)) != -1) {
                    totalBytes += read;
                    if (totalBytes > maxSizeBytes) {
                        return DownloadResult.failure(new DownloadError.TooLarge(maxSizeBytes));
                    }
                    baos.write(buffer, 0, read);
                }
            } catch (HttpTimeoutException e) {
                return DownloadResult.failure(new DownloadError.Timeout("read"));
            } catch (IOException e) {
                if (e.getCause() instanceof HttpTimeoutException || (e.getMessage() != null && e.getMessage().contains("timed out"))) {
                    return DownloadResult.failure(new DownloadError.Timeout("read"));
                }
                return DownloadResult.failure(new DownloadError.ConnectionFailed(e.getMessage()));
            }

            return DownloadResult.success(baos.toByteArray(), contentType, currentUri);
        }
    }

    private static boolean isRedirectStatus(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    public UrlGuard getUrlGuard() {
        return urlGuard;
    }

    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    public Duration getReadTimeout() {
        return readTimeout;
    }

    public long getMaxSizeBytes() {
        return maxSizeBytes;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }
}
