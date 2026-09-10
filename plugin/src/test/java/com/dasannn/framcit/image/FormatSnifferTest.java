package com.dasannn.framcit.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatSnifferTest {

    @Test
    @DisplayName("F3.1 - Detectar PNG por magic bytes")
    void testDetectPng() throws IOException {
        byte[] pngBytes = createTestImageBytes("png");
        Optional<ImageFormat> format = FormatSniffer.sniff(pngBytes);
        assertTrue(format.isPresent());
        assertEquals(ImageFormat.PNG, format.get());
    }

    @Test
    @DisplayName("F3.1 - Detectar JPEG por magic bytes")
    void testDetectJpeg() throws IOException {
        byte[] jpgBytes = createTestImageBytes("jpeg");
        Optional<ImageFormat> format = FormatSniffer.sniff(jpgBytes);
        assertTrue(format.isPresent());
        assertEquals(ImageFormat.JPEG, format.get());
    }

    @Test
    @DisplayName("F3.1 - Detectar BMP por magic bytes")
    void testDetectBmp() throws IOException {
        byte[] bmpBytes = createTestImageBytes("bmp");
        Optional<ImageFormat> format = FormatSniffer.sniff(bmpBytes);
        assertTrue(format.isPresent());
        assertEquals(ImageFormat.BMP, format.get());
    }

    @Test
    @DisplayName("F3.1 - Detectar GIF por magic bytes (GIF89a y GIF87a)")
    void testDetectGif() throws IOException {
        byte[] gifBytes = createTestImageBytes("gif");
        Optional<ImageFormat> format = FormatSniffer.sniff(gifBytes);
        assertTrue(format.isPresent());
        assertEquals(ImageFormat.GIF, format.get());

        // Probar tambien cabecera GIF87a explicita
        byte[] gif87a = new byte[] { 'G', 'I', 'F', '8', '7', 'a', 0, 0, 0, 0 };
        Optional<ImageFormat> format87 = FormatSniffer.sniff(gif87a);
        assertTrue(format87.isPresent());
        assertEquals(ImageFormat.GIF, format87.get());
    }

    @Test
    @DisplayName("F3.1 - Detectar WEBP por magic bytes (RIFF....WEBP)")
    void testDetectWebp() {
        byte[] webpHeader = new byte[] {
                'R', 'I', 'F', 'F',
                0x20, 0x00, 0x00, 0x00, // tamano
                'W', 'E', 'B', 'P',
                'V', 'P', '8', ' '
        };
        Optional<ImageFormat> format = FormatSniffer.sniff(webpHeader);
        assertTrue(format.isPresent());
        assertEquals(ImageFormat.WEBP, format.get());
    }

    @Test
    @DisplayName("F3.12 - PNG renombrado a .jpg se detecta como PNG")
    void testPngRenamedToJpgDetectedAsPng() throws IOException {
        // Generamos contenido binario real de un PNG
        byte[] pngContent = createTestImageBytes("png");

        // Supongamos que la URL o el archivo se llamaba "foto_vacaciones.jpg"
        String fakeFilename = "foto_vacaciones.jpg";
        assertTrue(fakeFilename.endsWith(".jpg"));

        // El sniffer NO debe mirar la extension, sino los magic bytes
        Optional<ImageFormat> detected = FormatSniffer.sniff(pngContent);
        assertTrue(detected.isPresent());
        assertEquals(ImageFormat.PNG, detected.get(), "Un PNG con extension .jpg debe detectarse como PNG");
    }

    @Test
    @DisplayName("Rechazar formatos no soportados y datos basura o cortos")
    void testUnknownAndInvalidData() {
        // Datos nulos o vacios
        assertTrue(FormatSniffer.sniff(null).isEmpty());
        assertTrue(FormatSniffer.sniff(new byte[0]).isEmpty());
        assertTrue(FormatSniffer.sniff(new byte[] { 0x00 }).isEmpty());

        // Cabecera PDF (%PDF-)
        byte[] pdf = "%PDF-1.4".getBytes();
        assertTrue(FormatSniffer.sniff(pdf).isEmpty());

        // Texto plano
        byte[] txt = "Hello world!".getBytes();
        assertTrue(FormatSniffer.sniff(txt).isEmpty());

        // Bytes aleatorios
        byte[] random = new byte[] { 0x12, 0x34, 0x56, 0x78, (byte) 0x9A, (byte) 0xBC };
        assertTrue(FormatSniffer.sniff(random).isEmpty());
    }

    private static byte[] createTestImageBytes(String format) throws IOException {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 10, 10);
        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, format, baos);
        return baos.toByteArray();
    }
}
