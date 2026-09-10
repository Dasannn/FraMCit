package com.dasannn.framcit.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Node;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.zip.CRC32;

import static org.junit.jupiter.api.Assertions.*;

class ImageDecoderTest {

    @Test
    @DisplayName("F3.3 - Decodificar imagenes estaticas (PNG, JPEG, BMP)")
    void testDecodeStaticImages() throws IOException {
        // PNG
        byte[] png = createStaticImageBytes("png", 50, 40, Color.BLUE);
        DecodedImage decodedPng = ImageDecoder.decode(png);
        assertEquals(ImageFormat.PNG, decodedPng.getFormat());
        assertEquals(1, decodedPng.getFrameCount());
        assertFalse(decodedPng.isAnimated());
        assertEquals(50, decodedPng.getWidth());
        assertEquals(40, decodedPng.getHeight());
        assertEquals(0, decodedPng.getFrames().get(0).getDelayMs());

        // JPEG
        byte[] jpg = createStaticImageBytes("jpeg", 60, 60, Color.GREEN);
        DecodedImage decodedJpg = ImageDecoder.decode(jpg);
        assertEquals(ImageFormat.JPEG, decodedJpg.getFormat());
        assertEquals(1, decodedJpg.getFrameCount());
        assertFalse(decodedJpg.isAnimated());
        assertEquals(60, decodedJpg.getWidth());
        assertEquals(60, decodedJpg.getHeight());

        // BMP
        byte[] bmp = createStaticImageBytes("bmp", 30, 70, Color.YELLOW);
        DecodedImage decodedBmp = ImageDecoder.decode(bmp);
        assertEquals(ImageFormat.BMP, decodedBmp.getFormat());
        assertEquals(1, decodedBmp.getFrameCount());
        assertFalse(decodedBmp.isAnimated());
        assertEquals(30, decodedBmp.getWidth());
        assertEquals(70, decodedBmp.getHeight());
    }

    @Test
    @DisplayName("F3.4 - GIF animado: frames, retardos y resolucion de composicion con frames parciales")
    void testAnimatedGifComposition() throws Exception {
        // Generamos un GIF de 100x100 con 3 frames:
        // Frame 0: 100x100 rojo completo, doNotDispose, retardo 20 (200 ms)
        // Frame 1: 40x40 azul en (30, 30), doNotDispose, retardo 15 (150 ms)
        // Frame 2: 20x20 verde en (70, 70), doNotDispose, retardo 5 (50 ms -> tope 100 ms)
        byte[] gifBytes = createPartialFramesGif();

        DecodedImage decoded = ImageDecoder.decode(gifBytes);
        assertEquals(ImageFormat.GIF, decoded.getFormat());
        assertEquals(3, decoded.getFrameCount());
        assertTrue(decoded.isAnimated());
        assertEquals(100, decoded.getWidth());
        assertEquals(100, decoded.getHeight());

        // Retardos normalizados
        assertEquals(200, decoded.getFrames().get(0).getDelayMs());
        assertEquals(150, decoded.getFrames().get(1).getDelayMs());
        assertEquals(100, decoded.getFrames().get(2).getDelayMs(), "50 ms debe subir a 100 ms por tope de 10 fps");

        // Verificacion estricta de la COMPOSICION de pixeles frame a frame:
        BufferedImage f0 = decoded.getFrames().get(0).getImage();
        assertEquals(100, f0.getWidth());
        assertEquals(100, f0.getHeight());
        assertColorEquals(Color.RED, f0.getRGB(10, 10));
        assertColorEquals(Color.RED, f0.getRGB(40, 40));
        assertColorEquals(Color.RED, f0.getRGB(75, 75));

        // Frame 1: El rectangulo azul en (30,30)-(70,70) esta pintado sobre el rojo anterior
        BufferedImage f1 = decoded.getFrames().get(1).getImage();
        assertEquals(100, f1.getWidth());
        assertEquals(100, f1.getHeight());
        assertColorEquals(Color.RED, f1.getRGB(10, 10), "Frame 1 debe mantener el fondo rojo");
        assertColorEquals(Color.BLUE, f1.getRGB(40, 40), "Frame 1 debe tener el parche azul parcial");
        assertColorEquals(Color.RED, f1.getRGB(75, 75), "Frame 1 fuera del parche azul debe seguir rojo");

        // Frame 2: El rectangulo verde en (70,70) sobre lo que ya habia (rojo + azul conservado)
        BufferedImage f2 = decoded.getFrames().get(2).getImage();
        assertEquals(100, f2.getWidth());
        assertEquals(100, f2.getHeight());
        assertColorEquals(Color.RED, f2.getRGB(10, 10), "Frame 2 debe conservar el fondo rojo original");
        assertColorEquals(Color.BLUE, f2.getRGB(40, 40), "Frame 2 debe conservar el parche azul de Frame 1 (doNotDispose)");
        assertColorEquals(Color.GREEN, f2.getRGB(75, 75), "Frame 2 debe tener el parche verde parcial");
    }

    @Test
    @DisplayName("F3.4 - GIF con disposal method restoreToPrevious")
    void testGifRestoreToPrevious() throws Exception {
        // Frame 0: 50x50 rojo completo, doNotDispose
        // Frame 1: 20x20 azul en (10, 10), restoreToPrevious
        // Frame 2: 20x20 verde en (30, 30), doNotDispose
        byte[] gif = createGifWithRestoreToPrevious();
        DecodedImage decoded = ImageDecoder.decode(gif);
        assertEquals(3, decoded.getFrameCount());

        BufferedImage f1 = decoded.getFrames().get(1).getImage();
        assertColorEquals(Color.BLUE, f1.getRGB(15, 15));

        // Frame 2 debe haber restaurado el canvas al estado previo al Frame 1 (sin azul en (15, 15))
        BufferedImage f2 = decoded.getFrames().get(2).getImage();
        assertColorEquals(Color.RED, f2.getRGB(15, 15), "Al usar restoreToPrevious, el parche azul debe desaparecer");
        assertColorEquals(Color.GREEN, f2.getRGB(35, 35));
    }

    @Test
    @DisplayName("F3.5 - WEBP animado y estatico segun resultado de F0")
    void testWebpDecoding() throws IOException {
        // Usar los samples reales verificados en F0 si estan disponibles en ../spike-webp/samples o spike-webp/samples
        File samplesDir = new File("../spike-webp/samples");
        if (!samplesDir.exists()) {
            samplesDir = new File("spike-webp/samples");
        }
        if (!samplesDir.exists()) {
            samplesDir = new File("../framcit-f0-spike-webp/spike-webp/samples");
        }

        if (samplesDir.exists() && samplesDir.isDirectory()) {
            // Test con static_google.webp
            File staticWebpFile = new File(samplesDir, "static_google.webp");
            if (staticWebpFile.exists()) {
                byte[] staticWebpBytes = Files.readAllBytes(staticWebpFile.toPath());
                DecodedImage staticImg = ImageDecoder.decode(staticWebpBytes);
                assertEquals(ImageFormat.WEBP, staticImg.getFormat());
                assertEquals(1, staticImg.getFrameCount());
                assertFalse(staticImg.isAnimated());
                assertEquals(0, staticImg.getFrames().get(0).getDelayMs());
            }

            // Test con mozilla_blend.webp (animado, 2 frames)
            File animWebpFile = new File(samplesDir, "mozilla_blend.webp");
            if (animWebpFile.exists()) {
                byte[] animWebpBytes = Files.readAllBytes(animWebpFile.toPath());
                DecodedImage animImg = ImageDecoder.decode(animWebpBytes);
                assertEquals(ImageFormat.WEBP, animImg.getFormat());
                assertEquals(2, animImg.getFrameCount());
                assertTrue(animImg.isAnimated());
                // Spec 4.2: retardo fijo de 100 ms por frame para WEBP animado
                assertEquals(100, animImg.getFrames().get(0).getDelayMs());
                assertEquals(100, animImg.getFrames().get(1).getDelayMs());
            }
        }
    }

    @Test
    @DisplayName("F3.6 & F3.14 - Imagen de 5000x5000 rechazada por cabecera, sin decodificarla")
    void testImage5000x5000RejectedByHeader() {
        // Creamos una cabecera PNG valida de solo 33 bytes declarando 5000x5000.
        // No contiene ningun chunk IDAT de pixeles ni datos comprimidos.
        // Si intentara decodificarla fallaria por EOF/corrupcion, pero al leer solo
        // la cabecera IHDR debe saltar exactamente la validacion de dimensiones maximas.
        byte[] pngHeader5000 = createPngHeader(5000, 5000);

        ImageValidationException ex = assertThrows(ImageValidationException.class, () -> {
            ImageDecoder.decode(pngHeader5000);
        });

        assertTrue(ex.getMessage().contains("4096x4096"),
                "El mensaje debe indicar el limite de dimensiones: " + ex.getMessage());

        // Comprobamos tambien en GIF: LogicalScreenDescriptor de 5000x5000
        byte[] gifHeader5000 = new byte[] {
                'G', 'I', 'F', '8', '9', 'a',
                (byte) (5000 & 0xFF), (byte) ((5000 >> 8) & 0xFF), // 5000 ancho little-endian
                (byte) (5000 & 0xFF), (byte) ((5000 >> 8) & 0xFF), // 5000 alto little-endian
                0x00, 0x00, 0x00,
                0x3B // Trailer
        };

        ImageValidationException exGif = assertThrows(ImageValidationException.class, () -> {
            ImageDecoder.decode(gifHeader5000);
        });
        assertTrue(exGif.getMessage().contains("4096x4096"),
                "El GIF de 5000x5000 debe rechazarse por cabecera: " + exGif.getMessage());
    }

    @Test
    @DisplayName("F3.6 & F3.13 - GIF de 101 frames rechazado, de 100 aceptado")
    void testGifFrameLimits() throws Exception {
        // GIF de 100 frames (limite maximo permitido)
        byte[] gif100 = createMinimalAnimatedGif(100);
        DecodedImage decoded100 = ImageDecoder.decode(gif100);
        assertNotNull(decoded100);
        assertEquals(100, decoded100.getFrameCount());

        // GIF de 101 frames (supera el limite de 100)
        byte[] gif101 = createMinimalAnimatedGif(101);
        ImageValidationException ex = assertThrows(ImageValidationException.class, () -> {
            ImageDecoder.decode(gif101);
        });

        assertTrue(ex.getMessage().contains("100"),
                "El mensaje debe advertir el limite de 100 frames: " + ex.getMessage());
    }

    @Test
    @DisplayName("F3.7 - Normalizar retardos de frames")
    void testNormalizeDelay() {
        // Retardo cero o negativo -> 100 ms por defecto
        assertEquals(100, ImageDecoder.normalizeDelay(0));
        assertEquals(100, ImageDecoder.normalizeDelay(-5));

        // Retardo menor a 10 fps (100 ms) -> tope a 100 ms
        assertEquals(100, ImageDecoder.normalizeDelay(1)); // 10 ms -> 100 ms
        assertEquals(100, ImageDecoder.normalizeDelay(5)); // 50 ms -> 100 ms
        assertEquals(100, ImageDecoder.normalizeDelay(9)); // 90 ms -> 100 ms

        // Retardo exacto o mayor a 100 ms -> respetado
        assertEquals(100, ImageDecoder.normalizeDelay(10)); // 100 ms
        assertEquals(150, ImageDecoder.normalizeDelay(15)); // 150 ms
        assertEquals(500, ImageDecoder.normalizeDelay(50)); // 500 ms
    }

    @Test
    @DisplayName("Rechazar archivo con formato no soportado o corrupto")
    void testInvalidFiles() {
        // No es imagen
        byte[] textFile = "Esto es un archivo de texto plano".getBytes();
        ImageValidationException ex = assertThrows(ImageValidationException.class, () -> {
            ImageDecoder.decode(textFile);
        });
        assertTrue(ex.getMessage().contains("Formato no soportado"));

        // Bytes corruptos que empiezan con PNG magic pero estan truncados
        byte[] corruptPng = new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01 };
        assertThrows(ImageValidationException.class, () -> {
            ImageDecoder.decode(corruptPng);
        });
    }

    // --- Metodos auxiliares de generacion por codigo ---

    private static void assertColorEquals(Color expected, int argb) {
        assertColorEquals(expected, argb, "");
    }

    private static void assertColorEquals(Color expected, int argb, String message) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        assertEquals(expected.getRed(), r, 10, message + " - Componente Red difiere");
        assertEquals(expected.getGreen(), g, 10, message + " - Componente Green difiere");
        assertEquals(expected.getBlue(), b, 10, message + " - Componente Blue difiere");
    }

    private static byte[] createStaticImageBytes(String format, int w, int h, Color color) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, w, h);
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

    private static byte[] createPartialFramesGif() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            // Frame 0: 100x100 rojo
            BufferedImage img0 = new BufferedImage(100, 100, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g0 = img0.createGraphics();
            g0.setColor(Color.RED);
            g0.fillRect(0, 0, 100, 100);
            g0.dispose();

            IIOMetadata meta0 = createGifFrameMetadata(writer, img0, 0, 0, "doNotDispose", 20);
            writer.writeToSequence(new IIOImage(img0, null, meta0), null);

            // Frame 1: 40x40 azul en (30,30)
            BufferedImage img1 = new BufferedImage(40, 40, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g1 = img1.createGraphics();
            g1.setColor(Color.BLUE);
            g1.fillRect(0, 0, 40, 40);
            g1.dispose();

            IIOMetadata meta1 = createGifFrameMetadata(writer, img1, 30, 30, "doNotDispose", 15);
            writer.writeToSequence(new IIOImage(img1, null, meta1), null);

            // Frame 2: 20x20 verde en (70,70)
            BufferedImage img2 = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img2.createGraphics();
            g2.setColor(Color.GREEN);
            g2.fillRect(0, 0, 20, 20);
            g2.dispose();

            IIOMetadata meta2 = createGifFrameMetadata(writer, img2, 70, 70, "doNotDispose", 5);
            writer.writeToSequence(new IIOImage(img2, null, meta2), null);

            writer.endWriteSequence();
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private static byte[] createGifWithRestoreToPrevious() throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName("gif").next();
            writer.setOutput(ios);
            writer.prepareWriteSequence(null);

            // Frame 0: 50x50 rojo
            BufferedImage img0 = new BufferedImage(50, 50, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g0 = img0.createGraphics();
            g0.setColor(Color.RED);
            g0.fillRect(0, 0, 50, 50);
            g0.dispose();

            IIOMetadata meta0 = createGifFrameMetadata(writer, img0, 0, 0, "doNotDispose", 10);
            writer.writeToSequence(new IIOImage(img0, null, meta0), null);

            // Frame 1: 20x20 azul en (10,10), con restoreToPrevious
            BufferedImage img1 = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g1 = img1.createGraphics();
            g1.setColor(Color.BLUE);
            g1.fillRect(0, 0, 20, 20);
            g1.dispose();

            IIOMetadata meta1 = createGifFrameMetadata(writer, img1, 10, 10, "restoreToPrevious", 10);
            writer.writeToSequence(new IIOImage(img1, null, meta1), null);

            // Frame 2: 20x20 verde en (30,30)
            BufferedImage img2 = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img2.createGraphics();
            g2.setColor(Color.GREEN);
            g2.fillRect(0, 0, 20, 20);
            g2.dispose();

            IIOMetadata meta2 = createGifFrameMetadata(writer, img2, 30, 30, "doNotDispose", 10);
            writer.writeToSequence(new IIOImage(img2, null, meta2), null);

            writer.endWriteSequence();
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private static IIOMetadata createGifFrameMetadata(ImageWriter writer, BufferedImage image, int left, int top, String disposal, int delayCs) throws Exception {
        ImageTypeSpecifier type = ImageTypeSpecifier.createFromRenderedImage(image);
        IIOMetadata meta = writer.getDefaultImageMetadata(type, null);
        String format = meta.getNativeMetadataFormatName();
        Node root = meta.getAsTree(format);

        for (int i = 0; i < root.getChildNodes().getLength(); i++) {
            Node child = root.getChildNodes().item(i);
            if ("GraphicControlExtension".equals(child.getNodeName())) {
                ((IIOMetadataNode) child).setAttribute("disposalMethod", disposal);
                ((IIOMetadataNode) child).setAttribute("delayTime", String.valueOf(delayCs));
            } else if ("ImageDescriptor".equals(child.getNodeName())) {
                ((IIOMetadataNode) child).setAttribute("imageLeftPosition", String.valueOf(left));
                ((IIOMetadataNode) child).setAttribute("imageTopPosition", String.valueOf(top));
            }
        }
        meta.setFromTree(format, root);
        return meta;
    }

    private static byte[] createPngHeader(int width, int height) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            // PNG Signature: 89 50 4E 47 0D 0A 1A 0A
            baos.write(new byte[] { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A });

            // Chunk IHDR: longitud 13 bytes
            baos.write(new byte[] { 0, 0, 0, 13 });

            ByteArrayOutputStream ihdrData = new ByteArrayOutputStream();
            ihdrData.write("IHDR".getBytes());
            // Width (4 bytes big-endian)
            ihdrData.write((width >> 24) & 0xFF);
            ihdrData.write((width >> 16) & 0xFF);
            ihdrData.write((width >> 8) & 0xFF);
            ihdrData.write(width & 0xFF);
            // Height (4 bytes big-endian)
            ihdrData.write((height >> 24) & 0xFF);
            ihdrData.write((height >> 16) & 0xFF);
            ihdrData.write((height >> 8) & 0xFF);
            ihdrData.write(height & 0xFF);
            // Bit depth 8, color type 2 (RGB), compression 0, filter 0, interlace 0
            ihdrData.write(new byte[] { 8, 2, 0, 0, 0 });

            byte[] ihdrBytes = ihdrData.toByteArray();
            baos.write(ihdrBytes);

            // CRC32 del chunk IHDR
            CRC32 crc = new CRC32();
            crc.update(ihdrBytes);
            long crcValue = crc.getValue();
            baos.write((int) ((crcValue >> 24) & 0xFF));
            baos.write((int) ((crcValue >> 16) & 0xFF));
            baos.write((int) ((crcValue >> 8) & 0xFF));
            baos.write((int) (crcValue & 0xFF));

        } catch (IOException ignored) {}
        return baos.toByteArray();
    }
}
