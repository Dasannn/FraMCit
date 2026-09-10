package com.dasannn.framcit.image;

import java.util.List;
import java.util.Objects;

/**
 * Representa una imagen completa convertida a bytes de paleta de mapa de Minecraft.
 */
public final class ConvertedImage {

    private final ImageFormat format;
    private final List<ConvertedFrame> frames;

    public ConvertedImage(ImageFormat format, List<ConvertedFrame> frames) {
        this.format = Objects.requireNonNull(format, "format cannot be null");
        Objects.requireNonNull(frames, "frames cannot be null");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames cannot be empty");
        }
        this.frames = List.copyOf(frames);
    }

    public ImageFormat getFormat() {
        return format;
    }

    public List<ConvertedFrame> getFrames() {
        return frames;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public boolean isAnimated() {
        return frames.size() > 1;
    }
}
