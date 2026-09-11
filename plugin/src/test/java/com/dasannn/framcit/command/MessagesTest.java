package com.dasannn.framcit.command;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class MessagesTest {

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

    @AfterEach
    void tearDown() {
        Messages.resetDefaults();
    }

    @Test
    @DisplayName("F4.8 - Todos los mensajes coinciden con la tabla de spec.md seccion 9 por defecto")
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
        assertEquals("Ese GIF tiene demasiados frames (limite: 100).", Messages.ERROR_TOO_MANY_FRAMES);
        assertEquals("Esa imagen es demasiado grande (limite: 4096x4096).", Messages.ERROR_DIMENSIONS_TOO_LARGE);

        assertEquals("Mapa creado correctamente (imagen estatica).", Messages.STATIC_SUCCESS);
        assertEquals("El sitio respondio con error (404). El enlace puede haber caducado.", Messages.httpError(404));
        assertEquals("El sitio respondio con error (500). El enlace puede haber caducado.", Messages.httpError(500));
        assertEquals("El sitio respondio con error. El enlace puede haber caducado.", Messages.genericHttpError());
        assertEquals("Imagen animada cargada (12 frames). Se muestra el primer frame como imagen fija.", Messages.animatedSuccess(12));
    }

    @Test
    @DisplayName("F7.3 & F7.4 - messages.yml con clave ausente usa texto por defecto y avisa")
    void testMessagesYmlMissingKeyUsesDefaultAndWarns() {
        Logger logger = Logger.getLogger("TestLogger-MessagesMissing");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        // Definir solo algunas claves, omitiendo el resto
        String yaml = """
            comando:
              uso: "Sintaxis: /imagen <enlace>"
              descargando: "Bajando archivo..."
            """;

        Messages.load(new StringReader(yaml), logger);

        // Claves presentes se actualizaron
        assertEquals("Sintaxis: /imagen <enlace>", Messages.USAGE);
        assertEquals("Bajando archivo...", Messages.DOWNLOADING);

        // Claves ausentes mantienen el texto por defecto
        assertEquals(Messages.DEFAULT_ONLY_PLAYER, Messages.ONLY_PLAYER);
        assertEquals(Messages.DEFAULT_ERROR_MALFORMED_URL, Messages.ERROR_MALFORMED_URL);
        assertEquals(Messages.DEFAULT_ERROR_BLOCKED_DESTINATION, Messages.ERROR_BLOCKED_DESTINATION);

        // Se emitieron advertencias claras indicando qué claves faltaban
        assertFalse(handler.records.isEmpty(), "Debe emitirse aviso para las claves ausentes");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("comando.solo-jugador")),
            "Debe avisar que falta comando.solo-jugador");
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("red.url-invalida")),
            "Debe avisar que falta red.url-invalida");
    }

    @Test
    @DisplayName("F7.3 - Carga completa de messages.yml personaliza todos los textos sin recompilar")
    void testMessagesYmlCustomLoaded() {
        Logger logger = Logger.getLogger("TestLogger-MessagesCustom");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        String yaml = """
            comando:
              uso: "Modo de empleo: /imagen <url>"
              descargando: "Descarga iniciada..."
              solo-jugador: "Acceso exclusivo para jugadores."
              sin-permiso: "Permiso denegado."
              inventario-lleno: "Suelo: mapa arrojado."
            red:
              url-invalida: "URL incorrecta."
              esquema-no-permitido: "Solo http/https."
              destino-bloqueado: "Destino no autorizado."
              host-no-resuelto: "Sitio inalcanzable."
              conexion-fallida: "Error de red."
              tiempo-agotado: "Timeout del servidor."
              demasiado-grande: "Tamano excedido."
              cola-llena: "Servidor ocupado."
              error-http: "HTTP Error {code}."
              error-http-generico: "HTTP Error."
            imagen:
              no-es-imagen: "No es imagen."
              formato-no-soportado: "Formato invalido."
              archivo-danado: "Archivo corrupto."
              demasiados-frames: "Demasiados frames."
              dimensiones-excesivas: "Demasiado grande."
            exito:
              estatico: "Completado estatico."
              animado: "Completado animado ({frames})."
            admin:
              recarga-exito: "Recarga lista."
              info-no-marco: "No miras marco."
              info-no-encontrado: "Sin datos {id}."
              purgar-confirmacion: "Confirmar {id} con {target}."
              purgar-exito: "Purgado {id}."
              purgar-no-encontrado: "No existe."
              purgar-uso: "Uso purgar."
              listar-vacio: "Vacio."
              listar-encabezado: "Pagina {page}/{total_pages}."
              listar-item: "{id} por {author}."
              listar-pie: "Siguiente {next_page}."
              listar-pagina-invalida: "Invalida {total_pages}."
            """;

        Messages.load(new StringReader(yaml), logger);

        assertEquals("Modo de empleo: /imagen <url>", Messages.USAGE);
        assertEquals("Descarga iniciada...", Messages.DOWNLOADING);
        assertEquals("Acceso exclusivo para jugadores.", Messages.ONLY_PLAYER);
        assertEquals("Permiso denegado.", Messages.NO_PERMISSION);
        assertEquals("Suelo: mapa arrojado.", Messages.INVENTORY_FULL);
        assertEquals("URL incorrecta.", Messages.ERROR_MALFORMED_URL);
        assertEquals("HTTP Error 404.", Messages.httpError(404));
        assertEquals("Completado animado (10).", Messages.animatedSuccess(10));
        assertEquals("Recarga lista.", Messages.ADMIN_RELOAD_SUCCESS);
        assertEquals("No miras marco.", Messages.ADMIN_INFO_NO_FRAME);
        assertEquals("Purgado 42.", Messages.ADMIN_PURGE_SUCCESS.replace("{id}", "42"));
        assertEquals("Vacio.", Messages.ADMIN_LIST_EMPTY);

        assertTrue(handler.records.isEmpty(), "No debe haber advertencias si todas las claves están presentes");
    }

    @Test
    @DisplayName("F7.3 - resetDefaults restablece correctamente todos los valores originales")
    void testResetDefaultsWorks() {
        Messages.USAGE = "Modificado";
        assertEquals("Modificado", Messages.USAGE);

        Messages.resetDefaults();
        assertEquals(Messages.DEFAULT_USAGE, Messages.USAGE);
    }
}
