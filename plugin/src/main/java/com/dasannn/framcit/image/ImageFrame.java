package com.dasannn.framcit.image;

import java.awt.image.BufferedImage;
import java.util.Objects;

/**
 * Representa un frame individual decodificado, con su retardo en milisegundos.
 */
public final class ImageFrame {

    private final BufferedImage image;
    private final int delayMs;

    public ImageFrame(BufferedImage image, int delayMs) {
        this.image = Objects.requireNonNull(image, "image cannot be null");
        this.delayMs = delayMs;
    }

    public BufferedImage getImage() {
        return image;
    }

    public int getDelayMs() {
        return delayMs;
    }
}
