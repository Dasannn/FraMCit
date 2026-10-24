package com.dasannn.framcit.store;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class IndexManagerTest {

    @TempDir
    Path tempDir;

    private static final Logger LOGGER = Logger.getLogger(IndexManagerTest.class.getName());

    @Test
    @DisplayName("F5.8 - index.json corrupto se renombra a .bak y se arranca vacío sin abortar")
    void testCorruptedIndexRenamedToBakAndStartsEmpty() throws IOException {
        Path indexPath = tempDir.resolve("index.json");
        Path bakPath = tempDir.resolve("index.json.bak");

        // Escribir JSON corrupto
        String corruptJson = "{ \"version\": 1, \"entries\": [ { \"mapId\": 1, \"corrupt\": ";
        Files.writeString(indexPath, corruptJson, StandardCharsets.UTF_8);

        IndexManager manager = new IndexManager(tempDir, LOGGER);
        manager.load();

        // Debe arrancar vacío
        assertEquals(0, manager.size(), "El índice debe arrancar vacío tras encontrar corrupción");
        assertTrue(manager.getAllEntries().isEmpty());

        // index.json original debe haberse renombrado a index.json.bak
        assertTrue(Files.exists(bakPath), "El archivo index.json.bak debe existir");
        assertEquals(corruptJson, Files.readString(bakPath, StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("F5.3 & F5.11 - Guardar y recargar index.json mantiene todos los campos intactos")
    void testSaveAndLoadRoundtrip() throws IOException {
        IndexManager manager = new IndexManager(tempDir, LOGGER);

        ImageIndexEntry entry1 = new ImageIndexEntry(
            10,
            "https://example.com/pic.png",
            "https://example.com/pic.png",
            "Alice",
            "00000000-0000-0000-0000-000000000001",
            "2026-09-10",
            "PNG",
            1,
            List.of(0),
            1,
            false
        );
        String pendingOwner = "00000000-0000-0000-0000-000000000001";
        entry1.addPendingDelivery(pendingOwner);
        String deliveryToken = entry1.getPendingDeliveryToken(pendingOwner);

        ImageIndexEntry entry2 = new ImageIndexEntry(
            11,
            "https://example.com/anim.gif",
            "https://example.com/anim.gif",
            "Bob",
            "00000000-0000-0000-0000-000000000002",
            "2026-09-10",
            "GIF",
            3,
            List.of(100, 200, 300),
            1,
            true
        );

        manager.addEntry(entry1);
        manager.addEntry(entry2);
        manager.save();

        Path indexPath = tempDir.resolve("index.json");
        assertTrue(Files.exists(indexPath));

        // Verificar que el formato en disco es texto legible (Constitution 5.4)
        String jsonText = Files.readString(indexPath, StandardCharsets.UTF_8);
        assertTrue(jsonText.contains("Alice"));
        assertTrue(jsonText.contains("https://example.com/pic.png"));
        assertTrue(jsonText.contains("Bob"));

        // Recargar con un nuevo IndexManager
        IndexManager reloaded = new IndexManager(tempDir, LOGGER);
        reloaded.load();

        assertEquals(2, reloaded.size());

        ImageIndexEntry reloaded1 = reloaded.getByMapId(10);
        assertNotNull(reloaded1);
        assertEquals("https://example.com/pic.png", reloaded1.getOriginalUrl());
        assertEquals("https://example.com/pic.png", reloaded1.getNormalizedUrl());
        assertEquals("Alice", reloaded1.getAuthorName());
        assertEquals(1, reloaded1.getFrameCount());
        assertFalse(reloaded1.isOrphan());
        assertEquals(List.of("00000000-0000-0000-0000-000000000001"), reloaded1.getPendingDeliveryOwners());
        assertEquals(deliveryToken, reloaded1.getPendingDeliveryToken(pendingOwner));

        ImageIndexEntry reloaded2 = reloaded.getByMapId(11);
        assertNotNull(reloaded2);
        assertEquals("Bob", reloaded2.getAuthorName());
        assertEquals(3, reloaded2.getFrameCount());
        assertEquals(List.of(100, 200, 300), reloaded2.getDelays());
        assertTrue(reloaded2.isOrphan());

        // Búsqueda por URL normalizada
        assertNotNull(reloaded.getByNormalizedUrl("https://example.com/pic.png"));
        assertNotNull(reloaded.getByNormalizedUrl("https://example.com/anim.gif"));
    }

    @Test
    @DisplayName("F5.3 - Archivo ausente arranca con índice vacío sin errores")
    void testNonExistentIndexStartsEmpty() {
        IndexManager manager = new IndexManager(tempDir, LOGGER);
        assertDoesNotThrow(manager::load);
        assertEquals(0, manager.size());
    }

    @Test
    @DisplayName("F11.6 - Migración de index.json legacy: entradas sin campo owners se asignan a su autor")
    void testLegacyIndexMigrationAddsAuthorUuidToOwners() throws IOException {
        Path indexPath = tempDir.resolve("index.json");

        // index.json pre-F11 sin campo "owners"
        String legacyJson = """
            {
              "version": 1,
              "entries": [
                {
                  "mapId": 1,
                  "originalUrl": "https://example.com/alice.png",
                  "normalizedUrl": "https://example.com/alice.png",
                  "authorName": "Alice",
                  "authorUuid": "11111111-1111-1111-1111-111111111111",
                  "createdAt": "2026-09-01",
                  "format": "PNG",
                  "frameCount": 1,
                  "delays": [0],
                  "paletteVersion": 1,
                  "orphan": false
                },
                {
                  "mapId": 2,
                  "originalUrl": "https://example.com/unknown.png",
                  "normalizedUrl": "https://example.com/unknown.png",
                  "authorName": "Desconocido",
                  "authorUuid": null,
                  "createdAt": "2026-09-01",
                  "format": "PNG",
                  "frameCount": 1,
                  "delays": [0],
                  "paletteVersion": 1,
                  "orphan": false
                }
              ]
            }
            """;
        Files.writeString(indexPath, legacyJson, StandardCharsets.UTF_8);

        IndexManager manager = new IndexManager(tempDir, LOGGER);
        manager.load();

        assertEquals(2, manager.size());

        ImageIndexEntry entry1 = manager.getByMapId(1);
        assertNotNull(entry1);
        assertNotNull(entry1.getOwners());
        assertEquals(List.of("11111111-1111-1111-1111-111111111111"), entry1.getOwners());
        assertTrue(entry1.hasOwner("11111111-1111-1111-1111-111111111111"));

        ImageIndexEntry entry2 = manager.getByMapId(2);
        assertNotNull(entry2);
        assertNotNull(entry2.getOwners());
        assertTrue(entry2.getOwners().isEmpty(), "Autor desconocido queda con lista de owners vacía sin borrar la entrada");

        // El archivo migrado se guardó con el nuevo campo
        String savedJson = Files.readString(indexPath, StandardCharsets.UTF_8);
        assertTrue(savedJson.contains("owners"), "El index.json migrado debe persistir el campo owners");
        assertTrue(savedJson.contains("11111111-1111-1111-1111-111111111111"));
    }
}
