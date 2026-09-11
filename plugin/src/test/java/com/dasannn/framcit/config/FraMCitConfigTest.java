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
        assertEquals(FraMCitConfig.DEFAULT_MAX_CACHE_MB, config.getMaxCacheMb());
        assertEquals(FraMCitConfig.DEFAULT_SLOTS_DEFAULT, config.getDefaultSlots());
        assertEquals(FraMCitConfig.DEFAULT_SLOTS_MAX_NODE, config.getMaxSlotsNode());
        assertEquals(FraMCitConfig.DEFAULT_FRAMES_DEFAULT, config.getDefaultFrames());
        assertEquals(FraMCitConfig.DEFAULT_FRAMES_MAX_NODE, config.getMaxFramesNode());

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
            cache:
              tamano-maximo-mb: 256
            economia:
              precio-por-imagen: 15.0
            slots:
              por-defecto: 2
              maximo-nodo: 15
            frames:
              por-defecto: 50
              maximo-nodo: 80
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
        assertEquals(256, config.getMaxCacheMb());
        assertEquals(256L * 1024L * 1024L, config.getMaxCacheBytes());
        assertEquals(15.0, config.getImagePrice());
        assertEquals(2, config.getDefaultSlots());
        assertEquals(15, config.getMaxSlotsNode());
        assertEquals(50, config.getDefaultFrames());
        assertEquals(80, config.getMaxFramesNode());

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
    @DisplayName("F11.3 - Configuración de slots y frames carga valores válidos y valores por defecto")
    void testSlotsAndFramesConfig() {
        Logger logger = Logger.getLogger("TestLogger-SlotsFrames");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        // Caso 1: Valores válidos personalizados
        String yaml = """
            imagen:
              frames-maximos: 50
            slots:
              por-defecto: 3
              maximo-nodo: 20
            frames:
              por-defecto: 25
              maximo-nodo: 50
            """;
        FraMCitConfig config = FraMCitConfig.load(new StringReader(yaml), logger);
        assertEquals(3, config.getDefaultSlots());
        assertEquals(20, config.getMaxSlotsNode());
        assertEquals(25, config.getDefaultFrames());
        assertEquals(50, config.getMaxFramesNode());

        // Caso 2: frames.por-defecto mayor que frames-maximos se acota al límite global
        handler.records.clear();
        String yamlOverLimit = """
            imagen:
              frames-maximos: 40
            frames:
              por-defecto: 80
            """;
        FraMCitConfig configOver = FraMCitConfig.load(new StringReader(yamlOverLimit), logger);
        assertEquals(40, configOver.getDefaultFrames(), "frames.por-defecto debe acotarse al límite global de frames-maximos");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("supera el límite global")),
            "Debe registrar aviso de acotación al límite global");

        // Caso 3: Valores inválidos o negativos caen a por defecto con aviso
        handler.records.clear();
        String yamlInvalid = """
            slots:
              por-defecto: -5
              maximo-nodo: "invalido"
            frames:
              por-defecto: -10
              maximo-nodo: -1
            """;
        FraMCitConfig configInv = FraMCitConfig.load(new StringReader(yamlInvalid), logger);
        assertEquals(FraMCitConfig.DEFAULT_SLOTS_DEFAULT, configInv.getDefaultSlots());
        assertEquals(FraMCitConfig.DEFAULT_SLOTS_MAX_NODE, configInv.getMaxSlotsNode());
        assertEquals(configInv.getMaxFrames(), configInv.getDefaultFrames());
        assertEquals(FraMCitConfig.DEFAULT_FRAMES_MAX_NODE, configInv.getMaxFramesNode());
        assertFalse(handler.records.isEmpty(), "Debe emitir avisos para valores inválidos o negativos");
    }
}

