package com.dasannn.framcit.image;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TwelveMonkeysRegistrarTest {

    @Test
    @DisplayName("F3.2 - Registrar el reader de TwelveMonkeys y verificar su descubrimiento")
    void testWebPReaderRegistration() {
        TwelveMonkeysRegistrar.register(null);

        assertTrue(TwelveMonkeysRegistrar.isWebPSupported(), "El formato webp debe estar soportado");

        Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("webp");
        assertTrue(readers.hasNext(), "ImageIO debe descubrir al menos un lector para webp");

        ImageReader reader = readers.next();
        assertNotNull(reader);
        assertTrue(reader.getClass().getName().contains("WebPImageReader"),
                "El lector debe ser WebPImageReader de TwelveMonkeys, obtenido: " + reader.getClass().getName());
        reader.dispose();

        // Llamadas consecutivas deben ser idempotentes y seguras
        TwelveMonkeysRegistrar.register(null);
        assertTrue(TwelveMonkeysRegistrar.isWebPSupported());
    }
}
