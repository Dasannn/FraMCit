package com.dasannn.framcit.image;

import org.bukkit.map.MapPalette;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Convierte imagenes a bytes de la paleta de mapa de Minecraft (128x128).
 * - Escala manteniendo la proporcion hasta caber en 128x128.
 * - Centra el resultado y rellena el espacio sobrante con pixeles transparentes.
 * - Nunca deforma ni recorta la imagen original.
 * - Convierte a la paleta de mapa de Minecraft conservando la transparencia total.
 */
public final class MapConverter {

    public static final int MAP_SIZE = 128;
    public static final int TOTAL_PIXELS = MAP_SIZE * MAP_SIZE; // 16384

    private MapConverter() {}

    /**
     * Convierte una {@link DecodedImage} completa en una {@link ConvertedImage} lista para renderizado.
     *
     * @param decodedImage imagen decodificada
     * @return imagen convertida con frames en bytes de paleta y sus retardos
     */
    public static ConvertedImage convert(DecodedImage decodedImage) {
        Objects.requireNonNull(decodedImage, "decodedImage cannot be null");
        List<ConvertedFrame> convertedFrames = new ArrayList<>(decodedImage.getFrameCount());

        for (ImageFrame frame : decodedImage.getFrames()) {
            convertedFrames.add(convertFrame(frame));
        }

        return new ConvertedImage(decodedImage.getFormat(), convertedFrames);
    }

    /**
     * Convierte un {@link ImageFrame} individual a {@link ConvertedFrame}.
     *
     * @param frame frame a convertir
     * @return frame convertido con bytes de paleta de mapa
     */
    public static ConvertedFrame convertFrame(ImageFrame frame) {
        Objects.requireNonNull(frame, "frame cannot be null");
        byte[] bytes = convertToMapBytes(frame.getImage());
        return new ConvertedFrame(bytes, frame.getDelayMs());
    }

    /**
     * Escala y centra una imagen en un canvas 128x128 y la convierte a bytes de paleta de mapa de Minecraft.
     *
     * @param sourceImage imagen de origen
     * @return arreglo de exactamente 16384 bytes (128x128)
     */
    @SuppressWarnings("deprecation")
    public static byte[] convertToMapBytes(BufferedImage sourceImage) {
        Objects.requireNonNull(sourceImage, "sourceImage cannot be null");
        BufferedImage fitted = fitToMapCanvas(sourceImage);
        return MapPalette.imageToBytes(fitted);
    }

    /**
     * Ajusta la imagen dentro de un lienzo de 128x128 pixeles:
     * - Escala manteniendo la relacion de aspecto.
     * - Centra la imagen escalada.
     * - Rellena el resto con pixeles completamente transparentes (ARGB = 0x00000000).
     *
     * @param sourceImage imagen de origen
     * @return nueva imagen de 128x128 con la imagen escalada y centrada
     */
    public static BufferedImage fitToMapCanvas(BufferedImage sourceImage) {
        Objects.requireNonNull(sourceImage, "sourceImage cannot be null");
        int origW = sourceImage.getWidth();
        int origH = sourceImage.getHeight();

        double scale = Math.min((double) MAP_SIZE / origW, (double) MAP_SIZE / origH);
        int scaledW = Math.min(MAP_SIZE, Math.max(1, (int) Math.round(origW * scale)));
        int scaledH = Math.min(MAP_SIZE, Math.max(1, (int) Math.round(origH * scale)));

        int offsetX = (MAP_SIZE - scaledW) / 2;
        int offsetY = (MAP_SIZE - scaledH) / 2;

        BufferedImage canvas = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = canvas.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Fondo completamente transparente
            g.setComposite(AlphaComposite.Clear);
            g.fillRect(0, 0, MAP_SIZE, MAP_SIZE);

            // Dibujar imagen escalada y centrada
            g.setComposite(AlphaComposite.SrcOver);
            g.drawImage(sourceImage, offsetX, offsetY, scaledW, scaledH, null);
        } finally {
            g.dispose();
        }

        return canvas;
    }

    /**
     * Reconstruye una imagen {@link BufferedImage} de 128x128 a partir de un arreglo de 16384 bytes de paleta.
     * Util para volcar a PNG y verificar visualmente la conversion (spec.md y F3.15).
     *
     * @param mapBytes arreglo de 16384 bytes
     * @return imagen de 128x128 en formato TYPE_INT_ARGB
     */
    @SuppressWarnings("deprecation")
    public static BufferedImage mapBytesToImage(byte[] mapBytes) {
        Objects.requireNonNull(mapBytes, "mapBytes cannot be null");
        if (mapBytes.length != TOTAL_PIXELS) {
            throw new IllegalArgumentException("mapBytes must be exactly " + TOTAL_PIXELS + " bytes, got " + mapBytes.length);
        }

        BufferedImage image = new BufferedImage(MAP_SIZE, MAP_SIZE, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < MAP_SIZE; y++) {
            for (int x = 0; x < MAP_SIZE; x++) {
                byte b = mapBytes[y * MAP_SIZE + x];
                if (b == MapPalette.TRANSPARENT) {
                    image.setRGB(x, y, 0x00000000);
                } else {
                    Color c = MapPalette.getColor(b);
                    image.setRGB(x, y, c.getRGB());
                }
            }
        }
        return image;
    }
}
