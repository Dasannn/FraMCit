package com.dasannn.framcit.store;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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

    public ImageIndexEntry() {
        this.delays = new ArrayList<>();
        this.owners = null;
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
    }

    public int getMapId() {
        return mapId;
    }

    public void setMapId(int mapId) {
        this.mapId = mapId;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public void setOriginalUrl(String originalUrl) {
        this.originalUrl = originalUrl;
    }

    public String getNormalizedUrl() {
        return normalizedUrl;
    }

    public void setNormalizedUrl(String normalizedUrl) {
        this.normalizedUrl = normalizedUrl;
    }

    public String getAuthorName() {
        return authorName;
    }

    public void setAuthorName(String authorName) {
        this.authorName = authorName;
    }

    public String getAuthorUuid() {
        return authorUuid;
    }

    public void setAuthorUuid(String authorUuid) {
        this.authorUuid = authorUuid;
    }

    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public int getFrameCount() {
        return frameCount;
    }

    public void setFrameCount(int frameCount) {
        this.frameCount = frameCount;
    }

    public List<Integer> getDelays() {
        return delays;
    }

    public void setDelays(List<Integer> delays) {
        this.delays = delays != null ? new ArrayList<>(delays) : new ArrayList<>();
    }

    public int getPaletteVersion() {
        return paletteVersion;
    }

    public void setPaletteVersion(int paletteVersion) {
        this.paletteVersion = paletteVersion;
    }

    public boolean isOrphan() {
        return orphan;
    }

    public void setOrphan(boolean orphan) {
        this.orphan = orphan;
    }

    public List<String> getOwners() {
        if (owners == null) {
            owners = new ArrayList<>();
        }
        return owners;
    }

    public List<String> getRawOwners() {
        return owners;
    }

    public void setOwners(List<String> owners) {
        this.owners = (owners != null) ? new ArrayList<>(owners) : new ArrayList<>();
    }

    public boolean hasOwner(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        return getOwners().contains(uuid);
    }

    public boolean hasOwner(java.util.UUID uuid) {
        if (uuid == null) return false;
        return hasOwner(uuid.toString());
    }

    public boolean addOwner(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        if (!getOwners().contains(uuid)) {
            return getOwners().add(uuid);
        }
        return false;
    }

    public boolean addOwner(java.util.UUID uuid) {
        if (uuid == null) return false;
        return addOwner(uuid.toString());
    }

    public boolean removeOwner(String uuid) {
        if (uuid == null || uuid.isBlank()) return false;
        return getOwners().remove(uuid);
    }

    public boolean removeOwner(java.util.UUID uuid) {
        if (uuid == null) return false;
        return removeOwner(uuid.toString());
    }

    public int getOwnerCount() {
        return getOwners().size();
    }

    public boolean isOwnedByAnyone() {
        return !getOwners().isEmpty();
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
