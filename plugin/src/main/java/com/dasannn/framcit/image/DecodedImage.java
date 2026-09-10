package com.dasannn.framcit.image;

import java.util.List;
import java.util.Objects;

/**
 * Representa una imagen decodificada (estatica o animada), compuesta por uno o mas frames.
 */
public final class DecodedImage {

    private final ImageFormat format;
    private final List<ImageFrame> frames;
    private final int width;
    private final int height;

    public DecodedImage(ImageFormat format, List<ImageFrame> frames, int width, int height) {
        this.format = Objects.requireNonNull(format, "format cannot be null");
        Objects.requireNonNull(frames, "frames cannot be null");
        if (frames.isEmpty()) {
            throw new IllegalArgumentException("frames cannot be empty");
        }
        this.frames = List.copyOf(frames);
        this.width = width;
        this.height = height;
    }

    public ImageFormat getFormat() {
        return format;
    }

    public List<ImageFrame> getFrames() {
        return frames;
    }

    public int getFrameCount() {
        return frames.size();
    }

    public boolean isAnimated() {
        return frames.size() > 1;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
