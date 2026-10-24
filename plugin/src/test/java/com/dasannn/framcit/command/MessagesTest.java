package com.dasannn.framcit.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    @DisplayName("Las variables dinámicas con ampersand se mantienen como texto literal al aplicar color")
    void testColoredTemplateDoesNotParseDynamicAmpersandsAsFormatting() {
        String url = "https://example.test/image.png?size=1&b=blue";
        Component message = Messages.componentTemplate("&bURL: {url}",
            Map.of("url", Component.text(url, NamedTextColor.AQUA)));

        assertEquals("URL: " + url, PlainTextComponentSerializer.plainText().serialize(message));
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

        // Spec 002 Sección 8
        assertEquals("No tienes suficiente dinero. Cuesta {precio} y tienes {saldo}.", Messages.ECONOMY_INSUFFICIENT_FUNDS);
        assertEquals("Se te han cobrado {precio} por la imagen.", Messages.ECONOMY_CHARGE_SUCCESS);
        assertEquals("Ya no tienes suficiente dinero: la imagen no se entrego.", Messages.ECONOMY_FUNDS_LOST);
        assertEquals("No se pudo procesar el pago. Avisa al staff.", Messages.ECONOMY_PAYMENT_FAILED);
        assertEquals("No tienes suficiente dinero. Cuesta 10 $ y tienes 2 $.", Messages.insufficientFunds("10 $", "2 $"));
        assertEquals("Se te han cobrado 10 $ por la imagen.", Messages.chargeSuccess("10 $"));

        // Spec 003 Sección 9
        assertEquals("Ya tienes {usados} de {limite} imagenes. Borra una con /imagen borrar <id> o mira las tuyas con /imagen mias.", Messages.QUOTA_FULL_REQUEST);
        assertEquals("Esa imagen tiene {frames} frames y tu limite es {limite}. No se te cobro nada.", Messages.QUOTA_TOO_MANY_FRAMES);
        assertEquals("Llenaste tu cupo mientras se descargaba. No se creo la imagen y no se te cobro nada.", Messages.QUOTA_FULL_DELIVERY);
        assertEquals("No tienes ningun espacio de imagenes asignado. Pideselo a un administrador.", Messages.QUOTA_DEFAULT_ZERO);
        assertEquals("No tienes ninguna imagen.", Messages.QUOTA_MY_LIST_EMPTY);
        assertEquals("Vas a borrar la imagen {id}. Los mapas que la usen se quedaran en blanco. Repite el comando con \"confirmar\" al final.", Messages.QUOTA_DELETE_CONFIRM);
        assertEquals("Vas a soltar la imagen {id}. Otros jugadores la tienen, asi que sus mapas seguiran funcionando. Repite el comando con \"confirmar\" al final.", Messages.QUOTA_DELETE_CONFIRM_SHARED);
        assertEquals("Imagen borrada. Te queda {usados} de {limite} en uso.", Messages.QUOTA_DELETED);
        assertEquals("No existe ninguna imagen con ese id.", Messages.QUOTA_ID_NOT_FOUND);
        assertEquals("Esa imagen no es tuya.", Messages.QUOTA_ID_NOT_OWNED);

        assertEquals("Ya tienes 3 de 3 imagenes. Borra una con /imagen borrar <id> o mira las tuyas con /imagen mias.", Messages.quotaFullRequest(3, 3));
        assertEquals("Esa imagen tiene 50 frames y tu limite es 25. No se te cobro nada.", Messages.quotaTooManyFrames(50, 25));
        assertEquals("Vas a borrar la imagen 5. Los mapas que la usen se quedaran en blanco. Repite el comando con \"confirmar\" al final.", Messages.quotaDeleteConfirm(5));
        assertEquals("Vas a soltar la imagen 5. Otros jugadores la tienen, asi que sus mapas seguiran funcionando. Repite el comando con \"confirmar\" al final.", Messages.quotaDeleteConfirmShared(5));
        assertEquals("Imagen borrada. Te queda 2 de 3 en uso.", Messages.quotaDeleted(5, 2, 3));
        assertEquals("No existe ninguna imagen con ese id.", Messages.quotaIdNotFound(99));
        assertEquals("Esa imagen no es tuya.", Messages.quotaIdNotOwned(99));
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
              guardar-en-progreso: "Guardado en curso."
            exito:
              estatico: "Completado estatico."
              animado: "Completado animado ({frames})."
              error-guardar: "Fallo al guardar; sin cobro."
              entrega-recuperada: "Entrega restaurada."
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
            economia:
              saldo-insuficiente: "Falta dinero: vale {precio}, posees {saldo}."
              cobro-correcto: "Cobrado: {precio}."
              saldo-perdido: "Sin saldo final."
              fallo-pago: "Error cobro."
            cupos:
              cupo-lleno-al-pedir: "Custom lleno {usados}/{limite}."
              cupo-demasiados-frames: "Custom frames {frames}/{limite}."
              cupo-lleno-al-entregar: "Custom lleno al entregar."
              cupo-cero: "Custom cupo cero."
              mias-vacia: "Custom vacia."
              borrar-confirmacion: "Custom confirmacion {id}."
              borrar-confirmacion-compartida: "Custom compartida {id}."
              borrada: "Custom borrada {id}."
              no-existe: "Custom no existe {id}."
              no-es-tuya: "Custom no tuya {id}."
              mias-encabezado: "Mias {pagina}/{total_paginas} {usados}/{limite}."
              mias-item: "{id} {tipo} {fecha} {url}."
              mias-pie: "Siguiente {pagina}."
              mias-pagina-invalida: "Pagina invalida {total_pages}."
              borrar-detalles: "Detalles {tipo} {frames} {fecha} {compartida} {url}."
            actualizacion:
              comprobando: "Check release."
              descargando: "Preparando release."
              actualizado: "Current {version}."
              local-mas-nueva: "Local {current} > latest {latest}."
              disponible: "Available {version}."
              preparada: "Staged {version}."
              error: "Update error {error}."
            almacenamiento:
              error: "Storage error."
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

        assertEquals("Falta dinero: vale {precio}, posees {saldo}.", Messages.ECONOMY_INSUFFICIENT_FUNDS);
        assertEquals("Cobrado: {precio}.", Messages.ECONOMY_CHARGE_SUCCESS);
        assertEquals("Sin saldo final.", Messages.ECONOMY_FUNDS_LOST);
        assertEquals("Error cobro.", Messages.ECONOMY_PAYMENT_FAILED);

        assertEquals("Custom lleno {usados}/{limite}.", Messages.QUOTA_FULL_REQUEST);
        assertEquals("Custom frames {frames}/{limite}.", Messages.QUOTA_TOO_MANY_FRAMES);
        assertEquals("Custom lleno al entregar.", Messages.QUOTA_FULL_DELIVERY);
        assertEquals("Custom cupo cero.", Messages.QUOTA_DEFAULT_ZERO);
        assertEquals("Custom vacia.", Messages.QUOTA_MY_LIST_EMPTY);
        assertEquals("Custom confirmacion {id}.", Messages.QUOTA_DELETE_CONFIRM);
        assertEquals("Custom compartida {id}.", Messages.QUOTA_DELETE_CONFIRM_SHARED);
        assertEquals("Custom borrada {id}.", Messages.QUOTA_DELETED);
        assertEquals("Custom no existe {id}.", Messages.QUOTA_ID_NOT_FOUND);
        assertEquals("Custom no tuya {id}.", Messages.QUOTA_ID_NOT_OWNED);

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
