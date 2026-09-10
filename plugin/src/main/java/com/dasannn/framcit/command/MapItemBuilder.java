package com.dasannn.framcit.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Constructor del ItemStack para mapas creados por FraMCit (F4.4, F4.5).
 * - Tipo: mapa relleno (Material.FILLED_MAP, cantidad 1).
 * - Nombre: nombre de archivo de la URL o el dominio si no se puede deducir.
 * - Lore: dominio de origen, autor y fecha.
 * - Marca interna con PersistentDataContainer identificando a FraMCit.
 */
public final class MapItemBuilder {

    public static final NamespacedKey KEY_FRAMCIT = new NamespacedKey("framcit", "map");
    public static final NamespacedKey KEY_URL = new NamespacedKey("framcit", "url");
    public static final NamespacedKey KEY_AUTHOR_UUID = new NamespacedKey("framcit", "author_uuid");
    public static final NamespacedKey KEY_AUTHOR_NAME = new NamespacedKey("framcit", "author_name");
    public static final NamespacedKey KEY_CREATED_AT = new NamespacedKey("framcit", "created_at");

    private MapItemBuilder() {}

    /**
     * Construye el ItemStack del mapa relleno con metadatos, lore y marcas PDC.
     */
    public static ItemStack buildMapItem(MapView mapView, URI finalUri, String rawUrl, Player player) {
        Objects.requireNonNull(mapView, "mapView cannot be null");
        Objects.requireNonNull(player, "player cannot be null");

        ItemStack item = new ItemStack(Material.FILLED_MAP, 1);
        MapMeta meta = (MapMeta) item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setMapView(mapView);

        String itemName = resolveItemName(finalUri, rawUrl);
        String domain = resolveDomain(finalUri, rawUrl);
        String authorName = player.getName();
        String dateStr = DateTimeFormatter.ISO_LOCAL_DATE.format(LocalDate.now());

        // Nombre visible sin cursiva
        meta.displayName(Component.text(itemName).decoration(TextDecoration.ITALIC, false));

        // Lore con origen, autor y fecha (F4.4)
        List<Component> lore = List.of(
            Component.text("Origen: " + domain, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Autor: " + authorName, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text("Fecha: " + dateStr, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)
        );
        meta.lore(lore);

        // Marca interna con PersistentDataContainer (F4.5)
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(KEY_FRAMCIT, PersistentDataType.BYTE, (byte) 1);
        if (rawUrl != null) {
            pdc.set(KEY_URL, PersistentDataType.STRING, rawUrl);
        }
        pdc.set(KEY_AUTHOR_UUID, PersistentDataType.STRING, player.getUniqueId().toString());
        pdc.set(KEY_AUTHOR_NAME, PersistentDataType.STRING, authorName);
        pdc.set(KEY_CREATED_AT, PersistentDataType.STRING, dateStr);

        item.setItemMeta(meta);
        return item;
    }

    /**
     * Extrae el nombre del archivo de la URL o el dominio si no se puede deducir.
     */
    public static String resolveItemName(URI uri, String rawUrl) {
        if (uri != null) {
            String path = uri.getPath();
            if (path != null && !path.isBlank()) {
                int lastSlash = path.lastIndexOf('/');
                String filename = (lastSlash >= 0) ? path.substring(lastSlash + 1) : path;
                if (!filename.isBlank()) {
                    try {
                        filename = URLDecoder.decode(filename, StandardCharsets.UTF_8);
                    } catch (Exception ignored) {}
                    if (!filename.isBlank()) {
                        return filename;
                    }
                }
            }
            String host = uri.getHost();
            if (host != null && !host.isBlank()) {
                return host;
            }
        }

        String domain = resolveDomain(uri, rawUrl);
        return domain.isBlank() ? "mapa" : domain;
    }

    /**
     * Obtiene el dominio de origen a partir del URI o de la URL en texto.
     */
    public static String resolveDomain(URI uri, String rawUrl) {
        if (uri != null && uri.getHost() != null && !uri.getHost().isBlank()) {
            return uri.getHost();
        }
        if (rawUrl != null && !rawUrl.isBlank()) {
            try {
                URI parsed = URI.create(rawUrl.trim());
                if (parsed.getHost() != null && !parsed.getHost().isBlank()) {
                    return parsed.getHost();
                }
            } catch (Exception ignored) {}
        }
        return "desconocido";
    }
}
