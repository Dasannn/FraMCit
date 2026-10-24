package com.dasannn.framcit.store;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Representa una entrada en index.json (F5.3, F5.7, F5.14).
 * Texto legible e inspeccionable (Constitution 5.4).
 */
public class ImageIndexEntry {

    private int mapId;
    private String originalUrl;
    private String normalizedUrl;
    private String authorName;
    private String authorUuid;
    private String createdAt;
    private String format;
    private int frameCount;
    private List<Integer> delays;
    private int paletteVersion;
    private boolean orphan;
    private List<String> owners;
    private List<String> pendingDeliveryOwners;
    private Map<String, String> pendingDeliveryTokens;

    public ImageIndexEntry() {
        this.delays = new ArrayList<>();
        this.owners = null;
        this.pendingDeliveryOwners = new ArrayList<>();
        this.pendingDeliveryTokens = new HashMap<>();
    }

    public ImageIndexEntry(
        int mapId,
        String originalUrl,
        String normalizedUrl,
        String authorName,
        String authorUuid,
        String createdAt,
        String format,
        int frameCount,
        List<Integer> delays,
        int paletteVersion,
        boolean orphan
    ) {
        this(
            mapId,
            originalUrl,
            normalizedUrl,
            authorName,
            authorUuid,
            createdAt,
            format,
            frameCount,
            delays,
            paletteVersion,
            orphan,
            (authorUuid != null && !authorUuid.isBlank()) ? List.of(authorUuid) : List.of()
        );
    }

    public ImageIndexEntry(
        int mapId,
        String originalUrl,
        String normalizedUrl,
        String authorName,
        String authorUuid,
        String createdAt,
        String format,
        int frameCount,
        List<Integer> delays,
        int paletteVersion,
        boolean orphan,
        List<String> owners
    ) {
        this.mapId = mapId;
        this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl cannot be null");
        this.normalizedUrl = Objects.requireNonNull(normalizedUrl, "normalizedUrl cannot be null");
        this.authorName = authorName != null ? authorName : "desconocido";
        this.authorUuid = authorUuid != null ? authorUuid : "";
        this.createdAt = createdAt != null ? createdAt : "";
        this.format = format != null ? format : "PNG";
        this.frameCount = frameCount;
        this.delays = delays != null ? new ArrayList<>(delays) : new ArrayList<>();
        this.paletteVersion = paletteVersion;
        this.orphan = orphan;
        this.owners = (owners != null) ? new ArrayList<>(owners) : new ArrayList<>();
        this.pendingDeliveryOwners = new ArrayList<>();
        this.pendingDeliveryTokens = new HashMap<>();
    }

    public int getMapId() {
        return mapId;
    }

    public synchronized void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public synchronized void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public synchronized void setNormalizedUrl(String normalizedUrl) {
        this.normalizedUrl = normalizedUrl;
    }

    public String getAuthorName() {
        return authorName;
    }

    public synchronized void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorUuid() {
        return authorUuid;
    }

    public synchronized void setAuthorUuid(String authorUuid) {
        this.authorUuid = authorUuid;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public synchronized void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormat() {
        return format;
    }

    public synchronized void setFormat(String format) {
        this.format = format;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public synchronized void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }

    public synchronized List<Integer> getDelays() {
        return delays != null ? List.copyOf(delays) : List.of();
    }

    public synchronized void setDelays(List<Integer> delays) {
        this.delays = delays != null ? new ArrayList<>(delays) : new ArrayList<>();
    }

    public int getPaletteVersion() {
        return paletteVersion;
    }

    public synchronized void setPaletteVersion(int paletteVersion) {
        this.paletteVersion = paletteVersion;
    }

    public synchronized boolean isOrphan() {
        return orphan;
    }

    public synchronized void setOrphan(boolean orphan) {
        this.orphan = orphan;
    }

    public synchronized List<String> getOwners() {
        if (owners == null) {
            owners = new ArrayList<>();
        }
        return List.copyOf(owners);
    }

    public synchronized List<String> getRawOwners() {
        return owners != null ? List.copyOf(owners) : null;
    }

    public synchronized void setOwners(List<String> owners) {
        this.owners = (owners != null) ? new ArrayList<>(owners) : new ArrayList<>();
    }

    public synchronized boolean hasOwner(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        return owners != null && owners.contains(uuid);
    }

    public boolean hasOwner(java.util.UUID uuid) {
        if (uuid == null) return false;
        return hasOwner(uuid.toString());
    }

    public synchronized boolean addOwner(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        if (owners == null) {
            owners = new ArrayList<>();
        }
        if (!owners.contains(uuid)) {
            return owners.add(uuid);
        }
        return false;
    }

    public boolean addOwner(java.util.UUID uuid) {
        if (uuid == null) return false;
        return addOwner(uuid.toString());
    }

    public synchronized boolean removeOwner(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        return owners != null && owners.remove(uuid);
    }

    public boolean removeOwner(java.util.UUID uuid) {
        if (uuid == null) return false;
        return removeOwner(uuid.toString());
    }

    public synchronized int getOwnerCount() {
        return owners != null ? owners.size() : 0;
    }

    public synchronized List<String> getPendingDeliveryOwners() {
        if (pendingDeliveryOwners == null) {
            pendingDeliveryOwners = new ArrayList<>();
        }
        return List.copyOf(pendingDeliveryOwners);
    }

    public synchronized boolean hasPendingDeliveries() {
        return pendingDeliveryOwners != null && !pendingDeliveryOwners.isEmpty();
    }

    public synchronized boolean hasPendingDelivery(String uuid) {
        return uuid != null && pendingDeliveryOwners != null && pendingDeliveryOwners.contains(uuid);
    }

    public synchronized boolean addPendingDelivery(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        if (pendingDeliveryOwners == null) {
            pendingDeliveryOwners = new ArrayList<>();
        }
        if (pendingDeliveryOwners.contains(uuid)) return false;
        pendingDeliveryOwners.add(uuid);
        pendingDeliveryTokens().put(uuid, UUID.randomUUID().toString());
        return true;
    }

    public synchronized boolean removePendingDelivery(String uuid) {
        if (uuid == null || pendingDeliveryOwners == null || !pendingDeliveryOwners.remove(uuid)) return false;
        pendingDeliveryTokens().remove(uuid);
        return true;
    }

    public synchronized String getPendingDeliveryToken(String uuid) {
        if (uuid == null || pendingDeliveryOwners == null || !pendingDeliveryOwners.contains(uuid)) return null;
        return pendingDeliveryTokens().computeIfAbsent(uuid, ignored -> UUID.randomUUID().toString());
    }

    private Map<String, String> pendingDeliveryTokens() {
        if (pendingDeliveryTokens == null) {
            pendingDeliveryTokens = new HashMap<>();
        }
        return pendingDeliveryTokens;
    }

    public synchronized boolean isOwnedByAnyone() {
        return owners != null && !owners.isEmpty();
    }

    /** Returns a detached value snapshot for asynchronous index serialization. */
    public synchronized ImageIndexEntry snapshotCopy() {
        ImageIndexEntry copy = new ImageIndexEntry(
            mapId,
            originalUrl,
            normalizedUrl,
            authorName,
            authorUuid,
            createdAt,
            format,
            frameCount,
            delays != null ? new ArrayList<>(delays) : List.of(),
            paletteVersion,
            orphan,
            owners != null ? new ArrayList<>(owners) : new ArrayList<>()
        );
        copy.setPendingDeliveryOwners(pendingDeliveryOwners != null ? new ArrayList<>(pendingDeliveryOwners) : List.of());
        copy.setPendingDeliveryTokens(pendingDeliveryTokens != null ? new HashMap<>(pendingDeliveryTokens) : Map.of());
        return copy;
    }

    public synchronized void setPendingDeliveryOwners(List<String> pendingDeliveryOwners) {
        this.pendingDeliveryOwners = (pendingDeliveryOwners != null)
            ? new ArrayList<>(pendingDeliveryOwners)
            : new ArrayList<>();
    }

    public synchronized void setPendingDeliveryTokens(Map<String, String> pendingDeliveryTokens) {
        this.pendingDeliveryTokens = (pendingDeliveryTokens != null)
            ? new HashMap<>(pendingDeliveryTokens)
            : new HashMap<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ImageIndexEntry that = (ImageIndexEntry) o;
        return mapId == that.mapId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mapId);
    }

    @Override
    public String toString() {
        return "ImageIndexEntry{" +
            "mapId=" + mapId +
            ", originalUrl='" + originalUrl + '\'' +
            ", normalizedUrl='" + normalizedUrl + '\'' +
            ", authorName='" + authorName + '\'' +
            ", authorUuid='" + authorUuid + '\'' +
            ", createdAt='" + createdAt + '\'' +
            ", format='" + format + '\'' +
            ", frameCount=" + frameCount +
            ", delays=" + delays +
            ", paletteVersion=" + paletteVersion +
            ", orphan=" + orphan +
            ", owners=" + owners +
            '}';
    }
}
