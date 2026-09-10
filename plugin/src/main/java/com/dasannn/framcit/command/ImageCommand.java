package com.dasannn.framcit.command;

import com.dasannn.framcit.FraMCitPlugin;
import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.render.FraMCitRenderer;
import com.dasannn.framcit.service.ImageProcessResult;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.FmcFormat;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import com.dasannn.framcit.store.UrlNormalizer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;

import java.net.URI;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

public final class ImageCommand {

    private ImageCommand() {}

    public static void register(Commands commands, FraMCitPlugin plugin, ImageService imageService) {
        register(commands, plugin, imageService, plugin != null ? plugin.getImageStore() : null);
    }

    public static void register(Commands commands, FraMCitPlugin plugin, ImageService imageService, ImageStore imageStore) {
        Objects.requireNonNull(commands, "commands cannot be null");
        Objects.requireNonNull(plugin, "plugin cannot be null");
        Objects.requireNonNull(imageService, "imageService cannot be null");

        LiteralCommandNode<CommandSourceStack> node = Commands.literal("imagen")
            .executes(ctx -> {
                CommandSender sender = ctx.getSource().getSender();
                if (!sender.hasPermission("framcit.imagen")) {
                    sender.sendMessage(Component.text(Messages.NO_PERMISSION));
                    return Command.SINGLE_SUCCESS;
                }
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text(Messages.ONLY_PLAYER));
                    return Command.SINGLE_SUCCESS;
                }
                sender.sendMessage(Component.text(Messages.USAGE));
                return Command.SINGLE_SUCCESS;
            })
            .then(Commands.argument("url", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    if (!sender.hasPermission("framcit.imagen")) {
                        sender.sendMessage(Component.text(Messages.NO_PERMISSION));
                        return Command.SINGLE_SUCCESS;
                    }
                    if (!(sender instanceof Player player)) {
                        sender.sendMessage(Component.text(Messages.ONLY_PLAYER));
                        return Command.SINGLE_SUCCESS;
                    }

                    String url = StringArgumentType.getString(ctx, "url").trim();
                    if (url.isEmpty()) {
                        sender.sendMessage(Component.text(Messages.USAGE));
                        return Command.SINGLE_SUCCESS;
                    }

                    // F5.4: Normalización de URL para la clave de cache
                    String normalizedUrl;
                    try {
                        normalizedUrl = UrlNormalizer.normalize(url);
                    } catch (IllegalArgumentException e) {
                        sender.sendMessage(Component.text(Messages.ERROR_MALFORMED_URL));
                        return Command.SINGLE_SUCCESS;
                    }

                    // F5.5: Comprobación de cache por URL normalizada antes de encolar descarga.
                    // Evita gastar nuevos IDs de mapa (recurso finito por mundo, R2) y descargas repetidas.
                    if (imageStore != null) {
                        ImageIndexEntry cached = imageStore.findByNormalizedUrl(normalizedUrl);
                        if (cached != null && !cached.isOrphan()) {
                            MapView existingMap = Bukkit.getMap(cached.getMapId());
                            if (existingMap != null) {
                                deliverCachedMap(plugin, player, url, cached, existingMap, imageStore);
                                return Command.SINGLE_SUCCESS;
                            } else {
                                plugin.getLogger().warning(String.format("Mapa en cache id=%d ya no existe en el mundo. Marcando como huérfano.", cached.getMapId()));
                                cached.setOrphan(true);
                                imageStore.saveIndex();
                            }
                        }
                    }

                    // F4.9: Mensaje inmediato de "Descargando imagen..." solo en caso de cache miss
                    player.sendMessage(Component.text(Messages.DOWNLOADING));

                    // F4.1: Encadenar descarga, decodificacion y conversion en el pool asincrono
                    imageService.process(url)
                        .thenAccept(result -> {
                            // F4.2: Salto explicito de vuelta al hilo principal
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                handleResult(plugin, player, url, normalizedUrl, result, imageStore);
                            });
                        });

                    return Command.SINGLE_SUCCESS;
                })
            )
            .build();

        commands.register(node, "Convierte una URL de imagen o GIF en un mapa", List.of("img", "image"));
    }

    /**
     * Entrega un mapa ya existente desde la cache sin crear un nuevo MapView ni encolar descargas (F5.5).
     */
    private static void deliverCachedMap(
        FraMCitPlugin plugin,
        Player player,
        String rawUrl,
        ImageIndexEntry cached,
        MapView mapView,
        ImageStore imageStore
    ) {
        if (!player.isOnline()) {
            return;
        }

        // Asegurar que FraMCitRenderer esté vinculado
        boolean hasRenderer = false;
        for (MapRenderer r : mapView.getRenderers()) {
            if (r instanceof FraMCitRenderer) {
                hasRenderer = true;
                break;
            }
        }
        if (!hasRenderer) {
            for (MapRenderer r : mapView.getRenderers()) {
                mapView.removeRenderer(r);
            }
            mapView.addRenderer(new FraMCitRenderer(cached.getMapId(), imageStore));
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);
        }

        URI uri = null;
        try {
            uri = URI.create(cached.getOriginalUrl());
        } catch (Exception ignored) {}

        ItemStack item = MapItemBuilder.buildMapItem(mapView, uri, rawUrl, player);

        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack dropped : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), dropped);
            }
            player.sendMessage(Component.text(Messages.INVENTORY_FULL));
        }

        if (cached.getFrameCount() > 1) {
            player.sendMessage(Component.text(Messages.animatedSuccess(cached.getFrameCount())));
        } else {
            player.sendMessage(Component.text(Messages.STATIC_SUCCESS));
        }

        plugin.getLogger().info(String.format(
            "Mapa entregado desde cache: jugador=%s, uuid=%s, url=%s, id=%d",
            player.getName(),
            player.getUniqueId(),
            rawUrl,
            mapView.getId()
        ));
    }

    public static void handleResult(FraMCitPlugin plugin, Player player, String rawUrl, ImageProcessResult result) {
        String normalized = null;
        try {
            normalized = UrlNormalizer.normalize(rawUrl);
        } catch (Exception ignored) {}
        handleResult(plugin, player, rawUrl, normalized, result, plugin != null ? plugin.getImageStore() : null);
    }

    /**
     * Procesa el resultado de la imagen en el hilo principal de Bukkit.
     */
    public static void handleResult(
        FraMCitPlugin plugin,
        Player player,
        String rawUrl,
        String normalizedUrl,
        ImageProcessResult result,
        ImageStore imageStore
    ) {
        if (result instanceof ImageProcessResult.Failure failure) {
            if (failure.cause() != null) {
                plugin.getLogger().warning("Fallo al procesar imagen para " + player.getName() + " (" + rawUrl + "): " + failure.cause().getMessage());
            }
            if (player.isOnline()) {
                // F4.13 / SEC 9: Ningun mensaje al jugador filtra rutas, IPs ni trazas
                player.sendMessage(Component.text(failure.userMessage()));
            }
            return;
        }

        ImageProcessResult.Success success = (ImageProcessResult.Success) result;

        // F4.7: Si el jugador se desconecto durante la descarga, cancelar sin entregar nada
        if (!player.isOnline()) {
            plugin.getLogger().info("Descarga completada para " + player.getName() + " (" + rawUrl + "), pero el jugador se desconecto. Entrega cancelada.");
            return;
        }

        try {
            // F4.3: Crear MapView en el hilo principal
            MapView mapView = Bukkit.createMap(player.getWorld());
            for (MapRenderer r : mapView.getRenderers()) {
                mapView.removeRenderer(r);
            }
            int mapId = mapView.getId();
            mapView.addRenderer(new FraMCitRenderer(mapId, imageStore, success.image()));
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);

            // F4.4 y F4.5: Construir el item (mapa relleno, nombre, lore y marca PDC)
            ItemStack item = MapItemBuilder.buildMapItem(mapView, success.finalUri(), rawUrl, player);

            // F4.6: Entrega al inventario; si esta lleno, soltar al suelo y avisar por chat
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack dropped : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
                player.sendMessage(Component.text(Messages.INVENTORY_FULL));
            }

            // F4.10: Mensaje final segun formato y numero de frames
            if (success.image().isAnimated()) {
                player.sendMessage(Component.text(Messages.animatedSuccess(success.image().getFrameCount())));
            } else {
                player.sendMessage(Component.text(Messages.STATIC_SUCCESS));
            }

            // F4.11 [SEC]: Log obligatorio con jugador, UUID, URL completa, tamano, formato, id de mapa
            plugin.getLogger().info(String.format(
                "Mapa creado: jugador=%s, uuid=%s, url=%s, tamano=%d bytes, formato=%s, id=%d",
                player.getName(),
                player.getUniqueId(),
                rawUrl,
                success.byteCount(),
                success.image().getFormat().name(),
                mapId
            ));

            // F5.2, F5.11, F5.14: Guardar imagen en .fmc y actualizar index.json fuera del hilo principal
            if (imageStore != null) {
                String norm = (normalizedUrl != null) ? normalizedUrl : UrlNormalizer.normalize(rawUrl);
                List<Integer> delays = success.image().getFrames().stream()
                    .map(ConvertedFrame::getDelayMs)
                    .toList();

                ImageIndexEntry entry = new ImageIndexEntry(
                    mapId,
                    rawUrl,
                    norm,
                    player.getName(),
                    player.getUniqueId().toString(),
                    LocalDate.now().toString(),
                    success.image().getFormat().name(),
                    success.image().getFrameCount(),
                    delays,
                    FmcFormat.PALETTE_VERSION,
                    false
                );

                imageStore.saveImageAsync(mapId, success.image(), entry);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Error inesperado al crear o entregar el mapa a " + player.getName(), t);
            if (player.isOnline()) {
                player.sendMessage(Component.text(Messages.ERROR_CORRUPTED_FILE));
            }
        }
    }
}
