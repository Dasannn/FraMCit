package com.dasannn.framcit.image;

import com.twelvemonkeys.imageio.plugins.webp.WebPImageReaderSpi;

import javax.imageio.ImageIO;
import javax.imageio.spi.IIORegistry;
import javax.imageio.spi.ImageReaderSpi;
import java.util.Iterator;
import java.util.logging.Logger;

/**
 * Registra y valida el lector de WebP de TwelveMonkeys en el IIORegistry de Java.
 * Dado que las clases de la libreria estan relocalizadas por Shadow en el jar final
 * (com.dasannn.framcit.libs.com.twelvemonkeys), este registro explicito garantiza que
 * el PluginClassLoader de Paper descubra y active el proveedor de servicios en tiempo
 * de ejecucion del servidor sin depender del escaneo automatico del sistema.
 */
public final class TwelveMonkeysRegistrar {

    private static volatile boolean registered = false;

    private TwelveMonkeysRegistrar() {}

    /**
     * Registra el proveedor WebPImageReaderSpi si no esta presente en el IIORegistry
     * y comprueba que ImageIO descubra un lector para "webp".
     *
     * @param logger logger opcional del plugin para registrar el resultado
     */
    public static synchronized void register(Logger logger) {
        if (registered) {
            return;
        }

        IIORegistry registry = IIORegistry.getDefaultInstance();

        // Comprobar si ya existe una instancia de WebPImageReaderSpi registrada
        boolean found = false;
        Iterator<ImageReaderSpi> providers = registry.getServiceProviders(ImageReaderSpi.class, false);
        while (providers.hasNext()) {
            ImageReaderSpi spi = providers.next();
            if (spi instanceof WebPImageReaderSpi) {
                found = true;
                break;
            }
        }

        if (!found) {
            WebPImageReaderSpi webpSpi = new WebPImageReaderSpi();
            registry.registerServiceProvider(webpSpi);
            if (logger != null) {
                logger.info("Proveedor WebPImageReaderSpi de TwelveMonkeys registrado en IIORegistry.");
            }
        }

        // Validar que ImageIO sea capaz de obtener lectores para formato webp
        boolean available = ImageIO.getImageReadersByFormatName("webp").hasNext();
        if (available) {
            if (logger != null) {
                logger.info("Lector WebP (TwelveMonkeys) verificado y activo en tiempo de ejecucion.");
            }
            registered = true;
        } else {
            String warning = "AVISO: No se pudo descubrir ningun ImageReader para formato WEBP tras el registro.";
            if (logger != null) {
                logger.warning(warning);
            } else {
                System.err.println(warning);
            }
        }
    }

    /**
     * Comprueba si existe al menos un lector registrado para WebP.
     *
     * @return true si ImageIO dispone de lector para "webp"
     */
    public static boolean isWebPSupported() {
        return ImageIO.getImageReadersByFormatName("webp").hasNext();
    }
}
