package com.dasannn.framcit.store;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Cache LRU en memoria acotado en megabytes (F5.10).
 * Por defecto 128 MB. Desaloja las imágenes menos recientemente usadas
 * cuando el consumo de memoria total supera el límite establecido.
 * Totalmente seguro para entornos multihilo.
 */
public class LruMemoryCache {

    public static final long DEFAULT_MAX_BYTES = 128L * 1024L * 1024L; // 128 MB

    private final long maxBytes;
    private long currentBytes = 0;
    private final Map<Integer, ConvertedImage> map;

    public LruMemoryCache() {
        this(DEFAULT_MAX_BYTES);
    }

    public LruMemoryCache(long maxBytes) {
        if (maxBytes <= 0) {
            throw new IllegalArgumentException("maxBytes must be positive: " + maxBytes);
        }
        this.maxBytes = maxBytes;
        // accessOrder = true habilita el ordenamiento LRU
        this.map = new LinkedHashMap<>(16, 0.75f, true);
    }

    /**
     * Obtiene una imagen del cache marcándola como recientemente usada.
     *
     * @param mapId identificador del mapa
     * @return ConvertedImage o null si no está en cache
     */
    public synchronized ConvertedImage get(int mapId) {
        return map.get(mapId);
    }

    /**
     * Almacena una imagen en el cache, desalojando las más antiguas si es necesario
     * para respetar el límite de memoria en bytes.
     *
     * @param mapId identificador del mapa
     * @param image imagen a almacenar
     */
    public synchronized void put(int mapId, ConvertedImage image) {
        Objects.requireNonNull(image, "image cannot be null");
        long itemSize = calculateSize(image);

        // Si el elemento individual es mayor que la capacidad total del cache, no se almacena
        if (itemSize > maxBytes) {
            return;
        }

        // Si ya existía, restar el tamaño del valor anterior
        ConvertedImage existing = map.remove(mapId);
        if (existing != null) {
            currentBytes -= calculateSize(existing);
        }

        // Desalojar los elementos más antiguos (al inicio de la iteración) mientras sobrepase el límite
        while (currentBytes + itemSize > maxBytes && !map.isEmpty()) {
            Iterator<Map.Entry<Integer, ConvertedImage>> iterator = map.entrySet().iterator();
            if (iterator.hasNext()) {
                Map.Entry<Integer, ConvertedImage> eldest = iterator.next();
                currentBytes -= calculateSize(eldest.getValue());
                iterator.remove();
            }
        }

        map.put(mapId, image);
        currentBytes += itemSize;
    }

    /**
     * Elimina una imagen del cache.
     *
     * @param mapId identificador del mapa
     * @return la imagen eliminada o null si no estaba
     */
    public synchronized ConvertedImage remove(int mapId) {
        ConvertedImage removed = map.remove(mapId);
        if (removed != null) {
            currentBytes -= calculateSize(removed);
        }
        return removed;
    }

    public synchronized boolean containsKey(int mapId) {
        return map.containsKey(mapId);
    }

    public synchronized void clear() {
        map.clear();
        currentBytes = 0;
    }

    public synchronized int size() {
        return map.size();
    }

    public synchronized long getCurrentBytes() {
        return currentBytes;
    }

    public long getMaxBytes() {
        return maxBytes;
    }

    /**
     * Calcula el tamaño estimado en memoria en bytes de una imagen convertida.
     * Cada frame ocupa exactamente 16384 bytes de índices de mapa.
     */
    public static long calculateSize(ConvertedImage image) {
        if (image == null) return 0;
        return (long) image.getFrameCount() * ConvertedFrame.EXPECTED_BYTES;
    }
}
