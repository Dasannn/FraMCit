package com.dasannn.framcit.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class FraMCitConfigTest {

    private static class TestLogHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }

    @Test
    @DisplayName("F7.4 - Claves ausentes usan valores por defecto y emiten advertencias en el log")
    void testMissingKeysUseDefaultsAndLogWarnings() {
        Logger logger = Logger.getLogger("TestLogger-MissingKeys");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        // YAML completamente vacío
        String yaml = "";
        FraMCitConfig config = FraMCitConfig.load(new StringReader(yaml), logger);

        // Verificar valores por defecto
        assertEquals(FraMCitConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, config.getConnectTimeoutSeconds());
        assertEquals(FraMCitConfig.DEFAULT_READ_TIMEOUT_SECONDS, config.getReadTimeoutSeconds());
        assertEquals(FraMCitConfig.DEFAULT_MAX_DOWNLOAD_SIZE_MB, config.getMaxDownloadSizeMb());
        assertEquals(FraMCitConfig.DEFAULT_MAX_REDIRECTS, config.getMaxRedirects());
        assertEquals(FraMCitConfig.DEFAULT_DOWNLOAD_THREADS, config.getDownloadThreads());
        assertEquals(FraMCitConfig.DEFAULT_QUEUE_CAPACITY, config.getQueueCapacity());
        assertEquals(FraMCitConfig.DEFAULT_MAX_DIMENSION, config.getMaxDimension());
        assertEquals(FraMCitConfig.DEFAULT_MAX_FRAMES, config.getMaxFrames());
        assertEquals(FraMCitConfig.DEFAULT_DELAY_MS, config.getDefaultDelayMs());
        assertEquals(FraMCitConfig.DEFAULT_MIN_DELAY_MS, config.getMinDelayMs());
        assertEquals(FraMCitConfig.DEFAULT_GAZE_MAX_DISTANCE, config.getGazeMaxDistance());
        assertEquals(FraMCitConfig.DEFAULT_GAZE_PERIOD_TICKS, config.getGazePeriodTicks());
        assertEquals(FraMCitConfig.DEFAULT_GAZE_PAUSE_MINUTES, config.getGazePauseMinutes());
        assertEquals(FraMCitConfig.DEFAULT_MAX_CACHE_MB, config.getMaxCacheMb());

        // Debe haber avisos para cada clave ausente
        assertFalse(handler.records.isEmpty(), "Deben emitirse avisos cuando faltan claves");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("descarga.hilos")),
            "Debe avisar que falta descarga.hilos");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("descarga.tamano-maximo-mb")),
            "Debe avisar que falta tamano-maximo-mb");
    }

    @Test
    @DisplayName("F7.4 - Claves con basura (texto donde va número) usan valor por defecto y avisan")
    void testGarbageTextWhereNumberExpected() {
        Logger logger = Logger.getLogger("TestLogger-GarbageText");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        String yaml = """
            descarga:
              tiempo-conexion-segundos: "cinco segundos"
              hilos: "muchos"
              tamano-maximo-mb: "grande"
            imagen:
              dimension-maxima: "maxima"
              frames-maximos: "infinitos"
            """;

        FraMCitConfig config = FraMCitConfig.load(new StringReader(yaml), logger);

        assertEquals(FraMCitConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, config.getConnectTimeoutSeconds());
        assertEquals(FraMCitConfig.DEFAULT_DOWNLOAD_THREADS, config.getDownloadThreads());
        assertEquals(FraMCitConfig.DEFAULT_MAX_DOWNLOAD_SIZE_MB, config.getMaxDownloadSizeMb());
        assertEquals(FraMCitConfig.DEFAULT_MAX_DIMENSION, config.getMaxDimension());
        assertEquals(FraMCitConfig.DEFAULT_MAX_FRAMES, config.getMaxFrames());

        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("descarga.hilos") && r.getMessage().contains("muchos")));
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("descarga.tamano-maximo-mb") && r.getMessage().contains("grande")));
    }

    @Test
    @DisplayName("F7.4 - Claves con valores peligrosos (cero o negativos) caen al mínimo o defecto y avisan")
    void testDangerousZeroOrNegativeValues() {
        Logger logger = Logger.getLogger("TestLogger-DangerousValues");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        String yaml = """
            descarga:
              hilos: 0
              capacidad-cola: -5
              tamano-maximo-mb: 0
              tiempo-conexion-segundos: 0
              tiempo-lectura-segundos: -1
              max-redirecciones: -1
            imagen:
              dimension-maxima: 0
              frames-maximos: -10
              retardo-defecto-ms: 0
              retardo-minimo-ms: -1
            animacion:
              distancia-maxima-vision: 0.0
              periodo-rastreo-ticks: 0
            cache:
              tamano-maximo-mb: 0
            """;

        FraMCitConfig config = FraMCitConfig.load(new StringReader(yaml), logger);

        // Ningún valor peligroso debe sobrevivir
        assertEquals(FraMCitConfig.DEFAULT_DOWNLOAD_THREADS, config.getDownloadThreads(), "0 hilos debe usar el defecto (4)");
        assertEquals(FraMCitConfig.DEFAULT_QUEUE_CAPACITY, config.getQueueCapacity(), "Cola negativa debe usar el defecto (20)");
        assertEquals(FraMCitConfig.DEFAULT_MAX_DOWNLOAD_SIZE_MB, config.getMaxDownloadSizeMb(), "Descarga 0 MB debe usar el defecto (5)");
        assertEquals(FraMCitConfig.DEFAULT_CONNECT_TIMEOUT_SECONDS, config.getConnectTimeoutSeconds());
        assertEquals(FraMCitConfig.DEFAULT_READ_TIMEOUT_SECONDS, config.getReadTimeoutSeconds());
        assertEquals(FraMCitConfig.DEFAULT_MAX_REDIRECTS, config.getMaxRedirects());
        assertEquals(FraMCitConfig.DEFAULT_MAX_DIMENSION, config.getMaxDimension());
        assertEquals(FraMCitConfig.DEFAULT_MAX_FRAMES, config.getMaxFrames());
        assertEquals(FraMCitConfig.DEFAULT_DELAY_MS, config.getDefaultDelayMs());
        assertEquals(FraMCitConfig.DEFAULT_MIN_DELAY_MS, config.getMinDelayMs());
        assertEquals(FraMCitConfig.DEFAULT_GAZE_MAX_DISTANCE, config.getGazeMaxDistance());
        assertEquals(FraMCitConfig.DEFAULT_GAZE_PERIOD_TICKS, config.getGazePeriodTicks());
        assertEquals(FraMCitConfig.DEFAULT_MAX_CACHE_MB, config.getMaxCacheMb());

        // Comprobar avisos en el log
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("descarga.hilos")),
            "Debe avisar sobre 0 hilos");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("descarga.capacidad-cola")),
            "Debe avisar sobre capacidad negativa");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("cache.tamano-maximo-mb")),
            "Debe avisar sobre cache 0 MB");
    }

    @Test
    @DisplayName("F7.1 - Valores válidos en YAML se cargan correctamente")
    void testValidCustomValuesLoadedCorrectly() {
        Logger logger = Logger.getLogger("TestLogger-Valid");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        String yaml = """
            descarga:
              tiempo-conexion-segundos: 10
              tiempo-lectura-segundos: 30
              tamano-maximo-mb: 12
              max-redirecciones: 5
              hilos: 8
              capacidad-cola: 50
            imagen:
              dimension-maxima: 2048
              frames-maximos: 50
              retardo-defecto-ms: 200
              retardo-minimo-ms: 50
            animacion:
              distancia-maxima-vision: 32.0
              periodo-rastreo-ticks: 10
              pausa-por-mirada: 8
            cache:
              tamano-maximo-mb: 256
            economia:
              precio-por-imagen: 15.0
            """;

        FraMCitConfig config = FraMCitConfig.load(new StringReader(yaml), logger);

        assertEquals(10, config.getConnectTimeoutSeconds());
        assertEquals(30, config.getReadTimeoutSeconds());
        assertEquals(12, config.getMaxDownloadSizeMb());
        assertEquals(12L * 1024L * 1024L, config.getMaxDownloadSizeBytes());
        assertEquals(5, config.getMaxRedirects());
        assertEquals(8, config.getDownloadThreads());
        assertEquals(50, config.getQueueCapacity());
        assertEquals(2048, config.getMaxDimension());
        assertEquals(50, config.getMaxFrames());
        assertEquals(200, config.getDefaultDelayMs());
        assertEquals(50, config.getMinDelayMs());
        assertEquals(32.0, config.getGazeMaxDistance());
        assertEquals(10, config.getGazePeriodTicks());
        assertEquals(8, config.getGazePauseMinutes());
        assertEquals(8 * 60 * 20L, config.getGazePauseTicks());
        assertEquals(256, config.getMaxCacheMb());
        assertEquals(256L * 1024L * 1024L, config.getMaxCacheBytes());
        assertEquals(15.0, config.getImagePrice());

        assertTrue(handler.records.isEmpty(), "No deben emitirse avisos cuando todo es válido");
    }

    @Test
    @DisplayName("F10.6 - Precio negativo o inválido en config se trata como cero y emite aviso")
    void testNegativeOrInvalidEconomyPriceTreatedAsZero() {
        Logger logger = Logger.getLogger("TestLogger-EconomyPrice");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        // Caso 1: Precio negativo
        String yamlNegative = """
            economia:
              precio-por-imagen: -10.0
            """;
        FraMCitConfig configNeg = FraMCitConfig.load(new StringReader(yamlNegative), logger);
        assertEquals(0.0, configNeg.getImagePrice(), "Precio negativo debe tratarse como 0.0");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("economia.precio-por-imagen") && r.getMessage().contains("-10")),
            "Debe avisar que el precio es negativo e inválido");

        // Caso 2: Precio no numérico
        handler.records.clear();
        String yamlInvalid = """
            economia:
              precio-por-imagen: "diez euros"
            """;
        FraMCitConfig configInv = FraMCitConfig.load(new StringReader(yamlInvalid), logger);
        assertEquals(0.0, configInv.getImagePrice(), "Precio inválido debe tratarse como 0.0");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("economia.precio-por-imagen") && r.getMessage().contains("diez euros")),
            "Debe avisar que el precio no es un número");
    }

    @Test
    @DisplayName("F12.1 - animacion.pausa-por-mirada por defecto es 5 minutos, a 0 se desactiva y valores negativos caen al por defecto")
    void testGazePauseMinutesConfig() {
        Logger logger = Logger.getLogger("TestLogger-GazePause");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        // Caso 1: A cero la regla se desactiva
        String yamlZero = """
            animacion:
              pausa-por-mirada: 0
            """;
        FraMCitConfig configZero = FraMCitConfig.load(new StringReader(yamlZero), logger);
        assertEquals(0, configZero.getGazePauseMinutes());
        assertEquals(0L, configZero.getGazePauseTicks());

        // Caso 2: Valor configurado positivo (e.g. 10 minutos)
        String yamlCustom = """
            animacion:
              pausa-por-mirada: 10
            """;
        FraMCitConfig configCustom = FraMCitConfig.load(new StringReader(yamlCustom), logger);
        assertEquals(10, configCustom.getGazePauseMinutes());
        assertEquals(12000L, configCustom.getGazePauseTicks());

        // Caso 3: Valor negativo cae al por defecto (5 minutos = 6000 ticks) con aviso
        handler.records.clear();
        String yamlNegative = """
            animacion:
              pausa-por-mirada: -3
            """;
        FraMCitConfig configNeg = FraMCitConfig.load(new StringReader(yamlNegative), logger);
        assertEquals(FraMCitConfig.DEFAULT_GAZE_PAUSE_MINUTES, configNeg.getGazePauseMinutes());
        assertEquals(6000L, configNeg.getGazePauseTicks());
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("animacion.pausa-por-mirada")),
            "Debe avisar que animacion.pausa-por-mirada es inválido");
    }
}
