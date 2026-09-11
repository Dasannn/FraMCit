package com.dasannn.framcit.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.Reader;
import java.util.logging.Logger;

/**
 * Representa la configuración centralizada de FraMCit (F7.1, F7.4).
 *
 * Mapea y valida todos los valores de config.yml.
 * Si alguna clave falta o contiene valores inválidos (texto, negativo, cero),
 * utiliza valores por defecto seguros y emite advertencias detalladas en el Logger.
 *
 * NUNCA impide el arranque del servidor por errores de configuración.
 * Los límites del piso de seguridad (UrlGuard) NO se configuran aquí (F7.2, SEC).
 */
public class FraMCitConfig {

    // Valores por defecto seguros (Spec 5 y ARCHITECTURE 8)
    public static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = 5;
    public static final int DEFAULT_READ_TIMEOUT_SECONDS = 15;
    public static final int DEFAULT_MAX_DOWNLOAD_SIZE_MB = 5;
    public static final int DEFAULT_MAX_REDIRECTS = 3;
    public static final int DEFAULT_DOWNLOAD_THREADS = 4;
    public static final int DEFAULT_QUEUE_CAPACITY = 20;

    public static final int DEFAULT_MAX_DIMENSION = 4096;
    public static final int DEFAULT_MAX_FRAMES = 100;
    public static final int DEFAULT_DELAY_MS = 100;
    public static final int DEFAULT_MIN_DELAY_MS = 100;

    public static final double DEFAULT_GAZE_MAX_DISTANCE = 24.0;
    public static final int DEFAULT_GAZE_PERIOD_TICKS = 5;

    public static final int DEFAULT_MAX_CACHE_MB = 128;

    // Mínimos permitidos para prevenir configuraciones peligrosas
    public static final int MIN_CONNECT_TIMEOUT_SECONDS = 1;
    public static final int MIN_READ_TIMEOUT_SECONDS = 1;
    public static final int MIN_MAX_DOWNLOAD_SIZE_MB = 1;
    public static final int MIN_MAX_REDIRECTS = 0;
    public static final int MIN_DOWNLOAD_THREADS = 1;
    public static final int MIN_QUEUE_CAPACITY = 1;

    public static final int MIN_MAX_DIMENSION = 128;
    public static final int MIN_MAX_FRAMES = 1;
    public static final int MIN_DEFAULT_DELAY_MS = 10;
    public static final int MIN_MIN_DELAY_MS = 10;

    public static final double MIN_GAZE_MAX_DISTANCE = 1.0;
    public static final int MIN_GAZE_PERIOD_TICKS = 1;

    public static final int MIN_MAX_CACHE_MB = 1;

    private final int connectTimeoutSeconds;
    private final int readTimeoutSeconds;
    private final int maxDownloadSizeMb;
    private final int maxRedirects;
    private final int downloadThreads;
    private final int queueCapacity;

    private final int maxDimension;
    private final int maxFrames;
    private final int defaultDelayMs;
    private final int minDelayMs;

    private final double gazeMaxDistance;
    private final int gazePeriodTicks;

    private final int maxCacheMb;

    public FraMCitConfig(
        int connectTimeoutSeconds,
        int readTimeoutSeconds,
        int maxDownloadSizeMb,
        int maxRedirects,
        int downloadThreads,
        int queueCapacity,
        int maxDimension,
        int maxFrames,
        int defaultDelayMs,
        int minDelayMs,
        double gazeMaxDistance,
        int gazePeriodTicks,
        int maxCacheMb
    ) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
        this.readTimeoutSeconds = readTimeoutSeconds;
        this.maxDownloadSizeMb = maxDownloadSizeMb;
        this.maxRedirects = maxRedirects;
        this.downloadThreads = downloadThreads;
        this.queueCapacity = queueCapacity;
        this.maxDimension = maxDimension;
        this.maxFrames = maxFrames;
        this.defaultDelayMs = defaultDelayMs;
        this.minDelayMs = minDelayMs;
        this.gazeMaxDistance = gazeMaxDistance;
        this.gazePeriodTicks = gazePeriodTicks;
        this.maxCacheMb = maxCacheMb;
    }

    /**
     * Retorna una instancia con todos los valores seguros por defecto.
     */
    public static FraMCitConfig defaults() {
        return new FraMCitConfig(
            DEFAULT_CONNECT_TIMEOUT_SECONDS,
            DEFAULT_READ_TIMEOUT_SECONDS,
            DEFAULT_MAX_DOWNLOAD_SIZE_MB,
            DEFAULT_MAX_REDIRECTS,
            DEFAULT_DOWNLOAD_THREADS,
            DEFAULT_QUEUE_CAPACITY,
            DEFAULT_MAX_DIMENSION,
            DEFAULT_MAX_FRAMES,
            DEFAULT_DELAY_MS,
            DEFAULT_MIN_DELAY_MS,
            DEFAULT_GAZE_MAX_DISTANCE,
            DEFAULT_GAZE_PERIOD_TICKS,
            DEFAULT_MAX_CACHE_MB
        );
    }

    /**
     * Carga la configuración desde un archivo YAML.
     */
    public static FraMCitConfig load(File file, Logger logger) {
        if (file == null || !file.exists()) {
            if (logger != null) {
                logger.warning("Archivo de configuración no encontrado. Usando valores por defecto.");
            }
            return defaults();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        return load(config, logger);
    }

    /**
     * Carga la configuración desde un Reader (útil para pruebas unitarias).
     */
    public static FraMCitConfig load(Reader reader, Logger logger) {
        if (reader == null) {
            return defaults();
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
        return load(config, logger);
    }

    /**
     * Carga y sanea cada parámetro desde una ConfigurationSection de Bukkit.
     */
    public static FraMCitConfig load(ConfigurationSection config, Logger logger) {
        if (config == null) {
            if (logger != null) {
                logger.warning("Sección de configuración nula. Usando valores por defecto.");
            }
            return defaults();
        }

        int connectTimeout = getInt(config, "descarga.tiempo-conexion-segundos", DEFAULT_CONNECT_TIMEOUT_SECONDS, MIN_CONNECT_TIMEOUT_SECONDS, logger);
        int readTimeout = getInt(config, "descarga.tiempo-lectura-segundos", DEFAULT_READ_TIMEOUT_SECONDS, MIN_READ_TIMEOUT_SECONDS, logger);
        int maxDownloadMb = getInt(config, "descarga.tamano-maximo-mb", DEFAULT_MAX_DOWNLOAD_SIZE_MB, MIN_MAX_DOWNLOAD_SIZE_MB, logger);
        int maxRedirects = getInt(config, "descarga.max-redirecciones", DEFAULT_MAX_REDIRECTS, MIN_MAX_REDIRECTS, logger);
        int threads = getInt(config, "descarga.hilos", DEFAULT_DOWNLOAD_THREADS, MIN_DOWNLOAD_THREADS, logger);
        int queueCap = getInt(config, "descarga.capacidad-cola", DEFAULT_QUEUE_CAPACITY, MIN_QUEUE_CAPACITY, logger);

        int maxDim = getInt(config, "imagen.dimension-maxima", DEFAULT_MAX_DIMENSION, MIN_MAX_DIMENSION, logger);
        int maxF = getInt(config, "imagen.frames-maximos", DEFAULT_MAX_FRAMES, MIN_MAX_FRAMES, logger);
        int defDelay = getInt(config, "imagen.retardo-defecto-ms", DEFAULT_DELAY_MS, MIN_DEFAULT_DELAY_MS, logger);
        int minDelay = getInt(config, "imagen.retardo-minimo-ms", DEFAULT_MIN_DELAY_MS, MIN_MIN_DELAY_MS, logger);

        double maxDist = getDouble(config, "animacion.distancia-maxima-vision", DEFAULT_GAZE_MAX_DISTANCE, MIN_GAZE_MAX_DISTANCE, logger);
        int periodTicks = getInt(config, "animacion.periodo-rastreo-ticks", DEFAULT_GAZE_PERIOD_TICKS, MIN_GAZE_PERIOD_TICKS, logger);

        int maxCacheMb = getInt(config, "cache.tamano-maximo-mb", DEFAULT_MAX_CACHE_MB, MIN_MAX_CACHE_MB, logger);

        return new FraMCitConfig(
            connectTimeout,
            readTimeout,
            maxDownloadMb,
            maxRedirects,
            threads,
            queueCap,
            maxDim,
            maxF,
            defDelay,
            minDelay,
            maxDist,
            periodTicks,
            maxCacheMb
        );
    }

    private static String resolveKey(ConfigurationSection config, String path) {
        if (config.contains(path)) {
            return path;
        }
        int dot = path.lastIndexOf('.');
        if (dot >= 0) {
            String flat = path.substring(dot + 1);
            if (config.contains(flat)) {
                return flat;
            }
        }
        return null;
    }

    private static int getInt(ConfigurationSection config, String path, int defaultValue, int min, Logger logger) {
        String key = resolveKey(config, path);
        if (key == null) {
            if (logger != null) {
                logger.warning(String.format("Clave ausente en config.yml: '%s'. Usando valor por defecto: %d", path, defaultValue));
            }
            return defaultValue;
        }

        Object raw = config.get(key);
        if (raw instanceof Number num) {
            int val = num.intValue();
            if (val < min) {
                if (logger != null) {
                    logger.warning(String.format("Valor inválido para '%s': %d (mínimo permitido: %d). Usando valor por defecto: %d", path, val, min, defaultValue));
                }
                return defaultValue;
            }
            return val;
        }

        if (raw instanceof String str) {
            try {
                int val = Integer.parseInt(str.trim());
                if (val < min) {
                    if (logger != null) {
                        logger.warning(String.format("Valor inválido para '%s': %d (mínimo permitido: %d). Usando valor por defecto: %d", path, val, min, defaultValue));
                    }
                    return defaultValue;
                }
                return val;
            } catch (NumberFormatException e) {
                if (logger != null) {
                    logger.warning(String.format("Valor inválido para '%s': '%s' (se esperaba un número entero). Usando valor por defecto: %d", path, str, defaultValue));
                }
                return defaultValue;
            }
        }

        if (logger != null) {
            logger.warning(String.format("Valor inválido para '%s': '%s' (tipo no soportado). Usando valor por defecto: %d", path, raw, defaultValue));
        }
        return defaultValue;
    }

    private static double getDouble(ConfigurationSection config, String path, double defaultValue, double min, Logger logger) {
        String key = resolveKey(config, path);
        if (key == null) {
            if (logger != null) {
                logger.warning(String.format("Clave ausente en config.yml: '%s'. Usando valor por defecto: %.1f", path, defaultValue));
            }
            return defaultValue;
        }

        Object raw = config.get(key);
        if (raw instanceof Number num) {
            double val = num.doubleValue();
            if (val < min) {
                if (logger != null) {
                    logger.warning(String.format("Valor inválido para '%s': %.1f (mínimo permitido: %.1f). Usando valor por defecto: %.1f", path, val, min, defaultValue));
                }
                return defaultValue;
            }
            return val;
        }

        if (raw instanceof String str) {
            try {
                double val = Double.parseDouble(str.trim());
                if (val < min) {
                    if (logger != null) {
                        logger.warning(String.format("Valor inválido para '%s': %.1f (mínimo permitido: %.1f). Usando valor por defecto: %.1f", path, val, min, defaultValue));
                    }
                    return defaultValue;
                }
                return val;
            } catch (NumberFormatException e) {
                if (logger != null) {
                    logger.warning(String.format("Valor inválido para '%s': '%s' (se esperaba un número decimal). Usando valor por defecto: %.1f", path, str, defaultValue));
                }
                return defaultValue;
            }
        }

        if (logger != null) {
            logger.warning(String.format("Valor inválido para '%s': '%s' (tipo no soportado). Usando valor por defecto: %.1f", path, raw, defaultValue));
        }
        return defaultValue;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public int getMaxDownloadSizeMb() {
        return maxDownloadSizeMb;
    }

    public long getMaxDownloadSizeBytes() {
        return (long) maxDownloadSizeMb * 1024L * 1024L;
    }

    public int getMaxRedirects() {
        return maxRedirects;
    }

    public int getDownloadThreads() {
        return downloadThreads;
    }

    public int getQueueCapacity() {
        return queueCapacity;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public int getMaxFrames() {
        return maxFrames;
    }

    public int getDefaultDelayMs() {
        return defaultDelayMs;
    }

    public int getMinDelayMs() {
        return minDelayMs;
    }

    public double getGazeMaxDistance() {
        return gazeMaxDistance;
    }

    public int getGazePeriodTicks() {
        return gazePeriodTicks;
    }

    public int getMaxCacheMb() {
        return maxCacheMb;
    }

    public long getMaxCacheBytes() {
        return (long) maxCacheMb * 1024L * 1024L;
    }
}
