package com.dasannn.framcit.net;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Guardian central de seguridad para URLs segun Constitution 6 y Spec 6.
 * Es el unico componente que decide si una URL se toca o no.
 *
 * Protecciones implementadas:
 * - Acepta unicamente esquemas http y https.
 * - Resuelve el host a IP y rechaza loopback, link-local, privadas y cualquier
 *   destino no publico, tanto en IPv4 como en IPv6.
 * - Valida contra todas las IPs resueltas, no contra el texto del host.
 */
public class UrlGuard {

    @FunctionalInterface
    public interface DnsResolver {
        InetAddress[] resolve(String host) throws UnknownHostException;
    }

    private final boolean allowLoopback;
    private final DnsResolver dnsResolver;

    /**
     * Constructor estricto de produccion (loopback bloqueado).
     */
    public UrlGuard() {
        this(false, InetAddress::getAllByName);
    }

    /**
     * Constructor que permite configurar el permiso de loopback (usado en tests).
     *
     * @param allowLoopback true solo en tests para permitir conectar a servidores HTTP locales.
     */
    public UrlGuard(boolean allowLoopback) {
        this(allowLoopback, InetAddress::getAllByName);
    }

    /**
     * Constructor con inyeccion de DNS y loopback (usado en tests unitarios deterministas).
     */
    public UrlGuard(boolean allowLoopback, DnsResolver dnsResolver) {
        this.allowLoopback = allowLoopback;
        this.dnsResolver = Objects.requireNonNull(dnsResolver, "dnsResolver cannot be null");
    }

    /**
     * Valida una URL en formato texto.
     *
     * @param rawUrl la cadena de texto de la URL.
     * @return Optional vacio si la URL es segura y valida, o Optional con el DownloadError si se rechaza.
     */
    public Optional<DownloadError> validate(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return Optional.of(new DownloadError.MalformedUrl(rawUrl));
        }

        URI uri;
        try {
            uri = new URI(rawUrl.trim());
        } catch (URISyntaxException e) {
            return Optional.of(new DownloadError.MalformedUrl(rawUrl));
        }

        return validate(uri);
    }

    /**
     * Valida un objeto URI.
     *
     * @param uri el URI a comprobar.
     * @return Optional vacio si la URL es segura y valida, o Optional con el DownloadError si se rechaza.
     */
    public Optional<DownloadError> validate(URI uri) {
        if (uri == null) {
            return Optional.of(new DownloadError.MalformedUrl(null));
        }

        // 1. Validar esquema: solo http y https
        String scheme = uri.getScheme();
        if (scheme == null) {
            return Optional.of(new DownloadError.MalformedUrl(uri.toString()));
        }

        scheme = scheme.toLowerCase(Locale.ROOT);
        if (!scheme.equals("http") && !scheme.equals("https")) {
            return Optional.of(new DownloadError.DisallowedScheme(scheme));
        }

        // 2. Validar host
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            return Optional.of(new DownloadError.MalformedUrl(uri.toString()));
        }

        String cleanHost = host;
        if (cleanHost.startsWith("[") && cleanHost.endsWith("]")) {
            cleanHost = cleanHost.substring(1, cleanHost.length() - 1);
        }
        cleanHost = cleanHost.replaceAll("\\.+$", "");

        if (cleanHost.isBlank()) {
            return Optional.of(new DownloadError.MalformedUrl(uri.toString()));
        }

        // 3. Resolver DNS y validar IPs resueltas
        InetAddress[] addresses;
        try {
            addresses = dnsResolver.resolve(cleanHost);
        } catch (UnknownHostException | SecurityException e) {
            return Optional.of(new DownloadError.UnresolvableHost(host));
        }

        if (addresses == null || addresses.length == 0) {
            return Optional.of(new DownloadError.UnresolvableHost(host));
        }

        // Si CUALQUIER IP resuelta no es publica, se rechaza inmediatamente
        for (InetAddress addr : addresses) {
            if (allowLoopback && isLoopbackAddress(addr)) {
                continue;
            }
            if (!isPublic(addr)) {
                return Optional.of(new DownloadError.BlockedDestination(host));
            }
        }

        return Optional.empty();
    }

    /**
     * Determina si la URL es permitida.
     */
    public boolean isAllowed(String rawUrl) {
        return validate(rawUrl).isEmpty();
    }

    /**
     * Determina si la URI es permitida.
     */
    public boolean isAllowed(URI uri) {
        return validate(uri).isEmpty();
    }

    /**
     * Comprueba si una direccion IP es de loopback (IPv4 o IPv6, incluyendo formatos compatibles/mapeados).
     */
    public static boolean isLoopbackAddress(InetAddress addr) {
        if (addr == null) {
            return false;
        }
        if (addr.isLoopbackAddress()) {
            return true;
        }

        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            return (raw[0] & 0xFF) == 127;
        } else if (raw.length == 16) {
            // ::1
            boolean allZerosExceptLast = true;
            for (int i = 0; i < 15; i++) {
                if (raw[i] != 0) {
                    allZerosExceptLast = false;
                    break;
                }
            }
            if (allZerosExceptLast && raw[15] == 1) {
                return true;
            }

            // IPv4-mapped o IPv4-compatible
            if (isIpv4Mapped(raw) || isIpv4Compatible(raw)) {
                return (raw[12] & 0xFF) == 127;
            }
        }

        return false;
    }

    /**
     * Verifica si una direccion IP es de acceso publico y no pertenece a
     * loopback, link-local, privada o reservada (IPv4 e IPv6).
     */
    public static boolean isPublic(InetAddress addr) {
        if (addr == null) {
            return false;
        }

        if (addr.isAnyLocalAddress() || addr.isLoopbackAddress() ||
            addr.isLinkLocalAddress() || addr.isSiteLocalAddress() ||
            addr.isMulticastAddress()) {
            return false;
        }

        byte[] raw = addr.getAddress();
        if (raw.length == 4) {
            return isPublicIpv4(raw);
        } else if (raw.length == 16) {
            return isPublicIpv6(raw);
        }

        return false;
    }

    private static boolean isPublicIpv4(byte[] raw) {
        int b0 = raw[0] & 0xFF;
        int b1 = raw[1] & 0xFF;
        int b2 = raw[2] & 0xFF;

        // 0.0.0.0/8 - Red actual (RFC 1122)
        if (b0 == 0) return false;

        // 10.0.0.0/8 - Red privada (RFC 1918)
        if (b0 == 10) return false;

        // 100.64.0.0/10 - Espacio compartido / CGNAT (RFC 6598: 100.64.0.0 - 100.127.255.255)
        if (b0 == 100 && (b1 >= 64 && b1 <= 127)) return false;

        // 127.0.0.0/8 - Loopback (RFC 1122)
        if (b0 == 127) return false;

        // 169.254.0.0/16 - Link-Local (RFC 3927)
        if (b0 == 169 && b1 == 254) return false;

        // 172.16.0.0/12 - Red privada (RFC 1918: 172.16.0.0 - 172.31.255.255)
        if (b0 == 172 && (b1 >= 16 && b1 <= 31)) return false;

        // 192.0.0.0/24 - Asignaciones de protocolo IETF (RFC 6890)
        if (b0 == 192 && b1 == 0 && b2 == 0) return false;

        // 192.0.2.0/24 - TEST-NET-1 (RFC 5737)
        if (b0 == 192 && b1 == 0 && b2 == 2) return false;

        // 192.88.99.0/24 - 6to4 Anycast (RFC 7526)
        if (b0 == 192 && b1 == 88 && b2 == 99) return false;

        // 192.168.0.0/16 - Red privada (RFC 1918)
        if (b0 == 192 && b1 == 168) return false;

        // 198.18.0.0/15 - Benchmarking (RFC 2544: 198.18.0.0 - 198.19.255.255)
        if (b0 == 198 && (b1 == 18 || b1 == 19)) return false;

        // 198.51.100.0/24 - TEST-NET-2 (RFC 5737)
        if (b0 == 198 && b1 == 51 && b2 == 100) return false;

        // 203.0.113.0/24 - TEST-NET-3 (RFC 5737)
        if (b0 == 203 && b1 == 0 && b2 == 113) return false;

        // 224.0.0.0/4 - Multicast (RFC 5771: 224.0.0.0 - 239.255.255.255)
        if (b0 >= 224 && b0 <= 239) return false;

        // 240.0.0.0/4 - Reservado / Clase E / Broadcast (RFC 1112, RFC 919)
        if (b0 >= 240) return false;

        return true;
    }

    private static boolean isPublicIpv6(byte[] raw) {
        // :: (unspecified) o ::1 (loopback)
        boolean allZerosUntil15 = true;
        for (int i = 0; i < 15; i++) {
            if (raw[i] != 0) {
                allZerosUntil15 = false;
                break;
            }
        }
        if (allZerosUntil15) {
            if (raw[15] == 0 || raw[15] == 1) {
                return false;
            }
        }

        // IPv4-compatible (::0:w.x.y.z)
        if (isIpv4Compatible(raw)) {
            byte[] ipv4 = new byte[] { raw[12], raw[13], raw[14], raw[15] };
            return isPublicIpv4(ipv4);
        }

        // IPv4-mapped (::ffff:w.x.y.z)
        if (isIpv4Mapped(raw)) {
            byte[] ipv4 = new byte[] { raw[12], raw[13], raw[14], raw[15] };
            return isPublicIpv4(ipv4);
        }

        // IPv4 translation (64:ff9b::/96, RFC 6052)
        if ((raw[0] & 0xFF) == 0x00 && (raw[1] & 0xFF) == 0x64 &&
            (raw[2] & 0xFF) == 0xFF && (raw[3] & 0xFF) == 0x9B) {
            boolean middleZeros = true;
            for (int i = 4; i < 12; i++) {
                if (raw[i] != 0) {
                    middleZeros = false;
                    break;
                }
            }
            if (middleZeros) {
                byte[] ipv4 = new byte[] { raw[12], raw[13], raw[14], raw[15] };
                return isPublicIpv4(ipv4);
            }
        }

        int b0 = raw[0] & 0xFF;
        int b1 = raw[1] & 0xFF;

        // Discard-only prefix (100::/64, RFC 6666)
        if (b0 == 0x01 && b1 == 0x00) {
            boolean zeros = true;
            for (int i = 2; i < 8; i++) {
                if (raw[i] != 0) {
                    zeros = false;
                    break;
                }
            }
            if (zeros) return false;
        }

        // Documentacion (2001:db8::/32, RFC 3849)
        if (b0 == 0x20 && b1 == 0x01 && (raw[2] & 0xFF) == 0x0D && (raw[3] & 0xFF) == 0xB8) {
            return false;
        }

        // Unique Local Address ULA (fc00::/7, RFC 4193: fc00:: y fd00::)
        if ((b0 & 0xFE) == 0xFC) {
            return false;
        }

        // Link-Local unicast (fe80::/10, RFC 4291)
        if (b0 == 0xFE && (b1 & 0xC0) == 0x80) {
            return false;
        }

        // Deprecated Site-Local unicast (fec0::/10, RFC 3879)
        if (b0 == 0xFE && (b1 & 0xC0) == 0xC0) {
            return false;
        }

        // Multicast (ff00::/8, RFC 4291)
        if (b0 == 0xFF) {
            return false;
        }

        return true;
    }

    private static boolean isIpv4Compatible(byte[] raw) {
        for (int i = 0; i < 12; i++) {
            if (raw[i] != 0) return false;
        }
        // Excluir :: y ::1 ya chequeados
        return !(raw[12] == 0 && raw[13] == 0 && raw[14] == 0 && (raw[15] == 0 || raw[15] == 1));
    }

    private static boolean isIpv4Mapped(byte[] raw) {
        for (int i = 0; i < 10; i++) {
            if (raw[i] != 0) return false;
        }
        return (raw[10] & 0xFF) == 0xFF && (raw[11] & 0xFF) == 0xFF;
    }
}
