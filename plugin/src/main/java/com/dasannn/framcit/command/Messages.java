package com.dasannn.framcit.command;

/**
 * Mensajes mostrados al usuario en chat segun la seccion 9 de spec.md.
 * Todos en espanol, sin filtrar rutas internas, IPs ni trazas de excepcion.
 */
public final class Messages {

    private Messages() {}

    // Errores de comando y jugador
    public static final String USAGE = "Uso: /imagen <url>";
    public static final String DOWNLOADING = "Descargando imagen...";
    public static final String ONLY_PLAYER = "Este comando solo lo puede usar un jugador.";
    public static final String NO_PERMISSION = "No tienes permiso para usar este comando.";
    public static final String INVENTORY_FULL = "Inventario lleno: el mapa se solto al suelo.";

    // Errores de URL y red (DownloadError)
    public static final String ERROR_MALFORMED_URL = "Esa URL no es valida.";
    public static final String ERROR_DISALLOWED_SCHEME = "Solo se aceptan enlaces http y https.";
    public static final String ERROR_BLOCKED_DESTINATION = "URL no permitida.";
    public static final String ERROR_UNRESOLVABLE_HOST = "No se pudo conectar con ese sitio.";
    public static final String ERROR_CONNECTION_FAILED = "No se pudo conectar con ese sitio.";
    public static final String ERROR_TIMEOUT = "El sitio tardo demasiado en responder.";
    public static final String ERROR_TOO_LARGE = "La imagen supera el limite de 5 MB.";
    public static final String ERROR_QUEUE_FULL = "Hay varias descargas en curso, espera un momento.";

    // Errores de imagen (ImageValidationException)
    public static final String ERROR_NOT_AN_IMAGE = "Ese enlace no apunta a una imagen.";
    public static final String ERROR_UNSUPPORTED_FORMAT = "Formato no soportado. Usa PNG, JPEG, BMP, GIF o WEBP.";
    public static final String ERROR_CORRUPTED_FILE = "Ese archivo esta danado y no se pudo leer.";
    public static final String ERROR_TOO_MANY_FRAMES = "Ese GIF tiene demasiados frames (limite: 100).";
    public static final String ERROR_DIMENSIONS_TOO_LARGE = "Esa imagen es demasiado grande (limite: 4096x4096).";

    // Mensajes de confirmacion
    public static final String STATIC_SUCCESS = "Mapa creado correctamente (imagen estatica).";

    public static String httpError(int statusCode) {
        return "El sitio respondio con error (" + statusCode + "). El enlace puede haber caducado.";
    }

    public static String genericHttpError() {
        return "El sitio respondio con error. El enlace puede haber caducado.";
    }

    /**
     * Confirmacion para cualquier imagen animada (GIF o WEBP).
     * En F4 todavia no hay animacion: se muestra el primer frame. F6 la activa.
     */
    public static String animatedSuccess(int frameCount) {
        return "Imagen animada cargada (" + frameCount + " frames). Se muestra el primer frame como imagen fija.";
    }
}
