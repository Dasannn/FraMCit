package com.dasannn.framcit.service;

import com.dasannn.framcit.command.Messages;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.net.DownloadError;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.net.DownloadResult;
import com.dasannn.framcit.net.Downloader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class ImageServiceTest {

    private DownloadQueue queue;

    @BeforeEach
    void setUp() {
        queue = new DownloadQueue(2, 5);
    }

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.close();
        }
    }

    private static byte[] createTestImage(String format, int width, int height) throws IOException {
        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, width, height);
        g.dispose();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }

    private static byte[] createMinimalAnimatedGif(int frameCount) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            BufferedImage frameImg = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = frameImg.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, 2, 2);
            g.dispose();

            for (int i = 0; i < frameCount; i++) {
                writer.writeToSequence(new IIOImage(frameImg, null, null), null);
            }

            writer.endWriteSequence();
            writer.dispose();
        }
        return baos.toByteArray();
    }

    /**
     * Downloader de prueba que devuelve un DownloadResult controlado sin peticiones HTTP reales.
     */
    static class StubDownloader extends Downloader {
        private final DownloadResult resultToReturn;

        StubDownloader(DownloadResult resultToReturn) {
            this.resultToReturn = resultToReturn;
        }

        @Override
        public DownloadResult download(String rawUrl) {
            return resultToReturn;
        }

        @Override
        public DownloadResult download(URI uri) {
            return resultToReturn;
        }
    }

    @Test
    @DisplayName("F4.1 - Exito: descarga, decodificacion y conversion de PNG a mapa")
    void testProcessSuccessPng() throws Exception {
        byte[] pngBytes = createTestImage("png", 64, 64);
        URI uri = URI.create("https://example.com/cat.png");
        StubDownloader stubDownloader = new StubDownloader(DownloadResult.success(pngBytes, "image/png", uri));

        ImageService service = new ImageService(stubDownloader, queue);
        ImageProcessResult result = service.process("https://example.com/cat.png").get();

        assertTrue(result.isSuccess());
        assertTrue(result.convertedImage().isPresent());
        assertEquals(ImageFormat.PNG, result.convertedImage().get().getFormat());
        assertEquals(1, result.convertedImage().get().getFrameCount());
        assertFalse(result.convertedImage().get().isAnimated());
        assertEquals(16384, result.convertedImage().get().getFrames().getFirst().getMapBytes().length);
    }

    @Test
    @DisplayName("F4.1 - Exito: descarga, decodificacion y conversion de JPEG a mapa")
    void testProcessSuccessJpeg() throws Exception {
        byte[] jpegBytes = createTestImage("jpeg", 80, 80);
        URI uri = URI.create("https://example.com/dog.jpg");
        StubDownloader stubDownloader = new StubDownloader(DownloadResult.success(jpegBytes, "image/jpeg", uri));

        ImageService service = new ImageService(stubDownloader, queue);
        ImageProcessResult result = service.process("https://example.com/dog.jpg").get();

        assertTrue(result.isSuccess());
        assertEquals(ImageFormat.JPEG, result.convertedImage().orElseThrow().getFormat());
    }

    @Test
    @DisplayName("F4.1 - Exito: descarga, decodificacion y conversion de BMP a mapa")
    void testProcessSuccessBmp() throws Exception {
        byte[] bmpBytes = createTestImage("bmp", 50, 50);
        URI uri = URI.create("https://example.com/pixel.bmp");
        StubDownloader stubDownloader = new StubDownloader(DownloadResult.success(bmpBytes, "image/bmp", uri));

        ImageService service = new ImageService(stubDownloader, queue);
        ImageProcessResult result = service.process("https://example.com/pixel.bmp").get();

        assertTrue(result.isSuccess());
        assertEquals(ImageFormat.BMP, result.convertedImage().orElseThrow().getFormat());
    }

    @Test
    @DisplayName("F4.1 & F4.10 - Exito: descarga, decodificacion y conversion de GIF animado")
    void testProcessSuccessAnimatedGif() throws Exception {
        byte[] gifBytes = createMinimalAnimatedGif(3);
        URI uri = URI.create("https://example.com/anim.gif");
        StubDownloader stub = new StubDownloader(DownloadResult.success(gifBytes, "image/gif", uri));

        ImageService service = new ImageService(stub, queue);
        ImageProcessResult result = service.process("https://example.com/anim.gif").get();

        assertTrue(result.isSuccess());
        assertEquals(ImageFormat.GIF, result.convertedImage().orElseThrow().getFormat());
        assertTrue(result.convertedImage().orElseThrow().isAnimated());
        assertEquals(3, result.convertedImage().orElseThrow().getFrameCount());
    }

    // =========================================================================
    // PUENTE DE ERRORES: Verificacion de cada error de la tabla de spec.md seccion 9
    // =========================================================================

    @Test
    @DisplayName("F4.8 & F4.14 - Error: URL malformada")
    void testErrorMalformedUrl() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.MalformedUrl("bad-url")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("bad-url").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_MALFORMED_URL, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Esquema no permitido")
    void testErrorDisallowedScheme() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.DisallowedScheme("ftp")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("ftp://example.com/img.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_DISALLOWED_SCHEME, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Destino bloqueado (URL no permitida, sin filtrar IPs)")
    void testErrorBlockedDestination() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.BlockedDestination("127.0.0.1")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("http://127.0.0.1/img.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_BLOCKED_DESTINATION, result.errorMessage().orElseThrow());
        assertFalse(result.errorMessage().orElseThrow().contains("127.0.0.1"), "No debe filtrar la IP");
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Host inexistente / no resolvible")
    void testErrorUnresolvableHost() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.UnresolvableHost("no-such-host.xyz")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://no-such-host.xyz/img.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_UNRESOLVABLE_HOST, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Fallo de conexion")
    void testErrorConnectionFailed() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.ConnectionFailed("Connection reset")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/img.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_CONNECTION_FAILED, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: HTTP 404 / 500")
    void testErrorHttp() throws Exception {
        StubDownloader stub404 = new StubDownloader(DownloadResult.failure(new DownloadError.HttpError(404)));
        ImageService service404 = new ImageService(stub404, queue);
        ImageProcessResult result404 = service404.process("https://example.com/img.png").get();
        assertEquals(Messages.httpError(404), result404.errorMessage().orElseThrow());

        StubDownloader stub500 = new StubDownloader(DownloadResult.failure(new DownloadError.HttpError(500)));
        ImageService service500 = new ImageService(stub500, queue);
        ImageProcessResult result500 = service500.process("https://example.com/img.png").get();
        assertEquals(Messages.httpError(500), result500.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Demasiadas redirecciones")
    void testErrorTooManyRedirects() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.TooManyRedirects(3)));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/loop").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.genericHttpError(), result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Timeout")
    void testErrorTimeout() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.Timeout("read")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/img.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_TIMEOUT, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Imagen supera el limite de 5 MB")
    void testErrorTooLarge() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.TooLarge(5 * 1024 * 1024)));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/huge.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_TOO_LARGE, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Cola de descargas llena")
    void testErrorQueueFull() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.failure(new DownloadError.QueueFull()));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/img.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_QUEUE_FULL, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: No es imagen (bytes vacios)")
    void testErrorNotAnImage() throws Exception {
        StubDownloader stub = new StubDownloader(DownloadResult.success(new byte[0], "text/plain", URI.create("https://example.com/empty")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/empty").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_NOT_AN_IMAGE, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Formato no soportado (archivo de texto/HTML)")
    void testErrorUnsupportedFormat() throws Exception {
        byte[] textBytes = "<!DOCTYPE html><html><body>Not an image</body></html>".getBytes();
        StubDownloader stub = new StubDownloader(DownloadResult.success(textBytes, "text/html", URI.create("https://example.com/index.html")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/index.html").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_UNSUPPORTED_FORMAT, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Archivo corrupto (magic bytes de PNG con contenido roto)")
    void testErrorCorruptedFile() throws Exception {
        // Cabecera PNG valida pero sin chunks
        byte[] corruptPng = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00 };
        StubDownloader stub = new StubDownloader(DownloadResult.success(corruptPng, "image/png", URI.create("https://example.com/broken.png")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/broken.png").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_CORRUPTED_FILE, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: GIF con demasiados frames (> 100)")
    void testErrorTooManyFrames() throws Exception {
        byte[] manyFramesGif = createMinimalAnimatedGif(101);
        StubDownloader stub = new StubDownloader(DownloadResult.success(manyFramesGif, "image/gif", URI.create("https://example.com/long.gif")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/long.gif").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_TOO_MANY_FRAMES, result.errorMessage().orElseThrow());
    }

    @Test
    @DisplayName("F4.8 & F4.14 - Error: Dimensiones excesivas (> 4096)")
    void testErrorDimensionsTooLarge() throws Exception {
        // Cabecera GIF con dimensiones 5000 x 5000
        byte[] largeGif = new byte[20];
        largeGif[0] = 'G'; largeGif[1] = 'I'; largeGif[2] = 'F';
        largeGif[3] = '8'; largeGif[4] = '9'; largeGif[5] = 'a';
        // Ancho 5000: 0x1388 -> 0x88, 0x13
        largeGif[6] = (byte) 0x88; largeGif[7] = (byte) 0x13;
        // Alto 5000: 0x1388 -> 0x88, 0x13
        largeGif[8] = (byte) 0x88; largeGif[9] = (byte) 0x13;

        StubDownloader stub = new StubDownloader(DownloadResult.success(largeGif, "image/gif", URI.create("https://example.com/huge.gif")));
        ImageService service = new ImageService(stub, queue);

        ImageProcessResult result = service.process("https://example.com/huge.gif").get();
        assertFalse(result.isSuccess());
        assertEquals(Messages.ERROR_DIMENSIONS_TOO_LARGE, result.errorMessage().orElseThrow());
    }
}
