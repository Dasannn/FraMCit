package com.dasannn.framcit.command;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.Reader;
import java.util.logging.Logger;

/**
 * Mensajes mostrados al usuario en chat según la sección 9 de spec.md y comandos de administración (F7.3).
 * Todos en español, editables sin recompilar mediante messages.yml.
 *
 * Si falta una clave o viene vacía, se utiliza el texto por defecto y se emite
 * una advertencia clara en el log del servidor.
 */
public final class Messages {

    private Messages() {}

    // Valores por defecto inmutables
    public static final String DEFAULT_USAGE = "Uso: /imagen <url>";
    public static final String DEFAULT_DOWNLOADING = "Descargando imagen...";
    public static final String DEFAULT_ONLY_PLAYER = "Este comando solo lo puede usar un jugador.";
    public static final String DEFAULT_NO_PERMISSION = "No tienes permiso para usar este comando.";
    public static final String DEFAULT_INVENTORY_FULL = "Inventario lleno: el mapa se solto al suelo.";

    public static final String DEFAULT_ERROR_MALFORMED_URL = "Esa URL no es valida.";
    public static final String DEFAULT_ERROR_DISALLOWED_SCHEME = "Solo se aceptan enlaces http y https.";
    public static final String DEFAULT_ERROR_BLOCKED_DESTINATION = "URL no permitida.";
    public static final String DEFAULT_ERROR_UNRESOLVABLE_HOST = "No se pudo conectar con ese sitio.";
    public static final String DEFAULT_ERROR_CONNECTION_FAILED = "No se pudo conectar con ese sitio.";
    public static final String DEFAULT_ERROR_TIMEOUT = "El sitio tardo demasiado en responder.";
    public static final String DEFAULT_ERROR_TOO_LARGE = "La imagen supera el limite de 5 MB.";
    public static final String DEFAULT_ERROR_QUEUE_FULL = "Hay varias descargas en curso, espera un momento.";

    public static final String DEFAULT_ERROR_NOT_AN_IMAGE = "Ese enlace no apunta a una imagen.";
    public static final String DEFAULT_ERROR_UNSUPPORTED_FORMAT = "Formato no soportado. Usa PNG, JPEG, BMP, GIF o WEBP.";
    public static final String DEFAULT_ERROR_CORRUPTED_FILE = "Ese archivo esta danado y no se pudo leer.";
    public static final String DEFAULT_ERROR_TOO_MANY_FRAMES = "Ese GIF tiene demasiados frames (limite: 100).";
    public static final String DEFAULT_ERROR_DIMENSIONS_TOO_LARGE = "Esa imagen es demasiado grande (limite: 4096x4096).";

    public static final String DEFAULT_STATIC_SUCCESS = "Mapa creado correctamente (imagen estatica).";
    public static final String DEFAULT_HTTP_ERROR_TEMPLATE = "El sitio respondio con error ({code}). El enlace puede haber caducado.";
    public static final String DEFAULT_GENERIC_HTTP_ERROR = "El sitio respondio con error. El enlace puede haber caducado.";
    public static final String DEFAULT_ANIMATED_SUCCESS_TEMPLATE = "Imagen animada cargada ({frames} frames). Se muestra el primer frame como imagen fija.";

    // Mensajes de administración por defecto
    public static final String DEFAULT_ADMIN_RELOAD_SUCCESS = "Configuración y mensajes recargados. Aplicado al vuelo: límites de descarga, timeouts, redirecciones, dimensiones, frames, retardos, distancia de visión y tamaño de caché. NOTA: hilos de descarga, capacidad de cola y período de rastreo requieren REINICIAR el servidor.";
    public static final String DEFAULT_ADMIN_INFO_NO_FRAME = "Debes estar mirando a un marco con un mapa de FraMCit.";
    public static final String DEFAULT_ADMIN_INFO_NOT_FOUND = "No se encontró información para el mapa con ID: {id}.";
    public static final String DEFAULT_ADMIN_PURGE_CONFIRMATION = "¿Estás seguro de que deseas purgar la imagen id={id}? Esto la borrará del cache y del disco, y sus mapas quedarán en blanco. Para confirmar, ejecuta: /imagen purgar {target} confirmar";
    public static final String DEFAULT_ADMIN_PURGE_SUCCESS = "Imagen purgada correctamente (id: {id}). Los mapas asociados ahora están en blanco.";
    public static final String DEFAULT_ADMIN_PURGE_NOT_FOUND = "No se encontró ninguna imagen con ese ID o URL.";
    public static final String DEFAULT_ADMIN_PURGE_USAGE = "Uso: /imagen purgar <url|id> [confirmar]";
    public static final String DEFAULT_ADMIN_LIST_EMPTY = "No hay imágenes almacenadas.";
    public static final String DEFAULT_ADMIN_LIST_HEADER = "--- Imágenes almacenadas (Página {page}/{total_pages}) ---";
    public static final String DEFAULT_ADMIN_LIST_ITEM = "#{id} | {author} | {date} | {url}";
    public static final String DEFAULT_ADMIN_LIST_FOOTER = "Usa /imagen listar {next_page} para ver más.";
    public static final String DEFAULT_ADMIN_LIST_PAGE_OUT_OF_RANGE = "Página fuera de rango. Las páginas van de 1 a {total_pages}.";

    // Mensajes de economía por defecto (Spec 002 Sección 8, F10.13)
    public static final String DEFAULT_ECONOMY_INSUFFICIENT_FUNDS = "No tienes suficiente dinero. Cuesta {precio} y tienes {saldo}.";
    public static final String DEFAULT_ECONOMY_CHARGE_SUCCESS = "Se te han cobrado {precio} por la imagen.";
    public static final String DEFAULT_ECONOMY_FUNDS_LOST = "Ya no tienes suficiente dinero: la imagen no se entrego.";
    public static final String DEFAULT_ECONOMY_PAYMENT_FAILED = "No se pudo procesar el pago. Avisa al staff.";

    // Campos activos (mutables mediante recarga de messages.yml)
    public static String USAGE = DEFAULT_USAGE;
    public static String DOWNLOADING = DEFAULT_DOWNLOADING;
    public static String ONLY_PLAYER = DEFAULT_ONLY_PLAYER;
    public static String NO_PERMISSION = DEFAULT_NO_PERMISSION;
    public static String INVENTORY_FULL = DEFAULT_INVENTORY_FULL;

    public static String ERROR_MALFORMED_URL = DEFAULT_ERROR_MALFORMED_URL;
    public static String ERROR_DISALLOWED_SCHEME = DEFAULT_ERROR_DISALLOWED_SCHEME;
    public static String ERROR_BLOCKED_DESTINATION = DEFAULT_ERROR_BLOCKED_DESTINATION;
    public static String ERROR_UNRESOLVABLE_HOST = DEFAULT_ERROR_UNRESOLVABLE_HOST;
    public static String ERROR_CONNECTION_FAILED = DEFAULT_ERROR_CONNECTION_FAILED;
    public static String ERROR_TIMEOUT = DEFAULT_ERROR_TIMEOUT;
    public static String ERROR_TOO_LARGE = DEFAULT_ERROR_TOO_LARGE;
    public static String ERROR_QUEUE_FULL = DEFAULT_ERROR_QUEUE_FULL;

    public static String ERROR_NOT_AN_IMAGE = DEFAULT_ERROR_NOT_AN_IMAGE;
    public static String ERROR_UNSUPPORTED_FORMAT = DEFAULT_ERROR_UNSUPPORTED_FORMAT;
    public static String ERROR_CORRUPTED_FILE = DEFAULT_ERROR_CORRUPTED_FILE;
    public static String ERROR_TOO_MANY_FRAMES = DEFAULT_ERROR_TOO_MANY_FRAMES;
    public static String ERROR_DIMENSIONS_TOO_LARGE = DEFAULT_ERROR_DIMENSIONS_TOO_LARGE;

    public static String STATIC_SUCCESS = DEFAULT_STATIC_SUCCESS;
    public static String HTTP_ERROR_TEMPLATE = DEFAULT_HTTP_ERROR_TEMPLATE;
    public static String GENERIC_HTTP_ERROR = DEFAULT_GENERIC_HTTP_ERROR;
    public static String ANIMATED_SUCCESS_TEMPLATE = DEFAULT_ANIMATED_SUCCESS_TEMPLATE;

    public static String ADMIN_RELOAD_SUCCESS = DEFAULT_ADMIN_RELOAD_SUCCESS;
    public static String ADMIN_INFO_NO_FRAME = DEFAULT_ADMIN_INFO_NO_FRAME;
    public static String ADMIN_INFO_NOT_FOUND = DEFAULT_ADMIN_INFO_NOT_FOUND;
    public static String ADMIN_PURGE_CONFIRMATION = DEFAULT_ADMIN_PURGE_CONFIRMATION;
    public static String ADMIN_PURGE_SUCCESS = DEFAULT_ADMIN_PURGE_SUCCESS;
    public static String ADMIN_PURGE_NOT_FOUND = DEFAULT_ADMIN_PURGE_NOT_FOUND;
    public static String ADMIN_PURGE_USAGE = DEFAULT_ADMIN_PURGE_USAGE;
    public static String ADMIN_LIST_EMPTY = DEFAULT_ADMIN_LIST_EMPTY;
    public static String ADMIN_LIST_HEADER = DEFAULT_ADMIN_LIST_HEADER;
    public static String ADMIN_LIST_ITEM = DEFAULT_ADMIN_LIST_ITEM;
    public static String ADMIN_LIST_FOOTER = DEFAULT_ADMIN_LIST_FOOTER;
    public static String ADMIN_LIST_PAGE_OUT_OF_RANGE = DEFAULT_ADMIN_LIST_PAGE_OUT_OF_RANGE;

    public static String ECONOMY_INSUFFICIENT_FUNDS = DEFAULT_ECONOMY_INSUFFICIENT_FUNDS;
    public static String ECONOMY_CHARGE_SUCCESS = DEFAULT_ECONOMY_CHARGE_SUCCESS;
    public static String ECONOMY_FUNDS_LOST = DEFAULT_ECONOMY_FUNDS_LOST;
    public static String ECONOMY_PAYMENT_FAILED = DEFAULT_ECONOMY_PAYMENT_FAILED;

    /**
     * Restablece todos los mensajes a sus valores originales por defecto.
     */
    public static void resetDefaults() {
        USAGE = DEFAULT_USAGE;
        DOWNLOADING = DEFAULT_DOWNLOADING;
        ONLY_PLAYER = DEFAULT_ONLY_PLAYER;
        NO_PERMISSION = DEFAULT_NO_PERMISSION;
        INVENTORY_FULL = DEFAULT_INVENTORY_FULL;

        ERROR_MALFORMED_URL = DEFAULT_ERROR_MALFORMED_URL;
        ERROR_DISALLOWED_SCHEME = DEFAULT_ERROR_DISALLOWED_SCHEME;
        ERROR_BLOCKED_DESTINATION = DEFAULT_ERROR_BLOCKED_DESTINATION;
        ERROR_UNRESOLVABLE_HOST = DEFAULT_ERROR_UNRESOLVABLE_HOST;
        ERROR_CONNECTION_FAILED = DEFAULT_ERROR_CONNECTION_FAILED;
        ERROR_TIMEOUT = DEFAULT_ERROR_TIMEOUT;
        ERROR_TOO_LARGE = DEFAULT_ERROR_TOO_LARGE;
        ERROR_QUEUE_FULL = DEFAULT_ERROR_QUEUE_FULL;

        ERROR_NOT_AN_IMAGE = DEFAULT_ERROR_NOT_AN_IMAGE;
        ERROR_UNSUPPORTED_FORMAT = DEFAULT_ERROR_UNSUPPORTED_FORMAT;
        ERROR_CORRUPTED_FILE = DEFAULT_ERROR_CORRUPTED_FILE;
        ERROR_TOO_MANY_FRAMES = DEFAULT_ERROR_TOO_MANY_FRAMES;
        ERROR_DIMENSIONS_TOO_LARGE = DEFAULT_ERROR_DIMENSIONS_TOO_LARGE;

        STATIC_SUCCESS = DEFAULT_STATIC_SUCCESS;
        HTTP_ERROR_TEMPLATE = DEFAULT_HTTP_ERROR_TEMPLATE;
        GENERIC_HTTP_ERROR = DEFAULT_GENERIC_HTTP_ERROR;
        ANIMATED_SUCCESS_TEMPLATE = DEFAULT_ANIMATED_SUCCESS_TEMPLATE;

        ADMIN_RELOAD_SUCCESS = DEFAULT_ADMIN_RELOAD_SUCCESS;
        ADMIN_INFO_NO_FRAME = DEFAULT_ADMIN_INFO_NO_FRAME;
        ADMIN_INFO_NOT_FOUND = DEFAULT_ADMIN_INFO_NOT_FOUND;
        ADMIN_PURGE_CONFIRMATION = DEFAULT_ADMIN_PURGE_CONFIRMATION;
        ADMIN_PURGE_SUCCESS = DEFAULT_ADMIN_PURGE_SUCCESS;
        ADMIN_PURGE_NOT_FOUND = DEFAULT_ADMIN_PURGE_NOT_FOUND;
        ADMIN_PURGE_USAGE = DEFAULT_ADMIN_PURGE_USAGE;
        ADMIN_LIST_EMPTY = DEFAULT_ADMIN_LIST_EMPTY;
        ADMIN_LIST_HEADER = DEFAULT_ADMIN_LIST_HEADER;
        ADMIN_LIST_ITEM = DEFAULT_ADMIN_LIST_ITEM;
        ADMIN_LIST_FOOTER = DEFAULT_ADMIN_LIST_FOOTER;
        ADMIN_LIST_PAGE_OUT_OF_RANGE = DEFAULT_ADMIN_LIST_PAGE_OUT_OF_RANGE;

        ECONOMY_INSUFFICIENT_FUNDS = DEFAULT_ECONOMY_INSUFFICIENT_FUNDS;
        ECONOMY_CHARGE_SUCCESS = DEFAULT_ECONOMY_CHARGE_SUCCESS;
        ECONOMY_FUNDS_LOST = DEFAULT_ECONOMY_FUNDS_LOST;
        ECONOMY_PAYMENT_FAILED = DEFAULT_ECONOMY_PAYMENT_FAILED;
    }

    /**
     * Carga los mensajes desde un archivo File messages.yml.
     */
    public static void load(File file, Logger logger) {
        if (file == null || !file.exists()) {
            if (logger != null) {
                logger.warning("Archivo messages.yml no encontrado. Usando mensajes por defecto.");
            }
            resetDefaults();
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        load(config, logger);
    }

    /**
     * Carga los mensajes desde un Reader (útil para pruebas unitarias).
     */
    public static void load(Reader reader, Logger logger) {
        if (reader == null) {
            resetDefaults();
            return;
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(reader);
        load(config, logger);
    }

    /**
     * Carga los mensajes desde una sección de configuración Yaml.
     */
    public static void load(ConfigurationSection config, Logger logger) {
        if (config == null) {
            resetDefaults();
            return;
        }

        USAGE = getMessage(config, "comando.uso", DEFAULT_USAGE, logger);
        DOWNLOADING = getMessage(config, "comando.descargando", DEFAULT_DOWNLOADING, logger);
        ONLY_PLAYER = getMessage(config, "comando.solo-jugador", DEFAULT_ONLY_PLAYER, logger);
        NO_PERMISSION = getMessage(config, "comando.sin-permiso", DEFAULT_NO_PERMISSION, logger);
        INVENTORY_FULL = getMessage(config, "comando.inventario-lleno", DEFAULT_INVENTORY_FULL, logger);

        ERROR_MALFORMED_URL = getMessage(config, "red.url-invalida", DEFAULT_ERROR_MALFORMED_URL, logger);
        ERROR_DISALLOWED_SCHEME = getMessage(config, "red.esquema-no-permitido", DEFAULT_ERROR_DISALLOWED_SCHEME, logger);
        ERROR_BLOCKED_DESTINATION = getMessage(config, "red.destino-bloqueado", DEFAULT_ERROR_BLOCKED_DESTINATION, logger);
        ERROR_UNRESOLVABLE_HOST = getMessage(config, "red.host-no-resuelto", DEFAULT_ERROR_UNRESOLVABLE_HOST, logger);
        ERROR_CONNECTION_FAILED = getMessage(config, "red.conexion-fallida", DEFAULT_ERROR_CONNECTION_FAILED, logger);
        ERROR_TIMEOUT = getMessage(config, "red.tiempo-agotado", DEFAULT_ERROR_TIMEOUT, logger);
        ERROR_TOO_LARGE = getMessage(config, "red.demasiado-grande", DEFAULT_ERROR_TOO_LARGE, logger);
        ERROR_QUEUE_FULL = getMessage(config, "red.cola-llena", DEFAULT_ERROR_QUEUE_FULL, logger);

        ERROR_NOT_AN_IMAGE = getMessage(config, "imagen.no-es-imagen", DEFAULT_ERROR_NOT_AN_IMAGE, logger);
        ERROR_UNSUPPORTED_FORMAT = getMessage(config, "imagen.formato-no-soportado", DEFAULT_ERROR_UNSUPPORTED_FORMAT, logger);
        ERROR_CORRUPTED_FILE = getMessage(config, "imagen.archivo-danado", DEFAULT_ERROR_CORRUPTED_FILE, logger);
        ERROR_TOO_MANY_FRAMES = getMessage(config, "imagen.demasiados-frames", DEFAULT_ERROR_TOO_MANY_FRAMES, logger);
        ERROR_DIMENSIONS_TOO_LARGE = getMessage(config, "imagen.dimensiones-excesivas", DEFAULT_ERROR_DIMENSIONS_TOO_LARGE, logger);

        STATIC_SUCCESS = getMessage(config, "exito.estatico", DEFAULT_STATIC_SUCCESS, logger);
        HTTP_ERROR_TEMPLATE = getMessage(config, "red.error-http", DEFAULT_HTTP_ERROR_TEMPLATE, logger);
        GENERIC_HTTP_ERROR = getMessage(config, "red.error-http-generico", DEFAULT_GENERIC_HTTP_ERROR, logger);
        ANIMATED_SUCCESS_TEMPLATE = getMessage(config, "exito.animado", DEFAULT_ANIMATED_SUCCESS_TEMPLATE, logger);

        ADMIN_RELOAD_SUCCESS = getMessage(config, "admin.recarga-exito", DEFAULT_ADMIN_RELOAD_SUCCESS, logger);
        ADMIN_INFO_NO_FRAME = getMessage(config, "admin.info-no-marco", DEFAULT_ADMIN_INFO_NO_FRAME, logger);
        ADMIN_INFO_NOT_FOUND = getMessage(config, "admin.info-no-encontrado", DEFAULT_ADMIN_INFO_NOT_FOUND, logger);
        ADMIN_PURGE_CONFIRMATION = getMessage(config, "admin.purgar-confirmacion", DEFAULT_ADMIN_PURGE_CONFIRMATION, logger);
        ADMIN_PURGE_SUCCESS = getMessage(config, "admin.purgar-exito", DEFAULT_ADMIN_PURGE_SUCCESS, logger);
        ADMIN_PURGE_NOT_FOUND = getMessage(config, "admin.purgar-no-encontrado", DEFAULT_ADMIN_PURGE_NOT_FOUND, logger);
        ADMIN_PURGE_USAGE = getMessage(config, "admin.purgar-uso", DEFAULT_ADMIN_PURGE_USAGE, logger);
        ADMIN_LIST_EMPTY = getMessage(config, "admin.listar-vacio", DEFAULT_ADMIN_LIST_EMPTY, logger);
        ADMIN_LIST_HEADER = getMessage(config, "admin.listar-encabezado", DEFAULT_ADMIN_LIST_HEADER, logger);
        ADMIN_LIST_ITEM = getMessage(config, "admin.listar-item", DEFAULT_ADMIN_LIST_ITEM, logger);
        ADMIN_LIST_FOOTER = getMessage(config, "admin.listar-pie", DEFAULT_ADMIN_LIST_FOOTER, logger);
        ADMIN_LIST_PAGE_OUT_OF_RANGE = getMessage(config, "admin.listar-pagina-invalida", DEFAULT_ADMIN_LIST_PAGE_OUT_OF_RANGE, logger);

        ECONOMY_INSUFFICIENT_FUNDS = getMessage(config, "economia.saldo-insuficiente", DEFAULT_ECONOMY_INSUFFICIENT_FUNDS, logger);
        String chargeKey = config.contains("economia.cobro-correcto") ? "economia.cobro-correcto" : "economia.cobro-exitoso";
        ECONOMY_CHARGE_SUCCESS = getMessage(config, chargeKey, DEFAULT_ECONOMY_CHARGE_SUCCESS, logger);
        ECONOMY_FUNDS_LOST = getMessage(config, "economia.saldo-perdido", DEFAULT_ECONOMY_FUNDS_LOST, logger);
        ECONOMY_PAYMENT_FAILED = getMessage(config, "economia.fallo-pago", DEFAULT_ECONOMY_PAYMENT_FAILED, logger);
    }

    private static String getMessage(ConfigurationSection config, String path, String defaultValue, Logger logger) {
        String key = path;
        if (!config.contains(key)) {
            int dot = path.lastIndexOf('.');
            if (dot >= 0) {
                String flat = path.substring(dot + 1);
                if (config.contains(flat)) {
                    key = flat;
                }
            }
        }

        if (!config.contains(key)) {
            if (logger != null) {
                logger.warning(String.format("Clave ausente en messages.yml: '%s'. Usando texto por defecto: '%s'", path, defaultValue));
            }
            return defaultValue;
        }

        String val = config.getString(key);
        if (val == null || val.isBlank()) {
            if (logger != null) {
                logger.warning(String.format("Clave vacía en messages.yml: '%s'. Usando texto por defecto: '%s'", path, defaultValue));
            }
            return defaultValue;
        }

        return val;
    }

    public static String httpError(int statusCode) {
        return HTTP_ERROR_TEMPLATE.replace("{code}", String.valueOf(statusCode))
                                  .replace("{codigo}", String.valueOf(statusCode));
    }

    public static String genericHttpError() {
        return GENERIC_HTTP_ERROR;
    }

    public static String animatedSuccess(int frameCount) {
        return ANIMATED_SUCCESS_TEMPLATE.replace("{frames}", String.valueOf(frameCount));
    }

    public static String tooLargeError(long maxBytes) {
        long mb = maxBytes / (1024L * 1024L);
        if (mb <= 0) {
            mb = 1;
        }
        return ERROR_TOO_LARGE.replace("5 MB", mb + " MB");
    }

    public static String insufficientFunds(String price, String balance) {
        return ECONOMY_INSUFFICIENT_FUNDS
            .replace("{precio}", price)
            .replace("{saldo}", balance);
    }

    public static String chargeSuccess(String price) {
        return ECONOMY_CHARGE_SUCCESS.replace("{precio}", price);
    }
}
