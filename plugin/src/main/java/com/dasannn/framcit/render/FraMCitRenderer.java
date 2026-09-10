package com.dasannn.framcit.render;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Objects;

/**
 * Renderer de mapas para FraMCit (F4.3).
 * En su version estatica, vuelca los 16384 bytes del unico frame al MapCanvas.
 * Deja la clase preparada para que la fase F6 la haga contextual sin reescribirla.
 */
public class FraMCitRenderer extends MapRenderer {

    private final ConvertedImage image;
    private boolean staticRendered = false;

    public FraMCitRenderer(ConvertedImage image) {
        this(image, false);
    }

    public FraMCitRenderer(ConvertedImage image, boolean contextual) {
        super(contextual);
        this.image = Objects.requireNonNull(image, "image cannot be null");
    }

    public ConvertedImage getImage() {
        return image;
    }

    public boolean isStaticRendered() {
        return staticRendered;
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (!isContextual()) {
            if (staticRendered) {
                return;
            }
            drawFrame(canvas, 0);
            staticRendered = true;
            return;
        }

        // Preparado para F6: modo contextual
        drawFrame(canvas, 0);
    }

    /**
     * Vuelca los 16384 bytes del frame especificado al MapCanvas.
     *
     * @param canvas canvas donde volcar los pixeles
     * @param frameIndex indice del frame
     */
    public void drawFrame(MapCanvas canvas, int frameIndex) {
        Objects.requireNonNull(canvas, "canvas cannot be null");
        if (frameIndex < 0 || frameIndex >= image.getFrameCount()) {
            frameIndex = 0;
        }
        ConvertedFrame frame = image.getFrames().get(frameIndex);
        byte[] bytes = frame.getMapBytes();
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, bytes[y * 128 + x]);
            }
        }
    }
}
