package com.dasannn.framcit.render;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.store.ImageStore;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renderer de mapas para FraMCit (F4.3, F5.6, F5.10, F6.1, F6.2).
 * Soporta renderizado estático y contextual con índice de frame por jugador.
 *
 * El volcado al canvas es una copia directa de memoria de los bytes ya convertidos:
 * no decodifica, no escala y no convierte color en el hilo principal (F6.2).
 */
public class FraMCitRenderer extends MapRenderer {

    @FunctionalInterface
    public interface FrameProvider {
        int getFrameIndex(Player player, int mapId);
    }

    private static volatile FrameProvider globalFrameProvider;

    public static void setGlobalFrameProvider(FrameProvider provider) {
        globalFrameProvider = provider;
    }

    public static FrameProvider getGlobalFrameProvider() {
        return globalFrameProvider;
    }

    private final ImageStore imageStore;
    private final int mapId;
    private final ConvertedImage directImage;
    private final FrameProvider frameProvider;
    private final Map<UUID, Integer> localPlayerFrames = new ConcurrentHashMap<>();
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
        this(-1, null, Objects.requireNonNull(image, "image cannot be null"), contextual, null);
    }

    /**
     * Constructor con carga perezosa desde ImageStore (F5.6, F5.10).
     */
    public FraMCitRenderer(int mapId, ImageStore imageStore) {
        this(mapId, imageStore, null, false, null);
    }

    /**
     * Constructor con carga perezosa e imagen inicial ya disponible en memoria (F5.5).
     */
    public FraMCitRenderer(int mapId, ImageStore imageStore, ConvertedImage initialImage) {
        this(mapId, imageStore, initialImage, false, null);
    }

    public FraMCitRenderer(int mapId, ImageStore imageStore, ConvertedImage initialImage, boolean contextual) {
        this(mapId, imageStore, initialImage, contextual, null);
    }

    public FraMCitRenderer(
        int mapId,
        ImageStore imageStore,
        ConvertedImage initialImage,
        boolean contextual,
        FrameProvider frameProvider
    ) {
        super(contextual);
        this.mapId = mapId;
        this.imageStore = imageStore;
        this.directImage = initialImage;
        this.frameProvider = frameProvider;
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

    public void setPlayerFrame(Player player, int frameIndex) {
        if (player != null) {
            localPlayerFrames.put(player.getUniqueId(), frameIndex);
        }
    }

    public int getPlayerFrame(Player player) {
        return resolveFrameIndex(player);
    }

    @Override
    public void render(MapView map, MapCanvas canvas, Player player) {
        if (!isContextual()) {
            if (staticRendered) {
                return;
            }
            ConvertedImage img = resolveImage();
            if (img == null) {
                if (imageStore != null && mapId >= 0) {
                    imageStore.requestLoadAsync(mapId);
                }
                return;
            }
            drawFrame(canvas, 0, img);
            staticRendered = true;
            return;
        }

        // Modo contextual (F6.1, F6.2)
        if (isMapDisabled()) {
            return;
        }

        ConvertedImage img = resolveImage();
        if (img == null) {
            if (imageStore != null && mapId >= 0) {
                imageStore.requestLoadAsync(mapId);
            }
            return;
        }

        int frameIndex = resolveFrameIndex(player);
        try {
            drawFrame(canvas, frameIndex, img);
        } catch (Throwable t) {
            // F6.12: Desactivar este mapa en caso de excepción sin afectar a los demás
            if (globalFrameProvider instanceof AnimationManager am && mapId >= 0) {
                am.disableMap(mapId, t);
            }
        }
    }

    private boolean isMapDisabled() {
        if (mapId >= 0 && globalFrameProvider instanceof AnimationManager am) {
            return am.isMapDisabled(mapId);
        }
        return false;
    }

    private int resolveFrameIndex(Player player) {
        if (player == null) {
            return 0;
        }
        if (frameProvider != null) {
            return frameProvider.getFrameIndex(player, mapId);
        }
        if (globalFrameProvider != null) {
            return globalFrameProvider.getFrameIndex(player, mapId);
        }
        return localPlayerFrames.getOrDefault(player.getUniqueId(), 0);
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
     * Vuelca los 16384 bytes del frame especificado de una imagen al MapCanvas (F6.2).
     * Copia pura de memoria, sin transformaciones ni decodificación.
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
