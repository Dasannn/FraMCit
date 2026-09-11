package com.dasannn.framcit.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class DnsCacheCheckerTest {

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
    @DisplayName("F7.10 [SEC] - TTL = 0 emite aviso crítico de seguridad sobre DNS rebinding")
    void testTtlZeroEmitsWarning() {
        Logger logger = Logger.getLogger("TestLogger-TtlZero");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        boolean warned = DnsCacheChecker.checkDnsTtlValue("0", logger);

        assertTrue(warned, "Debe retornar true indicando que se emitió aviso");
        assertEquals(1, handler.records.size(), "Debe haberse registrado exactamente un aviso");
        String msg = handler.records.getFirst().getMessage();
        assertTrue(msg.contains("[SEC R6]"), "El mensaje debe identificar el riesgo R6");
        assertTrue(msg.contains("DNS"), "El mensaje debe explicar la vulnerabilidad DNS");
        assertTrue(msg.contains("UrlGuard"), "El mensaje debe mencionar UrlGuard");
    }

    @Test
    @DisplayName("F7.10 [SEC] - TTL = null no emite aviso (comportamiento seguro por defecto de la JVM: 30s)")
    void testTtlNullDoesNotWarn() {
        Logger logger = Logger.getLogger("TestLogger-TtlNull");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        boolean warned = DnsCacheChecker.checkDnsTtlValue(null, logger);

        assertFalse(warned, "No debe emitir aviso si el TTL es null");
        assertTrue(handler.records.isEmpty(), "No debe haber registros de aviso si es null");
    }

    @Test
    @DisplayName("F7.10 [SEC] - TTL positivo o -1 (infinito) no emite aviso")
    void testTtlPositiveOrNegativeOneDoesNotWarn() {
        Logger logger = Logger.getLogger("TestLogger-TtlSafe");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        assertFalse(DnsCacheChecker.checkDnsTtlValue("30", logger));
        assertFalse(DnsCacheChecker.checkDnsTtlValue("60", logger));
        assertFalse(DnsCacheChecker.checkDnsTtlValue("-1", logger));
        assertTrue(handler.records.isEmpty());
    }

    @Test
    @DisplayName("F7.10 [SEC] - checkDnsCacheTtl se ejecuta contra el entorno de la JVM sin lanzar excepciones")
    void testCheckDnsCacheTtlLiveExecution() {
        Logger logger = Logger.getLogger("TestLogger-Live");
        assertDoesNotThrow(() -> DnsCacheChecker.checkDnsCacheTtl(logger));
    }

    @Test
    @DisplayName("F7.10 [SEC] - la propiedad heredada sun.net.inetaddr.ttl=0 tambien emite aviso")
    void testLegacySystemPropertyAlsoWarns() {
        // Arrancar la JVM con -Dsun.net.inetaddr.ttl=0 desactiva la cache igual que la
        // propiedad de seguridad, pero deja Security.getProperty devolviendo null. Sin
        // mirar las dos, la cache quedaria desactivada sin que nadie avise.
        Logger logger = Logger.getLogger("TestLogger-Legacy");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        String previous = System.getProperty(DnsCacheChecker.LEGACY_PROPERTY_NAME);
        try {
            System.setProperty(DnsCacheChecker.LEGACY_PROPERTY_NAME, "0");
            boolean warned = DnsCacheChecker.checkDnsCacheTtl(logger);

            // Solo es exigible si la propiedad de seguridad no manda en este entorno.
            String securityTtl = java.security.Security.getProperty(DnsCacheChecker.PROPERTY_NAME);
            if (securityTtl == null || securityTtl.isBlank()) {
                assertTrue(warned, "sun.net.inetaddr.ttl=0 debe emitir aviso de R6");
                assertFalse(handler.records.isEmpty(), "Debe quedar registrado el aviso");
            }
        } finally {
            if (previous == null) {
                System.clearProperty(DnsCacheChecker.LEGACY_PROPERTY_NAME);
            } else {
                System.setProperty(DnsCacheChecker.LEGACY_PROPERTY_NAME, previous);
            }
        }
    }
}
