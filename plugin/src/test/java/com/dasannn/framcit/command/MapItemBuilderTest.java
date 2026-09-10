package com.dasannn.framcit.command;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MapItemBuilderTest {

    @Test
    @DisplayName("F4.4 - Deduccion del nombre del item a partir de la URL")
    void testResolveItemName() {
        // 1. Archivo directo con extension
        URI uri1 = URI.create("https://example.com/photos/cat.png");
        assertEquals("cat.png", MapItemBuilder.resolveItemName(uri1, "https://example.com/photos/cat.png"));

        // 2. Archivo con query params
        URI uri2 = URI.create("https://example.com/images/cat.jpg?width=100&height=200");
        assertEquals("cat.jpg", MapItemBuilder.resolveItemName(uri2, "https://example.com/images/cat.jpg?width=100&height=200"));

        // 3. Archivo con espacios codificados
        URI uri3 = URI.create("https://example.com/my%20cool%20picture.webp");
        assertEquals("my cool picture.webp", MapItemBuilder.resolveItemName(uri3, "https://example.com/my%20cool%20picture.webp"));

        // 4. URL raiz: deduce el dominio
        URI uri4 = URI.create("https://example.com/");
        assertEquals("example.com", MapItemBuilder.resolveItemName(uri4, "https://example.com/"));

        // 5. URL sin barra final: deduce el dominio
        URI uri5 = URI.create("https://sub.domain.org");
        assertEquals("sub.domain.org", MapItemBuilder.resolveItemName(uri5, "https://sub.domain.org"));

        // 6. Subdirectorio con barra final: deduce el dominio
        URI uri6 = URI.create("https://example.com/gallery/");
        assertEquals("example.com", MapItemBuilder.resolveItemName(uri6, "https://example.com/gallery/"));
    }

    @Test
    @DisplayName("F4.4 - Deduccion del dominio de origen para el lore")
    void testResolveDomain() {
        URI uri1 = URI.create("https://cdn.example.org/images/12345.png");
        assertEquals("cdn.example.org", MapItemBuilder.resolveDomain(uri1, uri1.toString()));

        URI uri2 = URI.create("http://localhost.server.net:8080/image.jpg");
        assertEquals("localhost.server.net", MapItemBuilder.resolveDomain(uri2, uri2.toString()));

        assertEquals("desconocido", MapItemBuilder.resolveDomain(null, "invalid-uri"));
    }
}
