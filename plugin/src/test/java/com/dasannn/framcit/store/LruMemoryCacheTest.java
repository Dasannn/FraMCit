package com.dasannn.framcit.store;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LruMemoryCacheTest {

    private ConvertedImage createImageWithFrames(int frameCount) {
        List<ConvertedFrame> frames = new ArrayList<>();
        for (int i = 0; i < frameCount; i++) {
            frames.add(new ConvertedFrame(new byte[ConvertedFrame.EXPECTED_BYTES], 100));
        }
        return new ConvertedImage(frameCount > 1 ? ImageFormat.GIF : ImageFormat.PNG, frames);
    }

    @Test
    @DisplayName("F5.10 - Desalojo del LRU al superar el límite de memoria en megabytes / bytes")
    void testLruEvictionWhenMemoryLimitExceeded() {
        // Cada frame ocupa 16384 bytes.
        // Capacidad configurada: 40000 bytes (permite 2 imágenes de 1 frame: 32768 bytes; una tercera superaría 40000)
        long capacity = 40_000L;
        LruMemoryCache cache = new LruMemoryCache(capacity);

        ConvertedImage img1 = createImageWithFrames(1);
        ConvertedImage img2 = createImageWithFrames(1);
        ConvertedImage img3 = createImageWithFrames(1);

        cache.put(1, img1);
        cache.put(2, img2);

        assertEquals(2, cache.size());
        assertEquals(32768L, cache.getCurrentBytes());
        assertTrue(cache.containsKey(1));
        assertTrue(cache.containsKey(2));

        // Acceder a img1 para que img2 pase a ser el elemento menos recientemente usado (LRU)
        assertNotNull(cache.get(1));

        // Insertar img3: 32768 + 16384 = 49152 > 40000.
        // Debe desalojar img2 (el más antiguo no accedido) y conservar img1 e img3.
        cache.put(3, img3);

        assertEquals(2, cache.size());
        assertEquals(32768L, cache.getCurrentBytes());
        assertTrue(cache.containsKey(1), "img1 debe mantenerse porque fue accedida recientemente");
        assertFalse(cache.containsKey(2), "img2 debe haber sido desalojada por ser la menos recientemente usada");
        assertTrue(cache.containsKey(3), "img3 debe estar presente tras ser insertada");
    }

    @Test
    @DisplayName("F5.10 - Actualizar una clave existente recalcula correctamente el tamaño")
    void testUpdateExistingKeyRecalculatesSize() {
        LruMemoryCache cache = new LruMemoryCache(100_000L);

        ConvertedImage singleFrame = createImageWithFrames(1); // 16384 bytes
        ConvertedImage multiFrame = createImageWithFrames(3);  // 49152 bytes

        cache.put(1, singleFrame);
        assertEquals(16384L, cache.getCurrentBytes());

        // Sobrescribir clave 1 con una imagen de 3 frames
        cache.put(1, multiFrame);
        assertEquals(1, cache.size());
        assertEquals(49152L, cache.getCurrentBytes());

        // Eliminar clave 1
        cache.remove(1);
        assertEquals(0, cache.size());
        assertEquals(0L, cache.getCurrentBytes());
    }

    @Test
    @DisplayName("F5.10 - Imagen que excede la capacidad total del cache no se almacena")
    void testImageExceedingCapacityRejected() {
        LruMemoryCache cache = new LruMemoryCache(10_000L); // Menor que 16384
        ConvertedImage img = createImageWithFrames(1);

        cache.put(1, img);
        assertEquals(0, cache.size());
        assertEquals(0L, cache.getCurrentBytes());
        assertNull(cache.get(1));
    }
}
