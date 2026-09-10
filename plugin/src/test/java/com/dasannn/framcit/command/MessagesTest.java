package com.dasannn.framcit.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MessagesTest {

    @Test
    @DisplayName("F4.8 - Todos los mensajes coinciden con la tabla de spec.md seccion 9")
    void testSpec9MessagesExactMatch() {
        assertEquals("Uso: /imagen <url>", Messages.USAGE);
        assertEquals("Descargando imagen...", Messages.DOWNLOADING);
        assertEquals("Este comando solo lo puede usar un jugador.", Messages.ONLY_PLAYER);
        assertEquals("No tienes permiso para usar este comando.", Messages.NO_PERMISSION);
        assertEquals("Inventario lleno: el mapa se solto al suelo.", Messages.INVENTORY_FULL);

        assertEquals("Esa URL no es valida.", Messages.ERROR_MALFORMED_URL);
        assertEquals("Solo se aceptan enlaces http y https.", Messages.ERROR_DISALLOWED_SCHEME);
        assertEquals("URL no permitida.", Messages.ERROR_BLOCKED_DESTINATION);
        assertEquals("No se pudo conectar con ese sitio.", Messages.ERROR_UNRESOLVABLE_HOST);
        assertEquals("No se pudo conectar con ese sitio.", Messages.ERROR_CONNECTION_FAILED);
        assertEquals("El sitio tardo demasiado en responder.", Messages.ERROR_TIMEOUT);
        assertEquals("La imagen supera el limite de 5 MB.", Messages.ERROR_TOO_LARGE);
        assertEquals("Hay varias descargas en curso, espera un momento.", Messages.ERROR_QUEUE_FULL);

        assertEquals("Ese enlace no apunta a una imagen.", Messages.ERROR_NOT_AN_IMAGE);
        assertEquals("Formato no soportado. Usa PNG, JPEG, BMP, GIF o WEBP.", Messages.ERROR_UNSUPPORTED_FORMAT);
        assertEquals("Ese archivo esta danado y no se pudo leer.", Messages.ERROR_CORRUPTED_FILE);
        assertEquals("Aviso: este WEBP animado se coloco como imagen fija.", Messages.ERROR_WEBP_ANIMATED_UNSUPPORTED);
        assertEquals("Ese GIF tiene demasiados frames (limite: 100).", Messages.ERROR_TOO_MANY_FRAMES);
        assertEquals("Esa imagen es demasiado grande (limite: 4096x4096).", Messages.ERROR_DIMENSIONS_TOO_LARGE);

        assertEquals("Mapa creado correctamente (imagen estatica).", Messages.STATIC_SUCCESS);
        assertEquals("El sitio respondio con error (404). El enlace puede haber caducado.", Messages.httpError(404));
        assertEquals("El sitio respondio con error (500). El enlace puede haber caducado.", Messages.httpError(500));
        assertEquals("El sitio respondio con error. El enlace puede haber caducado.", Messages.genericHttpError());
        assertEquals("GIF cargado (12 frames). Se muestra el primer frame como imagen fija.", Messages.gifSuccess(12));
    }
}
