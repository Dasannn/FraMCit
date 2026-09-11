package com.dasannn.framcit.net;

import java.security.Security;
import java.util.logging.Logger;

/**
 * Verificador del TTL de la caché DNS de la JVM al arrancar el servidor (F7.10, SEC R6, Constitution 6.1).
 *
 * Contexto: UrlGuard valida la dirección IP resuelta antes de conectar, pero HttpClient
 * vuelve a resolver el nombre de host al establecer la conexión HTTP. Lo que impide
 * que un atacante desvíe esa segunda resolución hacia una IP interna (DNS Rebinding / SSRF)
 * es la caché DNS positiva de la JVM (30 segundos por defecto).
 *
 * Si la caché está desactivada (-Dsun.net.inetaddr.ttl=0 o networkaddress.cache.ttl=0),
 * esa protección desaparece por completo.
 */
public final class DnsCacheChecker {

    public static final String PROPERTY_NAME = "networkaddress.cache.ttl";
    public static final String LEGACY_PROPERTY_NAME = "sun.net.inetaddr.ttl";

    public static final String WARNING_MESSAGE =
        "[SEC R6] AVISO DE SEGURIDAD CRÍTICO: La caché de DNS de la JVM está desactivada (networkaddress.cache.ttl=0). " +
        "UrlGuard valida la IP resuelta inicialmente, pero HttpClient vuelve a resolver el nombre de host al conectar. " +
        "Sin caché DNS, un atacante que controle su propio servidor DNS puede desviar esa segunda resolución " +
        "hacia una IP interna de la red del servidor (DNS rebinding / SSRF). " +
        "Se recomienda NO arrancar la JVM con networkaddress.cache.ttl=0 o sun.net.inetaddr.ttl=0.";

    private DnsCacheChecker() {}

    /**
     * Lee la propiedad de seguridad de la JVM y avisa si el TTL efectivo es 0.
     * Si devuelve null, la JVM usa su valor seguro por defecto (30 segundos).
     *
     * @param logger Logger del plugin para emitir la advertencia si corresponde.
     * @return true si se detectó TTL=0 y se emitió aviso; false si el TTL es seguro o null.
     */
    public static boolean checkDnsCacheTtl(Logger logger) {
        // La propiedad de seguridad manda cuando esta definida. Si no lo esta, la JVM
        // todavia honra la propiedad de sistema heredada -Dsun.net.inetaddr.ttl, que es
        // justo la forma de desactivar la cache que advierte el mensaje. Sin mirar las dos,
        // arrancar con -Dsun.net.inetaddr.ttl=0 dejaria la cache desactivada en silencio.
        String ttlProperty = Security.getProperty(PROPERTY_NAME);
        if (ttlProperty == null || ttlProperty.isBlank()) {
            ttlProperty = System.getProperty(LEGACY_PROPERTY_NAME);
        }
        return checkDnsTtlValue(ttlProperty, logger);
    }

    /**
     * Evalúa el valor del TTL para pruebas unitarias deterministas.
     *
     * @param ttlProperty valor del TTL o null si no está configurado explícitamente.
     * @param logger Logger donde emitir la advertencia.
     * @return true si ttlProperty es "0", false en cualquier otro caso (incluido null).
     */
    public static boolean checkDnsTtlValue(String ttlProperty, Logger logger) {
        if (ttlProperty != null && "0".equals(ttlProperty.trim())) {
            if (logger != null) {
                logger.warning(WARNING_MESSAGE);
            }
            return true;
        }
        return false;
    }
}
