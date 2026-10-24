package com.dasannn.framcit.store;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gestor del archivo index.json (F5.3, F5.8, F5.11).
 * Formato legible con pretty printing (Constitution 5.4).
 * Manejo de corrupción con respaldo a .bak y arranque vacío sin abortar (F5.8).
 * Escritura atómica a disco (F5.2).
 */
public class IndexManager {

    private final Path indexPath;
    private final Path bakPath;
    private final Logger logger;
    private final Gson gson;

    private final Map<Integer, ImageIndexEntry> entriesByMapId = new ConcurrentHashMap<>();
    private final Map<String, ImageIndexEntry> entriesByNormalizedUrl = new ConcurrentHashMap<>();

    public IndexManager(Path dataFolder, Logger logger) {
        Objects.requireNonNull(dataFolder, "dataFolder cannot be null");
        this.indexPath = dataFolder.resolve("index.json");
        this.bakPath = dataFolder.resolve("index.json.bak");
        this.logger = logger;
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    /**
     * Carga el índice desde index.json.
     * Si el archivo no existe, arranca vacío.
     * Si está corrupto, lo renombra a index.json.bak, registra el aviso y arranca vacío (F5.8).
     */
    public synchronized void load() {
        entriesByMapId.clear();
        entriesByNormalizedUrl.clear();

        if (!Files.exists(indexPath)) {
            return;
        }

        try {
            String json = Files.readString(indexPath, StandardCharsets.UTF_8);
            IndexData data = gson.fromJson(json, IndexData.class);
            if (data == null || data.getEntries() == null) {
                handleCorruptedIndex("Contenido nulo o sin lista de entradas");
                return;
            }

            boolean needsSave = false;
            for (ImageIndexEntry entry : data.getEntries()) {
                if (entry != null) {
                    // F11.6: Migración al cargar: una entrada sin lista de poseedores estrena una con su autor dentro.
                    // Autor desconocido (authorUuid vacío o nulo): sin poseedores, y NO se borra.
                    if (entry.getRawOwners() == null) {
                        entry.setOwners(new ArrayList<>());
                        String authorUuid = entry.getAuthorUuid();
                        if (authorUuid != null && !authorUuid.isBlank()) {
                            entry.addOwner(authorUuid);
                        }
                        needsSave = true;
                    }
                    entriesByMapId.put(entry.getMapId(), entry);
                    if (entry.getNormalizedUrl() != null) {
                        entriesByNormalizedUrl.put(entry.getNormalizedUrl(), entry);
                    }
                }
            }
            if (needsSave) {
                try {
                    save();
                } catch (IOException e) {
                    if (logger != null) {
                        logger.warning("Error al guardar index.json tras migración de poseedores: " + e.getMessage());
                    }
                }
            }
        } catch (Throwable t) {
            handleCorruptedIndex(t.getMessage());
        }
    }

    private void handleCorruptedIndex(String reason) {
        if (logger != null) {
            logger.warning("index.json corrupto o ilegible (" + reason + "). Renombrando a index.json.bak y arrancando vacío.");
        }
        try {
            try {
                Files.move(indexPath, bakPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(indexPath, bakPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            if (logger != null) {
                logger.log(Level.WARNING, "No se pudo renombrar index.json a .bak: " + e.getMessage(), e);
            }
        }
        entriesByMapId.clear();
        entriesByNormalizedUrl.clear();
    }

    /**
     * Guarda el estado actual en index.json de forma atómica (F5.2, F5.11).
     */
    public synchronized void save() throws IOException {
        saveJson(snapshotJson());
    }

    /**
     * Crea una instantánea aislada del índice. Puede serializarse antes de encolar el I/O
     * para evitar que cambios posteriores de owners muten el JSON que se está escribiendo.
     */
    public synchronized String snapshotJson() {
        return snapshotJson(Set.of());
    }

    /** Snapshot that omits pending image entries which are not durable yet. */
    public synchronized String snapshotJson(Set<Integer> excludedMapIds) {
        List<ImageIndexEntry> snapshot = new ArrayList<>(entriesByMapId.size());
        for (ImageIndexEntry entry : entriesByMapId.values()) {
            if (excludedMapIds != null && excludedMapIds.contains(entry.getMapId())) {
                continue;
            }
            snapshot.add(entry.snapshotCopy());
        }
        return gson.toJson(new IndexData(1, snapshot));
    }

    /** Guarda una instantánea JSON previamente serializada mediante reemplazo atómico. */
    public synchronized void saveJson(String json) throws IOException {
        Path parent = indexPath.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        String tmpName = "index.json." + UUID.randomUUID() + ".tmp";
        Path tempPath = (parent != null) ? parent.resolve(tmpName) : Path.of(tmpName);

        try {
            Files.writeString(tempPath, json, StandardCharsets.UTF_8);
            try {
                Files.move(tempPath, indexPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tempPath, indexPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ignored) {}
        }
    }

    public synchronized void addEntry(ImageIndexEntry entry) {
        Objects.requireNonNull(entry, "entry cannot be null");
        entriesByMapId.put(entry.getMapId(), entry);
        if (entry.getNormalizedUrl() != null) {
            entriesByNormalizedUrl.put(entry.getNormalizedUrl(), entry);
        }
    }

    public synchronized ImageIndexEntry removeEntry(int mapId) {
        ImageIndexEntry removed = entriesByMapId.remove(mapId);
        if (removed != null && removed.getNormalizedUrl() != null) {
            entriesByNormalizedUrl.remove(removed.getNormalizedUrl());
        }
        return removed;
    }

    public ImageIndexEntry getByMapId(int mapId) {
        return entriesByMapId.get(mapId);
    }

    public ImageIndexEntry getByNormalizedUrl(String normalizedUrl) {
        if (normalizedUrl == null) return null;
        return entriesByNormalizedUrl.get(normalizedUrl);
    }

    public Collection<ImageIndexEntry> getAllEntries() {
        return List.copyOf(entriesByMapId.values());
    }

    public int size() {
        return entriesByMapId.size();
    }

    public Path getIndexPath() {
        return indexPath;
    }

    public Path getBakPath() {
        return bakPath;
    }
}
