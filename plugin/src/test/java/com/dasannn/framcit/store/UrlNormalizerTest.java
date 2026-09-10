package com.dasannn.framcit.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class UrlNormalizerTest {

    @Test
    @DisplayName("F5.4 - Mayúsculas en el host se convierten a minúsculas")
    void testHostLowercasing() {
        assertEquals("https://example.com/image.png", UrlNormalizer.normalize("https://EXAMPLE.COM/image.png"));
        assertEquals("https://cdn.photos.site.org/pic", UrlNormalizer.normalize("HTTPS://CDN.Photos.Site.ORG/pic"));
    }

    @Test
    @DisplayName("F5.4 - Barra final se elimina en rutas y raíz")
    void testTrailingSlashRemoval() {
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com/"));
        assertEquals("https://example.com", UrlNormalizer.normalize("https://example.com"));
        assertEquals("https://example.com/path", UrlNormalizer.normalize("https://example.com/path/"));
        assertEquals("https://example.com/path", UrlNormalizer.normalize("https://example.com/path///"));
    }

    @Test
    @DisplayName("F5.4 - Dos URLs equivalentes producen exactamente la misma clave de cache")
    void testEquivalentUrlsProduceIdenticalKey() {
        String url1 = "https://EXAMPLE.COM/images/sample.png/";
        String url2 = "https://example.com/images/sample.png";
        String url3 = "HTTPS://Example.Com:443/images/sample.png/";

        String key1 = UrlNormalizer.normalize(url1);
        String key2 = UrlNormalizer.normalize(url2);
        String key3 = UrlNormalizer.normalize(url3);

        assertEquals(key1, key2);
        assertEquals(key2, key3);
        assertEquals("https://example.com/images/sample.png", key1);
    }

    @Test
    @DisplayName("F5.4 - Puertos por defecto (80 para http, 443 para https) se omiten, puertos no estándar se conservan")
    void testPortNormalization() {
        assertEquals("http://example.com/test", UrlNormalizer.normalize("http://EXAMPLE.COM:80/test/"));
        assertEquals("https://example.com/test", UrlNormalizer.normalize("https://example.com:443/test"));
        assertEquals("http://example.com:8080/test", UrlNormalizer.normalize("http://example.com:8080/test/"));
        assertEquals("https://example.com:8443/test", UrlNormalizer.normalize("https://example.com:8443/test/"));
    }

    @Test
    @DisplayName("F5.4 - Query parameters se conservan y fragmentos (#ancla) se eliminan")
    void testQueryAndFragment() {
        assertEquals(
            "https://example.com/api?id=123&type=thumb",
            UrlNormalizer.normalize("https://EXAMPLE.com/api/?id=123&type=thumb#section1")
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "ftp://example.com/file.png",
        "file:///etc/passwd",
        "jar:file:/plugin.jar!/",
        "javascript:alert(1)"
    })
    @DisplayName("F5.4 - Esquemas no permitidos lanzan IllegalArgumentException")
    void testDisallowedSchemes(String disallowedUrl) {
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize(disallowedUrl));
    }

    @Test
    @DisplayName("F5.4 - Entradas nulas, vacías o malformadas lanzan IllegalArgumentException")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize(null));
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize("   "));
        assertThrows(IllegalArgumentException.class, () -> UrlNormalizer.normalize("not a valid url ://"));
    }
}
