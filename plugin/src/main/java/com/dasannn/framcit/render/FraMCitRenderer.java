package com.dasannn.framcit.render;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Objects;

/**
 * Renderer de mapas para FraMCit (F4.3, F5.6, F5.10).
 * Soporta renderizado estático y carga perezosa desde ImageStore fuera del hilo principal.
 * Mientras la imagen no está cargada en memoria, no bloquea el hilo principal y conserva
 * el contenido previo del canvas.
 */
public class FraMCitRenderer extends MapRenderer {

    private final ImageStore imageStore;
    private final int mapId;
    private final ConvertedImage directImage;
    private boolean staticRendered = false;

    /**
     * Constructor estático con imagen directa en memoria (compatibilidad F4).
     */
    public FraMCitRenderer(ConvertedImage image) {
        this(image, false);
    }

    /**
     * Constructor contextual con imagen directa en memoria (compatibilidad F4).
     */
    public FraMCitRenderer(ConvertedImage image, boolean contextual) {
        super(contextual);
        this.directImage = Objects.requireNonNull(image, "image cannot be null");
        this.imageStore = null;
        this.mapId = -1;
    }

    /**
     * Constructor con carga perezosa desde ImageStore (F5.6, F5.10).
     */
    public FraMCitRenderer(int mapId, ImageStore imageStore) {
        this(mapId, imageStore, null, false);
    }

    /**
     * Constructor con carga perezosa e imagen inicial ya disponible en memoria (F5.5).
     */
    public FraMCitRenderer(int mapId, ImageStore imageStore, ConvertedImage initialImage) {
        this(mapId, imageStore, initialImage, false);
    }

    public FraMCitRenderer(int mapId, ImageStore imageStore, ConvertedImage initialImage, boolean contextual) {
        super(contextual);
        this.mapId = mapId;
        this.imageStore = imageStore;
        this.directImage = initialImage;
    }

    public ConvertedImage getImage() {
        return resolveImage();
    }

    public int getMapId() {
        return mapId;
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
            ConvertedImage img = resolveImage();
            if (img == null) {
                // Si aún no está en cache, solicitar carga asíncrona fuera del hilo principal
                if (imageStore != null && mapId >= 0) {
                    imageStore.requestLoadAsync(mapId);
                }
                // Mientras no esté cargado, el mapa conserva el frame anterior / en blanco
                return;
            }
            drawFrame(canvas, 0, img);
            staticRendered = true;
            return;
        }

        // Preparado para F6: modo contextual
        ConvertedImage img = resolveImage();
        if (img != null) {
            drawFrame(canvas, 0, img);
        } else if (imageStore != null && mapId >= 0) {
            imageStore.requestLoadAsync(mapId);
        }
    }

    /**
     * Resuelve la imagen actual: imagen directa en memoria o imagen desde el cache LRU.
     */
    private ConvertedImage resolveImage() {
        if (directImage != null) {
            return directImage;
        }
        if (imageStore != null && mapId >= 0) {
            return imageStore.getIfPresent(mapId);
        }
        return null;
    }

    /**
     * Vuelca los 16384 bytes del frame especificado al MapCanvas.
     */
    public void drawFrame(MapCanvas canvas, int frameIndex) {
        ConvertedImage img = resolveImage();
        if (img != null) {
            drawFrame(canvas, frameIndex, img);
        }
    }

    /**
     * Vuelca los 16384 bytes del frame especificado de una imagen al MapCanvas.
     */
    @SuppressWarnings("deprecation")
    public void drawFrame(MapCanvas canvas, int frameIndex, ConvertedImage img) {
        Objects.requireNonNull(canvas, "canvas cannot be null");
        Objects.requireNonNull(img, "img cannot be null");
        if (frameIndex < 0 || frameIndex >= img.getFrameCount()) {
            frameIndex = 0;
        }
        ConvertedFrame frame = img.getFrames().get(frameIndex);
        byte[] bytes = frame.getMapBytes();
        for (int y = 0; y < 128; y++) {
            for (int x = 0; x < 128; x++) {
                canvas.setPixel(x, y, bytes[y * 128 + x]);
            }
        }
    }
}
