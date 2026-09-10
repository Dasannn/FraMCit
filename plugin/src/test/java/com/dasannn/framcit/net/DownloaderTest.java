package com.dasannn.framcit.net;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DownloaderTest {

    private static HttpServer server;
    private static int serverPort;
    private static final AtomicBoolean redirectHopReached = new AtomicBoolean(false);

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverPort = server.getAddress().getPort();

        // Endpoint exitoso normal
        server.createContext("/ok-image", exchange -> {
            byte[] body = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A }; // PNG magic bytes
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(body);
            }
        });

        // F2.9: Endpoint que redirige a IP privada (10.0.0.1)
        server.createContext("/redirect-to-private", exchange -> {
            redirectHopReached.set(true);
            exchange.getResponseHeaders().set("Location", "http://10.0.0.1/private.png");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // F2.10: Endpoint que declara Content-Length: 1000 y manda mas bytes
        server.createContext("/content-length-lie", exchange -> {
            exchange.getResponseHeaders().set("Content-Length", "1000");
            exchange.sendResponseHeaders(200, 1000);
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] data = new byte[1000];
                os.write(data);
            }
        });

        // F2.10: Endpoint de streaming chunked que manda mas de 5 MB (6.4 MB)
        server.createContext("/chunked-bomb", exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.sendResponseHeaders(200, 0); // 0 = chunked encoding
            try (OutputStream os = exchange.getResponseBody()) {
                byte[] chunk = new byte[64 * 1024]; // 64 KB
                for (int i = 0; i < 100; i++) {     // 100 * 64 KB = 6.4 MB
                    os.write(chunk);
                }
            } catch (IOException ignored) {
                // El cliente cerro la conexion al cortar por bytes, comportamiento esperado
            }
        });

        // F2.11: Endpoint que acepta la conexion pero nunca responde (timeout)
        server.createContext("/slow-hang", exchange -> {
            try {
                Thread.sleep(10000);
            } catch (InterruptedException ignored) {}
        });

        // Bucle de redirecciones continuas
        server.createContext("/redirect-loop", exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + serverPort + "/redirect-loop");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });

        // Error HTTP 404
        server.createContext("/not-found", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });

        // Error HTTP 500
        server.createContext("/server-error", exchange -> {
            exchange.sendResponseHeaders(500, -1);
            exchange.close();
        });

        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    // =========================================================================
    // F2.9 [SEC] Test: host publico que redirige a IP privada: debe rechazarse EN el salto
    // =========================================================================

    @Test
    @DisplayName("F2.9 [SEC]: Host que redirige a una IP privada debe rechazarse EN el salto")
    void shouldRejectRedirectToPrivateIpAtTheHop() {
        redirectHopReached.set(false);
        // Usamos allowLoopback=true para que el servidor local de prueba actue como el host origen
        UrlGuard testGuard = new UrlGuard(true);
        Downloader downloader = new Downloader(testGuard);

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/redirect-to-private");

        assertFalse(result.isSuccess(), "La descarga debe fallar por destino bloqueado");
        assertTrue(redirectHopReached.get(), "La primera peticion debio llegar al servidor para recibir la redireccion");
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.error().get());
        assertEquals("URL no permitida.", result.error().get().userMessage());
    }

    // =========================================================================
    // F2.10 [SEC] Test: servidor local que declara Content-Length: 1000 y manda
    // mucho mas: debe cortarse en el limite
    // =========================================================================

    @Test
    @DisplayName("F2.10 [SEC]: Servidor que declara Content-Length: 1000 y manda mas que el limite debe cortarse en el stream")
    void shouldCutOffStreamWhenDeclaredContentLengthExceedsConfiguredLimit() {
        UrlGuard testGuard = new UrlGuard(true);
        // Configuramos el limite maximo en 500 bytes para probar que al recibir 1000 se corta en el stream
        Downloader downloader = new Downloader(testGuard, Duration.ofSeconds(2), Duration.ofSeconds(5), 500, 3);

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/content-length-lie");

        assertFalse(result.isSuccess(), "Debe fallar por superar el limite de bytes");
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.TooLarge.class, result.error().get());
        assertEquals("La imagen supera el limite de 5 MB.", result.error().get().userMessage());
    }

    @Test
    @DisplayName("F2.10 [SEC]: Servidor que transmite mas de 5 MB en stream debe cortarse al limite de 5 MB")
    void shouldCutOffStreamWhenPayloadExceeds5Mb() {
        UrlGuard testGuard = new UrlGuard(true);
        Downloader downloader = new Downloader(testGuard); // Limite por defecto: 5 MB

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/chunked-bomb");

        assertFalse(result.isSuccess(), "Debe fallar al superar los 5 MB");
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.TooLarge.class, result.error().get());
        assertEquals("La imagen supera el limite de 5 MB.", result.error().get().userMessage());
    }

    // =========================================================================
    // F2.11 Test: timeout con servidor que acepta y nunca responde
    // =========================================================================

    @Test
    @DisplayName("F2.11: Timeout con servidor que acepta conexion y nunca responde")
    void shouldTimeoutWhenServerHangs() {
        UrlGuard testGuard = new UrlGuard(true);
        // Timeout corto de 300 ms para ejecutar la prueba de forma agil
        Downloader fastTimeoutDownloader = new Downloader(testGuard, Duration.ofMillis(300), Duration.ofMillis(300), 5 * 1024 * 1024, 3);

        long start = System.currentTimeMillis();
        DownloadResult result = fastTimeoutDownloader.download("http://127.0.0.1:" + serverPort + "/slow-hang");
        long duration = System.currentTimeMillis() - start;

        assertFalse(result.isSuccess(), "Debe fallar por timeout");
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.Timeout.class, result.error().get());
        assertEquals("El sitio tardo demasiado en responder.", result.error().get().userMessage());
        assertTrue(duration < 3000, "El timeout debe activarse rapidamente y no colgar el proceso");
    }

    // =========================================================================
    // Redirecciones y codigos de error HTTP
    // =========================================================================

    @Test
    @DisplayName("Falla limpiamente al superar el limite de 3 redirecciones consecutivas")
    void shouldFailWhenRedirectLimitExceeded() {
        UrlGuard testGuard = new UrlGuard(true);
        Downloader downloader = new Downloader(testGuard);

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/redirect-loop");

        assertFalse(result.isSuccess());
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.TooManyRedirects.class, result.error().get());
    }

    @Test
    @DisplayName("Mapea codigo HTTP 404 a HttpError")
    void shouldReturnHttpErrorOn404() {
        UrlGuard testGuard = new UrlGuard(true);
        Downloader downloader = new Downloader(testGuard);

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/not-found");

        assertFalse(result.isSuccess());
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.HttpError.class, result.error().get());
        assertEquals(404, ((DownloadError.HttpError) result.error().get()).statusCode());
        assertEquals("El sitio respondio con error (404). El enlace puede haber caducado.", result.error().get().userMessage());
    }

    @Test
    @DisplayName("Mapea codigo HTTP 500 a HttpError")
    void shouldReturnHttpErrorOn500() {
        UrlGuard testGuard = new UrlGuard(true);
        Downloader downloader = new Downloader(testGuard);

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/server-error");

        assertFalse(result.isSuccess());
        assertTrue(result.error().isPresent());
        assertInstanceOf(DownloadError.HttpError.class, result.error().get());
        assertEquals(500, ((DownloadError.HttpError) result.error().get()).statusCode());
    }

    @Test
    @DisplayName("Descarga exitosa devuelve bytes, content-type y finalUri")
    void shouldDownloadSuccessfully() {
        UrlGuard testGuard = new UrlGuard(true);
        Downloader downloader = new Downloader(testGuard);

        DownloadResult result = downloader.download("http://127.0.0.1:" + serverPort + "/ok-image");

        assertTrue(result.isSuccess());
        assertTrue(result.data().isPresent());
        assertEquals(8, result.data().get().length);
        assertInstanceOf(DownloadResult.Success.class, result);
        DownloadResult.Success success = (DownloadResult.Success) result;
        assertEquals("image/png", success.contentType());
        assertEquals("http://127.0.0.1:" + serverPort + "/ok-image", success.finalUri().toString());
    }
}
