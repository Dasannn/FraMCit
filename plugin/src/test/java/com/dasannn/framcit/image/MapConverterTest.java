package com.dasannn.framcit.image;

import org.bukkit.map.MapPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MapConverterTest {

    @Test
    @DisplayName("F3.8 & F3.10 - Imagen ancha: escalar sin deformar, centrada verticalmente, sobrante transparente")
    void testWideImageScalingAndCentering() {
        // Imagen ancha de 200x100 (proporcion 2:1)
        BufferedImage wide = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = wide.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 100);
        g.dispose();

        BufferedImage fitted = MapConverter.fitToMapCanvas(wide);
        assertEquals(128, fitted.getWidth());
        assertEquals(128, fitted.getHeight());

        // Al escalar 200x100 a maximo 128x128:
        // escala = 128 / 200 = 0.64
        // ancho escalado = 128, alto escalado = 64
        // centrado vertical: offsetY = (128 - 64) / 2 = 32
        // Las primeras 32 filas (0..31) y las ultimas 32 filas (96..127) deben ser transparentes.
        // Las filas intermedias (32..95) deben contener la imagen (rojo opaco).

        // Verificar zona superior transparente (alpha == 0)
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 128; x++) {
                int alpha = (fitted.getRGB(x, y) >> 24) & 0xFF;
                assertEquals(0, alpha, "Fila superior " + y + " debe ser transparente");
            }
        }

        // Verificar zona inferior transparente (alpha == 0)
        for (int y = 96; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int alpha = (fitted.getRGB(x, y) >> 24) & 0xFF;
                assertEquals(0, alpha, "Fila inferior " + y + " debe ser transparente");
            }
        }

        // Verificar zona central dibujada
        for (int y = 35; y < 90; y++) {
            for (int x = 10; x < 118; x++) {
                int alpha = (fitted.getRGB(x, y) >> 24) & 0xFF;
                assertEquals(255, alpha, "Pixel central (" + x + "," + y + ") debe ser opaco");
            }
        }

        // Conversion a bytes de paleta de mapa
        byte[] mapBytes = MapConverter.convertToMapBytes(wide);
        assertEquals(16384, mapBytes.length);

        // Los pixeles de las zonas superior e inferior deben ser MapPalette.TRANSPARENT (0)
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals(MapPalette.TRANSPARENT, mapBytes[y * 128 + x], "Padding superior en mapa debe ser transparente");
            }
        }
        for (int y = 96; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                assertEquals(MapPalette.TRANSPARENT, mapBytes[y * 128 + x], "Padding inferior en mapa debe ser transparente");
            }
        }
        // Centro no debe ser transparente
        assertNotEquals(MapPalette.TRANSPARENT, mapBytes[64 * 128 + 64], "Pixel central no debe ser transparente");
    }

    @Test
    @DisplayName("F3.8 & F3.10 - Imagen alta: escalar sin deformar, centrada horizontalmente, sobrante transparente")
    void testTallImageScalingAndCentering() {
        // Imagen alta de 100x200 (proporcion 1:2)
        BufferedImage tall = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = tall.createGraphics();
        g.setColor(Color.BLUE);
        g.fillRect(0, 0, 100, 200);
        g.dispose();

        BufferedImage fitted = MapConverter.fitToMapCanvas(tall);
        assertEquals(128, fitted.getWidth());
        assertEquals(128, fitted.getHeight());

        // Al escalar 100x200 a maximo 128x128:
        // escala = 128 / 200 = 0.64
        // ancho escalado = 64, alto escalado = 128
        // centrado horizontal: offsetX = (128 - 64) / 2 = 32
        // Columnas 0..31 y 96..127 transparentes, columnas 32..95 con contenido

        for (int x = 0; x < 32; x++) {
            for (int y = 0; y < 128; y++) {
                int alpha = (fitted.getRGB(x, y) >> 24) & 0xFF;
                assertEquals(0, alpha, "Columna izquierda " + x + " debe ser transparente");
            }
        }

        for (int x = 96; x < 128; x++) {
            for (int y = 0; y < 128; y++) {
                int alpha = (fitted.getRGB(x, y) >> 24) & 0xFF;
                assertEquals(0, alpha, "Columna derecha " + x + " debe ser transparente");
            }
        }

        byte[] mapBytes = MapConverter.convertToMapBytes(tall);
        assertEquals(16384, mapBytes.length);
        for (int x = 0; x < 32; x++) {
            assertEquals(MapPalette.TRANSPARENT, mapBytes[64 * 128 + x]);
        }
        for (int x = 96; x < 128; x++) {
            assertEquals(MapPalette.TRANSPARENT, mapBytes[64 * 128 + x]);
        }
        assertNotEquals(MapPalette.TRANSPARENT, mapBytes[64 * 128 + 64]);
    }

    @Test
    @DisplayName("F3.8 & F3.10 - Imagen cuadrada: ocupa los 128x128 completos sin padding")
    void testSquareImageScaling() {
        BufferedImage square = new BufferedImage(300, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = square.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 300, 300);
        g.dispose();

        BufferedImage fitted = MapConverter.fitToMapCanvas(square);
        assertEquals(128, fitted.getWidth());
        assertEquals(128, fitted.getHeight());

        // No debe haber padding transparente
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                int alpha = (fitted.getRGB(x, y) >> 24) & 0xFF;
                assertEquals(255, alpha, "Un cuadrado no debe tener padding transparente");
            }
        }

        byte[] mapBytes = MapConverter.convertToMapBytes(square);
        assertEquals(16384, mapBytes.length);
        for (byte b : mapBytes) {
            assertNotEquals(MapPalette.TRANSPARENT, b);
        }
    }

    @Test
    @DisplayName("F3.9 & F3.11 - PNG con transparencia que se mantiene transparente en la paleta de mapa")
    void testPngTransparencyPreserved() {
        // Imagen de 128x128: mitad izquierda opaca roja, mitad derecha transparente
        BufferedImage img = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setColor(new Color(255, 0, 0, 255));
        g.fillRect(0, 0, 64, 128); // Izquierda opaca
        // La mitad derecha (64..127) queda vacia con ARGB 0 (alpha 0)
        g.dispose();

        byte[] mapBytes = MapConverter.convertToMapBytes(img);
        assertEquals(16384, mapBytes.length);

        // Verificar que la mitad izquierda tiene color no-transparente
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 64; x++) {
                assertNotEquals(MapPalette.TRANSPARENT, mapBytes[y * 128 + x],
                        "Pixel opaco en (" + x + "," + y + ") no debe ser transparente");
            }
        }

        // Verificar que la mitad derecha conserva la transparencia total (MapPalette.TRANSPARENT = 0)
        for (int y = 0; y < 128; y++) {
            for (int x = 64; x < 128; x++) {
                assertEquals(MapPalette.TRANSPARENT, mapBytes[y * 128 + x],
                        "Pixel transparente en (" + x + "," + y + ") debe mantenerse transparente");
            }
        }
    }

    @Test
    @DisplayName("F3.15 - Volcar una conversion a PNG desde un test para revisarla a ojo")
    void testDumpConversionToPngForVisualInspection() throws IOException {
        // Crear una imagen de prueba representativa de 200x150 con fondo transparente,
        // un rectangulo rojo, un circulo azul y texto o detalles
        BufferedImage testImg = new BufferedImage(200, 150, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = testImg.createGraphics();
        // Rectangulo rojo
        g.setColor(Color.RED);
        g.fillRect(10, 10, 80, 130);
        // Circulo azul superpuesto
        g.setColor(Color.BLUE);
        g.fillOval(60, 30, 90, 90);
        // Cuadrado verde
        g.setColor(Color.GREEN);
        g.fillRect(120, 10, 70, 70);
        g.dispose();

        // 1. Convertir a bytes de paleta de mapa
        byte[] mapBytes = MapConverter.convertToMapBytes(testImg);
        assertEquals(16384, mapBytes.length);

        // 2. Reconstruir BufferedImage desde los bytes de paleta
        BufferedImage reconstructed = MapConverter.mapBytesToImage(mapBytes);
        assertEquals(128, reconstructed.getWidth());
        assertEquals(128, reconstructed.getHeight());

        // 3. Volcar a un archivo PNG en build/ para que pueda revisarse a ojo
        File outputDir = new File("build/reports/visual-tests");
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "test_f3_map_conversion.png");
        boolean written = ImageIO.write(reconstructed, "PNG", outputFile);

        assertTrue(written, "ImageIO debe poder escribir la imagen PNG reconstruida");
        assertTrue(outputFile.exists(), "El archivo PNG volcado debe existir en el disco");
        assertTrue(outputFile.length() > 0, "El archivo PNG volcado no debe estar vacio");

        System.out.println("=================================================================");
        System.out.println("F3.15: Conversion volcada a PNG para revision visual en:");
        System.out.println(outputFile.getAbsolutePath());
        System.out.println("=================================================================");
    }

    @Test
    @DisplayName("Conversion completa de DecodedImage a ConvertedImage")
    void testConvertDecodedImage() {
        BufferedImage img1 = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        BufferedImage img2 = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
        List<ImageFrame> frames = List.of(
                new ImageFrame(img1, 100),
                new ImageFrame(img2, 200)
        );
        DecodedImage decoded = new DecodedImage(ImageFormat.GIF, frames, 50, 50);

        ConvertedImage converted = MapConverter.convert(decoded);
        assertEquals(ImageFormat.GIF, converted.getFormat());
        assertEquals(2, converted.getFrameCount());
        assertTrue(converted.isAnimated());
        assertEquals(100, converted.getFrames().get(0).getDelayMs());
        assertEquals(200, converted.getFrames().get(1).getDelayMs());
        assertEquals(16384, converted.getFrames().get(0).getMapBytes().length);
        assertEquals(16384, converted.getFrames().get(1).getMapBytes().length);
    }
}
