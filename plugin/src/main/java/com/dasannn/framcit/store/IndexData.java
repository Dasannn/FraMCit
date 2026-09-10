package com.dasannn.framcit.store;

import java.util.ArrayList;
import java.util.List;

/**
 * Contenedor raíz de index.json (F5.3).
 */
public class IndexData {

    private int version = 1;
    private List<ImageIndexEntry> entries = new ArrayList<>();

    public IndexData() {}

    public IndexData(int version, List<ImageIndexEntry> entries) {
        this.version = version;
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public List<ImageIndexEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ImageIndexEntry> entries) {
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }
}
