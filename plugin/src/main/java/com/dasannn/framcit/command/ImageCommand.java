package com.dasannn.framcit.command;

import com.dasannn.framcit.FraMCitPlugin;
import com.dasannn.framcit.economy.ChargeResult;
import com.dasannn.framcit.economy.EconomyCheckResult;
import com.dasannn.framcit.economy.EconomyService;
import com.dasannn.framcit.economy.NoOpEconomyService;
import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.render.FraMCitRenderer;
import com.dasannn.framcit.render.GazeTracker;
import com.dasannn.framcit.service.ImageProcessResult;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.FmcFormat;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import com.dasannn.framcit.store.UrlNormalizer;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;

import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;

public final class ImageCommand {

    public static final int ITEMS_PER_PAGE = 5;

    private ImageCommand() {}

    public static void register(Commands commands, FraMCitPlugin plugin, ImageService imageService) {
        register(commands, plugin, imageService, plugin != null ? plugin.getImageStore() : null);
    }

    public static void register(Commands commands, FraMCitPlugin plugin, ImageService imageService, ImageStore imageStore) {
        Objects.requireNonNull(commands, "commands cannot be null");
        LiteralCommandNode<CommandSourceStack> node = createCommandNode(plugin, imageService, imageStore);
        commands.register(node, "Convierte una URL de imagen o GIF en un mapa", List.of("img", "image"));
    }

    public static LiteralCommandNode<CommandSourceStack> createCommandNode(FraMCitPlugin plugin, ImageService imageService, ImageStore imageStore) {
        return createCommandNode(plugin, imageService, imageStore, null);
    }

    public static LiteralCommandNode<CommandSourceStack> createCommandNode(
        FraMCitPlugin plugin,
        ImageService imageService,
        ImageStore imageStore,
        EconomyService fallbackEconomyService
    ) {
        return Commands.literal("imagen")
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
            // Subcomando: /imagen recargar (F7.5)
            .then(Commands.literal("recargar")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> handleReload(ctx.getSource().getSender(), plugin))
            )
            // Subcomando: /imagen info (F7.6)
            .then(Commands.literal("info")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> handleInfo(ctx.getSource().getSender(), plugin, imageStore))
            )
            // Subcomando: /imagen purgar <url|id> [confirmar] (F7.7)
            .then(Commands.literal("purgar")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(Component.text(Messages.ADMIN_PURGE_USAGE));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("objetivo", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> {
                        if (imageStore == null) return builder.buildFuture();
                        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
                        for (ImageIndexEntry entry : imageStore.getIndexManager().getAllEntries()) {
                            String idStr = String.valueOf(entry.getMapId());
                            String url = entry.getOriginalUrl();
                            if (idStr.startsWith(remaining)) {
                                builder.suggest(idStr);
                            }
                            if (url.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                                builder.suggest(url);
                            }
                            if (remaining.equals(idStr) || remaining.equals(idStr + " ")) {
                                builder.suggest(idStr + " confirmar");
                            }
                            if (remaining.equalsIgnoreCase(url) || remaining.equalsIgnoreCase(url + " ")) {
                                builder.suggest(url + " confirmar");
                            }
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String rawTarget = StringArgumentType.getString(ctx, "objetivo");
                        return handlePurge(ctx.getSource().getSender(), rawTarget, plugin, imageStore);
                    })
                )
            )
            // Subcomando: /imagen listar [pagina] (F7.8)
            .then(Commands.literal("listar")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> handleList(ctx.getSource().getSender(), 1, imageStore))
                .then(Commands.argument("pagina", IntegerArgumentType.integer())
                    .suggests((ctx, builder) -> {
                        int total = (imageStore != null) ? imageStore.getIndexManager().size() : 0;
                        int totalPages = Math.max(1, (int) Math.ceil((double) total / ITEMS_PER_PAGE));
                        for (int i = 1; i <= totalPages; i++) {
                            builder.suggest(String.valueOf(i));
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        int page = IntegerArgumentType.getInteger(ctx, "pagina");
                        return handleList(ctx.getSource().getSender(), page, imageStore);
                    })
                )
            )
            // Argumento: /imagen <url> (Spec 2.1)
            .then(Commands.argument("url", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String url = StringArgumentType.getString(ctx, "url");
                    return handleImage(sender, url, plugin, imageService, imageStore, fallbackEconomyService);
                })
            )
            .build();
    }

    /**
     * Maneja la ejecución de /imagen <url> (F4.1, F5.5, F10.7, F10.11).
     */
    public static int handleImage(
        CommandSender sender,
        String rawUrl,
        FraMCitPlugin plugin,
        ImageService imageService,
        ImageStore imageStore,
        EconomyService fallbackEconomyService
    ) {
        String url = rawUrl != null ? rawUrl.trim() : "";

        // Interceptar si un usuario sin framcit.admin intentó escribir un subcomando
        String urlLower = url.toLowerCase(Locale.ROOT);
        if (urlLower.equals("recargar") || urlLower.equals("info")
            || urlLower.startsWith("purgar") || urlLower.startsWith("listar")) {
            if (!sender.hasPermission("framcit.admin")) {
                sender.sendMessage(Component.text(Messages.NO_PERMISSION));
                return Command.SINGLE_SUCCESS;
            }
        }

        if (!sender.hasPermission("framcit.imagen")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(Messages.ONLY_PLAYER));
            return Command.SINGLE_SUCCESS;
        }

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

        // Obtener servicio de economía activo o fallback (F10.4)
        EconomyService eco = (plugin != null && plugin.getEconomyService() != null)
            ? plugin.getEconomyService()
            : (fallbackEconomyService != null ? fallbackEconomyService : NoOpEconomyService.INSTANCE);

        // F10.7, F10.11: Comprobar saldo ANTES de descargar o entregar mapa en caché
        EconomyCheckResult checkResult = eco.checkCanPay(player);
        if (checkResult instanceof EconomyCheckResult.InsufficientFunds inf) {
            player.sendMessage(Component.text(Messages.insufficientFunds(inf.formattedPrice(), inf.formattedBalance())));
            return Command.SINGLE_SUCCESS;
        }

        // F5.5: Comprobación de cache por URL normalizada antes de encolar descarga.
        if (imageStore != null) {
            ImageIndexEntry cached = imageStore.findByNormalizedUrl(normalizedUrl);
            if (cached != null && !cached.isOrphan()) {
                MapView existingMap = null;
                try {
                    existingMap = Bukkit.getMap(cached.getMapId());
                } catch (Throwable ignored) {}

                if (existingMap != null) {
                    deliverCachedMap(plugin, eco, player, url, cached, existingMap, imageStore);
                    return Command.SINGLE_SUCCESS;
                } else {
                    if (plugin != null) {
                        plugin.getLogger().warning(String.format("Mapa en cache id=%d ya no existe en el mundo. Marcando como huérfano.", cached.getMapId()));
                    }
                    cached.setOrphan(true);
                    imageStore.saveIndex();
                }
            }
        }

        // F4.9: Mensaje inmediato de "Descargando imagen..." solo en caso de cache miss
        player.sendMessage(Component.text(Messages.DOWNLOADING));

        // F4.1: Encadenar descarga, decodificación y conversión en el pool asíncrono
        if (imageService != null) {
            imageService.process(url)
                .thenAccept(result -> {
                    // F4.2: Salto explícito de vuelta al hilo principal
                    if (plugin != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            handleResult(plugin, eco, player, url, normalizedUrl, result, imageStore);
                        });
                    } else {
                        handleResult(null, eco, player, url, normalizedUrl, result, imageStore);
                    }
                });
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen recargar (F7.5).
     */
    public static int handleReload(CommandSender sender, FraMCitPlugin plugin) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (plugin != null) {
            plugin.reloadPluginConfig();
            plugin.reloadMessages();
        }

        sender.sendMessage(Component.text(Messages.ADMIN_RELOAD_SUCCESS));
        if (plugin != null) {
            plugin.getLogger().info("Configuración y mensajes recargados por " + sender.getName() + ".");
        }
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen info (F7.6).
     */
    public static int handleInfo(CommandSender sender, FraMCitPlugin plugin, ImageStore imageStore) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(Messages.ONLY_PLAYER));
            return Command.SINGLE_SUCCESS;
        }

        double maxDist = 24.0;
        if (plugin != null && plugin.getGazeTracker() != null) {
            maxDist = plugin.getGazeTracker().getMaxDistance();
        }

        RayTraceResult hit = player.getWorld().rayTrace(
            player.getEyeLocation(),
            player.getEyeLocation().getDirection(),
            maxDist,
            FluidCollisionMode.NEVER,
            true,
            0.0,
            entity -> entity instanceof ItemFrame
        );

        if (hit == null || !(hit.getHitEntity() instanceof ItemFrame frame)) {
            player.sendMessage(Component.text(Messages.ADMIN_INFO_NO_FRAME));
            return Command.SINGLE_SUCCESS;
        }

        int mapId = GazeTracker.extractFraMCitMapId(frame.getItem());
        if (mapId < 0) {
            player.sendMessage(Component.text(Messages.ADMIN_INFO_NO_FRAME));
            return Command.SINGLE_SUCCESS;
        }

        ImageIndexEntry entry = (imageStore != null) ? imageStore.findByMapId(mapId) : null;
        if (entry == null) {
            player.sendMessage(Component.text(
                Messages.ADMIN_INFO_NOT_FOUND.replace("{id}", String.valueOf(mapId))
            ));
            return Command.SINGLE_SUCCESS;
        }

        player.sendMessage(Component.text(String.format("Información de la imagen (ID: %d):", mapId)));
        player.sendMessage(Component.text("  Autor: " + (entry.getAuthorName() != null ? entry.getAuthorName() : "Desconocido")));
        player.sendMessage(Component.text("  Fecha: " + (entry.getCreatedAt() != null ? entry.getCreatedAt() : "-")));
        player.sendMessage(Component.text("  URL: " + entry.getOriginalUrl()));
        player.sendMessage(Component.text(String.format("  Formato: %s (%d frame(s))", entry.getFormat(), entry.getFrameCount())));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen purgar <url|id> [confirmar] (F7.7).
     */
    public static int handlePurge(CommandSender sender, String rawArg, FraMCitPlugin plugin, ImageStore imageStore) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (rawArg == null || rawArg.isBlank()) {
            sender.sendMessage(Component.text(Messages.ADMIN_PURGE_USAGE));
            return Command.SINGLE_SUCCESS;
        }

        String trimmed = rawArg.trim();
        boolean confirmed = false;
        String target = trimmed;
        if (trimmed.toLowerCase(Locale.ROOT).endsWith(" confirmar")) {
            confirmed = true;
            target = trimmed.substring(0, trimmed.length() - " confirmar".length()).trim();
        }

        if (imageStore == null) {
            sender.sendMessage(Component.text(Messages.ADMIN_PURGE_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }

        ImageIndexEntry entry = null;
        int targetId = -1;
        try {
            targetId = Integer.parseInt(target);
            entry = imageStore.findByMapId(targetId);
        } catch (NumberFormatException ignored) {}

        if (entry == null) {
            entry = imageStore.findByUrl(target);
            if (entry == null) {
                try {
                    String norm = UrlNormalizer.normalize(target);
                    entry = imageStore.findByNormalizedUrl(norm);
                } catch (Exception ignored) {}
            }
        }

        if (entry == null) {
            for (ImageIndexEntry e : imageStore.getIndexManager().getAllEntries()) {
                if (target.equalsIgnoreCase(e.getOriginalUrl()) || target.equalsIgnoreCase(e.getNormalizedUrl())) {
                    entry = e;
                    break;
                }
            }
        }

        if (entry == null) {
            sender.sendMessage(Component.text(Messages.ADMIN_PURGE_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }

        int mapId = entry.getMapId();

        if (!confirmed) {
            sender.sendMessage(Component.text(
                Messages.ADMIN_PURGE_CONFIRMATION
                    .replace("{id}", String.valueOf(mapId))
                    .replace("{target}", target)
            ));
            return Command.SINGLE_SUCCESS;
        }

        // Ejecutar purga de imagen
        imageStore.getLruCache().remove(mapId);

        java.nio.file.Path fmcPath = imageStore.getImagesFolder().resolve(mapId + ".fmc");
        try {
            java.nio.file.Files.deleteIfExists(fmcPath);
        } catch (java.io.IOException e) {
            if (plugin != null) {
                plugin.getLogger().warning("Error al borrar " + fmcPath + ": " + e.getMessage());
            }
        }

        imageStore.getIndexManager().removeEntry(mapId);
        imageStore.saveIndex();

        if (plugin != null && plugin.getAnimationManager() != null) {
            plugin.getAnimationManager().removeMap(mapId);
        }

        try {
            MapView mapView = Bukkit.getMap(mapId);
            if (mapView != null) {
                for (MapRenderer r : mapView.getRenderers()) {
                    mapView.removeRenderer(r);
                }
            }
        } catch (Throwable ignored) {}

        sender.sendMessage(Component.text(
            Messages.ADMIN_PURGE_SUCCESS.replace("{id}", String.valueOf(mapId))
        ));

        if (plugin != null) {
            plugin.getLogger().info(String.format("Imagen purgada por %s: id=%d, url=%s", sender.getName(), mapId, entry.getOriginalUrl()));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen listar [pagina] (F7.8).
     */
    public static int handleList(CommandSender sender, int page, ImageStore imageStore) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (imageStore == null) {
            sender.sendMessage(Component.text(Messages.ADMIN_LIST_EMPTY));
            return Command.SINGLE_SUCCESS;
        }

        List<ImageIndexEntry> entries = new ArrayList<>(imageStore.getIndexManager().getAllEntries());
        if (entries.isEmpty()) {
            sender.sendMessage(Component.text(Messages.ADMIN_LIST_EMPTY));
            return Command.SINGLE_SUCCESS;
        }

        entries.sort(Comparator.comparingInt(ImageIndexEntry::getMapId));

        int totalEntries = entries.size();
        int totalPages = (int) Math.ceil((double) totalEntries / ITEMS_PER_PAGE);

        if (page < 1 || page > totalPages) {
            sender.sendMessage(Component.text(
                Messages.ADMIN_LIST_PAGE_OUT_OF_RANGE.replace("{total_pages}", String.valueOf(totalPages))
            ));
            return Command.SINGLE_SUCCESS;
        }

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalEntries);

        sender.sendMessage(Component.text(
            Messages.ADMIN_LIST_HEADER
                .replace("{page}", String.valueOf(page))
                .replace("{total_pages}", String.valueOf(totalPages))
        ));

        for (int i = startIndex; i < endIndex; i++) {
            ImageIndexEntry e = entries.get(i);
            String line = Messages.ADMIN_LIST_ITEM
                .replace("{id}", String.valueOf(e.getMapId()))
                .replace("{author}", e.getAuthorName() != null ? e.getAuthorName() : "Desconocido")
                .replace("{date}", e.getCreatedAt() != null ? e.getCreatedAt() : "-")
                .replace("{url}", e.getOriginalUrl());
            sender.sendMessage(Component.text(line));
        }

        if (page < totalPages) {
            sender.sendMessage(Component.text(
                Messages.ADMIN_LIST_FOOTER
                    .replace("{next_page}", String.valueOf(page + 1))
                    .replace("{page}", String.valueOf(page + 1))
            ));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Entrega un mapa ya existente desde la cache sin crear un nuevo MapView ni encolar descargas (F5.5).
     */
    private static void deliverCachedMap(
        FraMCitPlugin plugin,
        EconomyService eco,
        Player player,
        String rawUrl,
        ImageIndexEntry cached,
        MapView mapView,
        ImageStore imageStore
    ) {
        if (!player.isOnline()) {
            return;
        }

        // F10.8, F10.11: Cobrar al entregar el mapa
        ChargeResult chargeResult = (eco != null) ? eco.charge(player) : new ChargeResult.Success(true, "gratis");
        if (chargeResult instanceof ChargeResult.InsufficientFunds) {
            player.sendMessage(Component.text(Messages.ECONOMY_FUNDS_LOST));
            if (plugin != null) {
                plugin.getLogger().info(String.format("Entrega de mapa en cache cancelada para %s (%s): saldo perdido entre comprobación y cobro.", player.getName(), rawUrl));
            }
            return;
        }
        if (chargeResult instanceof ChargeResult.Failure failure) {
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, String.format("Fallo en plugin de economía al cobrar mapa en cache a %s (%s): %s", player.getName(), rawUrl, failure.errorMessage()), failure.cause());
            }
            player.sendMessage(Component.text(Messages.ECONOMY_PAYMENT_FAILED));
            return;
        }

        ChargeResult.Success successCharge = (ChargeResult.Success) chargeResult;

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
            boolean contextual = cached.getFrameCount() > 1;
            mapView.addRenderer(new FraMCitRenderer(cached.getMapId(), imageStore, null, contextual));
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

        // F10.13, F10.15: Mensaje de cobro si no fue gratis
        if (!successCharge.isFree()) {
            player.sendMessage(Component.text(Messages.chargeSuccess(successCharge.formattedAmount())));
        }

        if (cached.getFrameCount() > 1) {
            player.sendMessage(Component.text(Messages.animatedSuccess(cached.getFrameCount())));
        } else {
            player.sendMessage(Component.text(Messages.STATIC_SUCCESS));
        }

        if (plugin != null) {
            // F10.14: Log obligatorio incorporando cobro
            plugin.getLogger().info(String.format(
                "Mapa entregado desde cache: jugador=%s, uuid=%s, url=%s, id=%d, cobro=%s",
                player.getName(),
                player.getUniqueId(),
                rawUrl,
                mapView.getId(),
                successCharge.formattedAmount()
            ));
        }
    }

    public static void handleResult(FraMCitPlugin plugin, Player player, String rawUrl, ImageProcessResult result) {
        String normalized = null;
        try {
            normalized = UrlNormalizer.normalize(rawUrl);
        } catch (Exception ignored) {}
        EconomyService eco = (plugin != null && plugin.getEconomyService() != null) ? plugin.getEconomyService() : NoOpEconomyService.INSTANCE;
        handleResult(plugin, eco, player, rawUrl, normalized, result, plugin != null ? plugin.getImageStore() : null);
    }

    public static void handleResult(
        FraMCitPlugin plugin,
        Player player,
        String rawUrl,
        String normalizedUrl,
        ImageProcessResult result,
        ImageStore imageStore
    ) {
        EconomyService eco = (plugin != null && plugin.getEconomyService() != null) ? plugin.getEconomyService() : NoOpEconomyService.INSTANCE;
        handleResult(plugin, eco, player, rawUrl, normalizedUrl, result, imageStore);
    }

    /**
     * Procesa el resultado de la imagen en el hilo principal de Bukkit (F10.8, F10.9, F10.10, F10.14).
     */
    public static void handleResult(
        FraMCitPlugin plugin,
        EconomyService eco,
        Player player,
        String rawUrl,
        String normalizedUrl,
        ImageProcessResult result,
        ImageStore imageStore
    ) {
        if (result instanceof ImageProcessResult.Failure failure) {
            if (failure.cause() != null && plugin != null) {
                plugin.getLogger().warning("Fallo al procesar imagen para " + player.getName() + " (" + rawUrl + "): " + failure.cause().getMessage());
            }
            if (player.isOnline()) {
                // F4.13 / SEC 9: Ningún mensaje al jugador filtra rutas, IPs ni trazas
                player.sendMessage(Component.text(failure.userMessage()));
            }
            return;
        }

        ImageProcessResult.Success success = (ImageProcessResult.Success) result;

        // F4.7: Si el jugador se desconectó durante la descarga, cancelar sin entregar nada (ni cobrar)
        if (!player.isOnline()) {
            if (plugin != null) {
                plugin.getLogger().info("Descarga completada para " + player.getName() + " (" + rawUrl + "), pero el jugador se desconecto. Entrega cancelada.");
            }
            return;
        }

        // F10.8: Cobrar AL ENTREGAR el mapa, nunca antes
        ChargeResult chargeResult = (eco != null) ? eco.charge(player) : new ChargeResult.Success(true, "gratis");
        if (chargeResult instanceof ChargeResult.InsufficientFunds) {
            // F10.9: Saldo perdido entre la comprobación y el cobro: no se entrega el mapa y se descarta lo creado
            player.sendMessage(Component.text(Messages.ECONOMY_FUNDS_LOST));
            if (plugin != null) {
                plugin.getLogger().info(String.format("Entrega cancelada para %s (%s): saldo perdido entre comprobación y cobro.", player.getName(), rawUrl));
            }
            return;
        }
        if (chargeResult instanceof ChargeResult.Failure failure) {
            // F10.10: Fallo del plugin de economía al cobrar: no se entrega el mapa, traza completa al log y mensaje genérico al jugador
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, String.format("Fallo en plugin de economía al cobrar a %s (%s): %s", player.getName(), rawUrl, failure.errorMessage()), failure.cause());
            }
            player.sendMessage(Component.text(Messages.ECONOMY_PAYMENT_FAILED));
            return;
        }

        ChargeResult.Success successCharge = (ChargeResult.Success) chargeResult;

        try {
            // F4.3: Crear MapView en el hilo principal
            MapView mapView = Bukkit.createMap(player.getWorld());
            for (MapRenderer r : mapView.getRenderers()) {
                mapView.removeRenderer(r);
            }
            int mapId = mapView.getId();
            boolean contextual = success.image().isAnimated();
            mapView.addRenderer(new FraMCitRenderer(mapId, imageStore, success.image(), contextual));
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);

            // F4.4 y F4.5: Construir el item (mapa relleno, nombre, lore y marca PDC)
            ItemStack item = MapItemBuilder.buildMapItem(mapView, success.finalUri(), rawUrl, player);

            // F4.6: Entrega al inventario; si está lleno, soltar al suelo y avisar por chat
            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack dropped : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
                player.sendMessage(Component.text(Messages.INVENTORY_FULL));
            }

            // F10.13, F10.15: Mensaje de cobro si no fue gratis
            if (!successCharge.isFree()) {
                player.sendMessage(Component.text(Messages.chargeSuccess(successCharge.formattedAmount())));
            }

            // F4.10: Mensaje final según formato y número de frames
            if (success.image().isAnimated()) {
                player.sendMessage(Component.text(Messages.animatedSuccess(success.image().getFrameCount())));
            } else {
                player.sendMessage(Component.text(Messages.STATIC_SUCCESS));
            }

            // F4.11 [SEC] & F10.14: Log obligatorio incorporando cobro ("gratis" o importe)
            if (plugin != null) {
                plugin.getLogger().info(String.format(
                    "Mapa creado: jugador=%s, uuid=%s, url=%s, tamano=%d bytes, formato=%s, id=%d, cobro=%s",
                    player.getName(),
                    player.getUniqueId(),
                    rawUrl,
                    success.byteCount(),
                    success.image().getFormat().name(),
                    mapId,
                    successCharge.formattedAmount()
                ));
            }

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
            if (plugin != null) {
                plugin.getLogger().log(Level.SEVERE, "Error inesperado al crear o entregar el mapa a " + player.getName(), t);
            }
            if (player.isOnline()) {
                player.sendMessage(Component.text(Messages.ERROR_CORRUPTED_FILE));
            }
        }
    }
}
