package com.dasannn.framcit.net;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UrlGuardTest {

    private final UrlGuard urlGuard = new UrlGuard();

    // =========================================================================
    // F2.8 [SEC] Test: 127.0.0.1, localhost, 10.0.0.1, 192.168.1.1, 172.16.0.1,
    // [::1], 169.254.x
    // =========================================================================

    @Test
    @DisplayName("F2.8 [SEC]: Rechaza 127.0.0.1 como destino loopback")
    void shouldRejectLoopbackIpv4() {
        Optional<DownloadError> result = urlGuard.validate("http://127.0.0.1/image.png");
        assertTrue(result.isPresent(), "Debe rechazar 127.0.0.1");
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.8 [SEC]: Rechaza localhost como destino loopback")
    void shouldRejectLocalhost() {
        Optional<DownloadError> result = urlGuard.validate("http://localhost/test.jpg");
        assertTrue(result.isPresent(), "Debe rechazar localhost");
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.8 [SEC]: Rechaza 10.0.0.1 como rango privado RFC 1918")
    void shouldRejectPrivate10Net() {
        Optional<DownloadError> result = urlGuard.validate("http://10.0.0.1/image.png");
        assertTrue(result.isPresent(), "Debe rechazar 10.0.0.1");
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.8 [SEC]: Rechaza 192.168.1.1 como rango privado RFC 1918")
    void shouldRejectPrivate192Net() {
        Optional<DownloadError> result = urlGuard.validate("http://192.168.1.1/image.png");
        assertTrue(result.isPresent(), "Debe rechazar 192.168.1.1");
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.8 [SEC]: Rechaza 172.16.0.1 como rango privado RFC 1918")
    void shouldRejectPrivate172Net() {
        Optional<DownloadError> result = urlGuard.validate("http://172.16.0.1/image.png");
        assertTrue(result.isPresent(), "Debe rechazar 172.16.0.1");
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.8 [SEC]: Rechaza [::1] como loopback IPv6")
    void shouldRejectLoopbackIpv6() {
        Optional<DownloadError> result = urlGuard.validate("http://[::1]/image.png");
        assertTrue(result.isPresent(), "Debe rechazar [::1]");
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "http://169.254.0.1/image.png",
        "http://169.254.1.1/image.png",
        "http://169.254.254.254/image.png"
    })
    @DisplayName("F2.8 [SEC]: Rechaza rango link-local 169.254.x.x")
    void shouldRejectLinkLocalIpv4(String url) {
        Optional<DownloadError> result = urlGuard.validate(url);
        assertTrue(result.isPresent(), "Debe rechazar link-local: " + url);
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
        assertEquals("URL no permitida.", result.get().userMessage());
    }

    // =========================================================================
    // F2.12 Test: Esquemas file:, ftp:, jar:, data: rechazados
    // =========================================================================

    @Test
    @DisplayName("F2.12: Rechaza esquema file:")
    void shouldRejectFileScheme() {
        Optional<DownloadError> result = urlGuard.validate("file:///etc/passwd");
        assertTrue(result.isPresent(), "Debe rechazar file:");
        assertInstanceOf(DownloadError.DisallowedScheme.class, result.get());
        assertEquals("Solo se aceptan enlaces http y https.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.12: Rechaza esquema ftp:")
    void shouldRejectFtpScheme() {
        Optional<DownloadError> result = urlGuard.validate("ftp://example.com/photo.png");
        assertTrue(result.isPresent(), "Debe rechazar ftp:");
        assertInstanceOf(DownloadError.DisallowedScheme.class, result.get());
        assertEquals("Solo se aceptan enlaces http y https.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.12: Rechaza esquema jar:")
    void shouldRejectJarScheme() {
        Optional<DownloadError> result = urlGuard.validate("jar:file:/tmp/app.jar!/icon.png");
        assertTrue(result.isPresent(), "Debe rechazar jar:");
        assertInstanceOf(DownloadError.DisallowedScheme.class, result.get());
        assertEquals("Solo se aceptan enlaces http y https.", result.get().userMessage());
    }

    @Test
    @DisplayName("F2.12: Rechaza esquema data:")
    void shouldRejectDataScheme() {
        Optional<DownloadError> result = urlGuard.validate("data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==");
        assertTrue(result.isPresent(), "Debe rechazar data:");
        assertInstanceOf(DownloadError.DisallowedScheme.class, result.get());
        assertEquals("Solo se aceptan enlaces http y https.", result.get().userMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "gopher://example.com/",
        "ldap://example.com/dc=example,dc=com",
        "mailto:user@example.com",
        "netdoc:///etc/hosts"
    })
    @DisplayName("F2.12: Rechaza cualquier otro esquema no http/https")
    void shouldRejectOtherSchemes(String url) {
        Optional<DownloadError> result = urlGuard.validate(url);
        assertTrue(result.isPresent(), "Debe rechazar esquema ajeno: " + url);
        assertInstanceOf(DownloadError.DisallowedScheme.class, result.get());
        assertEquals("Solo se aceptan enlaces http y https.", result.get().userMessage());
    }

    // =========================================================================
    // Pruebas adicionales de destinos internos IPv4 e IPv6
    // =========================================================================

    @Test
    @DisplayName("Rechaza 0.0.0.0 (red actual / alias de localhost en muchos sistemas)")
    void shouldRejectAnyLocalIpv4() {
        Optional<DownloadError> result = urlGuard.validate("http://0.0.0.0/image.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("Rechaza IPv6 ULA fc00::1 y fd00::1 (RFC 4193)")
    void shouldRejectIpv6UniqueLocalAddresses() {
        Optional<DownloadError> r1 = urlGuard.validate("http://[fc00::1]/img.png");
        assertTrue(r1.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, r1.get());

        Optional<DownloadError> r2 = urlGuard.validate("http://[fd12:3456:789a:1::1]/img.png");
        assertTrue(r2.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, r2.get());
    }

    @Test
    @DisplayName("Rechaza IPv6 link-local fe80::1 (RFC 4291)")
    void shouldRejectIpv6LinkLocal() {
        Optional<DownloadError> result = urlGuard.validate("http://[fe80::1]/img.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("Rechaza IPv4-mapped IPv6 a loopback (::ffff:127.0.0.1)")
    void shouldRejectIpv4MappedLoopback() {
        Optional<DownloadError> result = urlGuard.validate("http://[::ffff:127.0.0.1]/img.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("Rechaza IPv4-mapped IPv6 a red privada (::ffff:10.0.0.1)")
    void shouldRejectIpv4MappedPrivate() {
        Optional<DownloadError> result = urlGuard.validate("http://[::ffff:10.0.0.1]/img.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("Rechaza CGNAT 100.64.0.1 (RFC 6598)")
    void shouldRejectCgnatAddress() {
        Optional<DownloadError> result = urlGuard.validate("http://100.64.0.1/img.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("Rechaza multicast 224.0.0.1 y ff02::1")
    void shouldRejectMulticast() {
        Optional<DownloadError> r1 = urlGuard.validate("http://224.0.0.1/img.png");
        assertTrue(r1.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, r1.get());

        Optional<DownloadError> r2 = urlGuard.validate("http://[ff02::1]/img.png");
        assertTrue(r2.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, r2.get());
    }

    // =========================================================================
    // Resolucion DNS y validacion contra la IP RESUELTA
    // =========================================================================

    @Test
    @DisplayName("F2.2: Valida contra la IP RESUELTA aunque el host parezca inocuo")
    void shouldRejectWhenDnsResolvesToPrivateIp() {
        // Simulamos un dominio aparentemente publico que resuelve a 10.0.0.5
        UrlGuard customGuard = new UrlGuard(false, host -> new InetAddress[] {
            InetAddress.getByName("10.0.0.5")
        });

        Optional<DownloadError> result = customGuard.validate("http://innocent-looking-site.com/image.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("F2.2: Rechaza si el DNS devuelve una IP publica y una IP privada (multi-A record)")
    void shouldRejectIfAnyResolvedIpIsPrivate() {
        UrlGuard customGuard = new UrlGuard(false, host -> new InetAddress[] {
            InetAddress.getByName("93.184.216.34"), // publica
            InetAddress.getByName("127.0.0.1")     // privada/loopback
        });

        Optional<DownloadError> result = customGuard.validate("http://dual-record-site.com/image.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.BlockedDestination.class, result.get());
    }

    @Test
    @DisplayName("Acepta destino si todas las IPs resueltas son publicas")
    void shouldAcceptPublicIp() {
        UrlGuard customGuard = new UrlGuard(false, host -> new InetAddress[] {
            InetAddress.getByName("93.184.216.34")
        });

        Optional<DownloadError> result = customGuard.validate("https://example.com/cat.png");
        assertTrue(result.isEmpty(), "Una IP publica legitima debe ser aceptada");
    }

    @Test
    @DisplayName("Retorna UnresolvableHost cuando el DNS no puede resolver el host")
    void shouldReturnUnresolvableHostOnDnsFailure() {
        UrlGuard customGuard = new UrlGuard(false, host -> {
            throw new UnknownHostException("Host desconocido: " + host);
        });

        Optional<DownloadError> result = customGuard.validate("http://nonexistent-domain-12345.xyz/image.png");
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.UnresolvableHost.class, result.get());
        assertEquals("No se pudo conectar con ese sitio.", result.get().userMessage());
    }

    // =========================================================================
    // URLs malformadas
    // =========================================================================

    @ParameterizedTest
    @ValueSource(strings = {
        "",
        "   ",
        "not-a-url",
        "http://",
        "http:///image.png",
        "https://"
    })
    @DisplayName("Rechaza URLs con sintaxis malformada")
    void shouldRejectMalformedUrls(String url) {
        Optional<DownloadError> result = urlGuard.validate(url);
        assertTrue(result.isPresent());
        assertInstanceOf(DownloadError.MalformedUrl.class, result.get());
        assertEquals("Esa URL no es valida.", result.get().userMessage());
    }
}
