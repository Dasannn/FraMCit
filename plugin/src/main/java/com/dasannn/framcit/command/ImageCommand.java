package com.dasannn.framcit.command;

import com.dasannn.framcit.FraMCitPlugin;
import com.dasannn.framcit.config.FraMCitConfig;
import com.dasannn.framcit.economy.ChargeResult;
import com.dasannn.framcit.economy.EconomyCheckResult;
import com.dasannn.framcit.economy.EconomyService;
import com.dasannn.framcit.economy.NoOpEconomyService;
import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.TooManyFramesException;
import com.dasannn.framcit.quota.PermissionTier;
import com.dasannn.framcit.quota.QuotaService;
import com.dasannn.framcit.render.FraMCitRenderer;
import com.dasannn.framcit.render.GazeTracker;
import com.dasannn.framcit.render.PlayerMapState;
import com.dasannn.framcit.service.ImageProcessResult;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.FmcFormat;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import com.dasannn.framcit.store.LruMemoryCache;
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
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.map.MapRenderer;
import org.bukkit.map.MapView;
import org.bukkit.util.RayTraceResult;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
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
        return createCommandNode(plugin, imageService, imageStore, null, null);
    }

    public static LiteralCommandNode<CommandSourceStack> createCommandNode(
        FraMCitPlugin plugin,
        ImageService imageService,
        ImageStore imageStore,
        EconomyService fallbackEconomyService
    ) {
        return createCommandNode(plugin, imageService, imageStore, fallbackEconomyService, null);
    }

    public static LiteralCommandNode<CommandSourceStack> createCommandNode(
        FraMCitPlugin plugin,
        ImageService imageService,
        ImageStore imageStore,
        EconomyService fallbackEconomyService,
        QuotaService fallbackQuotaService
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
            // Subcomando: /imagen mias (F11.15)
            .then(Commands.literal("mias")
                .requires(source -> source.getSender().hasPermission("framcit.imagen"))
                .executes(ctx -> handleMias(ctx.getSource().getSender(), plugin, imageStore, fallbackQuotaService))
            )
            // Subcomando: /imagen borrar <id> [confirmar] (F11.16, F11.17)
            .then(Commands.literal("borrar")
                .requires(source -> source.getSender().hasPermission("framcit.imagen"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(Component.text("Uso: /imagen borrar <id> [confirmar]"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("id", IntegerArgumentType.integer())
                    .executes(ctx -> handleBorrar(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "id"), false, plugin, imageStore, fallbackQuotaService))
                    .then(Commands.literal("confirmar")
                        .executes(ctx -> handleBorrar(ctx.getSource().getSender(), IntegerArgumentType.getInteger(ctx, "id"), true, plugin, imageStore, fallbackQuotaService))
                    )
                )
            )
            // Subcomando: /imagen listar [jugador] [pagina] (F7.8, F11.18)
            .then(Commands.literal("listar")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> handleList(ctx.getSource().getSender(), null, 1, imageStore, fallbackQuotaService))
                .then(Commands.argument("arg1", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        if (imageStore == null) return builder.buildFuture();
                        int total = imageStore.getIndexManager().size();
                        int totalPages = Math.max(1, (int) Math.ceil((double) total / ITEMS_PER_PAGE));
                        for (int i = 1; i <= totalPages; i++) {
                            builder.suggest(String.valueOf(i));
                        }
                        if (Bukkit.getServer() != null) {
                            try {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (p != null && p.getName() != null) {
                                        builder.suggest(p.getName());
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> {
                        String arg = StringArgumentType.getString(ctx, "arg1");
                        try {
                            int page = Integer.parseInt(arg);
                            return handleList(ctx.getSource().getSender(), null, page, imageStore, fallbackQuotaService);
                        } catch (NumberFormatException e) {
                            return handleList(ctx.getSource().getSender(), arg, 1, imageStore, fallbackQuotaService);
                        }
                    })
                    .then(Commands.argument("pagina", IntegerArgumentType.integer())
                        .suggests((ctx, builder) -> {
                            for (int i = 1; i <= 10; i++) {
                                builder.suggest(String.valueOf(i));
                            }
                            return builder.buildFuture();
                        })
                        .executes(ctx -> {
                            String playerArg = StringArgumentType.getString(ctx, "arg1");
                            int page = IntegerArgumentType.getInteger(ctx, "pagina");
                            return handleList(ctx.getSource().getSender(), playerArg, page, imageStore, fallbackQuotaService);
                        })
                    )
                )
            )
            // Subcomando: /imagen uso [jugador] (F11.19)
            .then(Commands.literal("uso")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> handleUso(ctx.getSource().getSender(), null, plugin, imageStore, fallbackQuotaService))
                .then(Commands.argument("jugador", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        if (Bukkit.getServer() != null) {
                            try {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (p != null && p.getName() != null) {
                                        builder.suggest(p.getName());
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> handleUso(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "jugador"), plugin, imageStore, fallbackQuotaService))
                )
            )
            // Subcomando: /imagen purgar-jugador <jugador> [confirmar] (F11.20)
            .then(Commands.literal("purgar-jugador")
                .requires(source -> source.getSender().hasPermission("framcit.admin"))
                .executes(ctx -> {
                    ctx.getSource().getSender().sendMessage(Component.text("Uso: /imagen purgar-jugador <jugador> [confirmar]"));
                    return Command.SINGLE_SUCCESS;
                })
                .then(Commands.argument("jugador", StringArgumentType.word())
                    .suggests((ctx, builder) -> {
                        if (Bukkit.getServer() != null) {
                            try {
                                for (Player p : Bukkit.getOnlinePlayers()) {
                                    if (p != null && p.getName() != null) {
                                        builder.suggest(p.getName());
                                    }
                                }
                            } catch (Throwable ignored) {}
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> handlePurgarJugador(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "jugador"), false, plugin, imageStore, fallbackQuotaService))
                    .then(Commands.literal("confirmar")
                        .executes(ctx -> handlePurgarJugador(ctx.getSource().getSender(), StringArgumentType.getString(ctx, "jugador"), true, plugin, imageStore, fallbackQuotaService))
                    )
                )
            )
            // Argumento: /imagen <url> (Spec 2.1)
            .then(Commands.argument("url", StringArgumentType.greedyString())
                .executes(ctx -> {
                    CommandSender sender = ctx.getSource().getSender();
                    String url = StringArgumentType.getString(ctx, "url");
                    return handleImage(sender, url, plugin, imageService, imageStore, fallbackEconomyService, fallbackQuotaService);
                })
            )
            .build();
    }

    /**
     * Maneja la ejecución de /imagen <url> (F4.1, F5.5, F10.7, F10.11, F11.8, F11.10, F11.11, F11.12).
     */
    public static int handleImage(
        CommandSender sender,
        String rawUrl,
        FraMCitPlugin plugin,
        ImageService imageService,
        ImageStore imageStore,
        EconomyService fallbackEconomyService
    ) {
        return handleImage(sender, rawUrl, plugin, imageService, imageStore, fallbackEconomyService, null);
    }

    public static int handleImage(
        CommandSender sender,
        String rawUrl,
        FraMCitPlugin plugin,
        ImageService imageService,
        ImageStore imageStore,
        EconomyService fallbackEconomyService,
        QuotaService fallbackQuotaService
    ) {
        String url = rawUrl != null ? rawUrl.trim() : "";

        // Interceptar si un usuario sin permisos intentó escribir un subcomando
        String urlLower = url.toLowerCase(Locale.ROOT);
        if (urlLower.equals("recargar") || urlLower.equals("info")
            || urlLower.startsWith("purgar ") || urlLower.equals("purgar")
            || urlLower.startsWith("listar") || urlLower.startsWith("uso")
            || urlLower.startsWith("purgar-jugador")) {
            if (!sender.hasPermission("framcit.admin")) {
                sender.sendMessage(Component.text(Messages.NO_PERMISSION));
                return Command.SINGLE_SUCCESS;
            }
        }
        if (urlLower.equals("mias") || urlLower.startsWith("borrar")) {
            if (!sender.hasPermission("framcit.imagen")) {
                sender.sendMessage(Component.text(Messages.NO_PERMISSION));
                return Command.SINGLE_SUCCESS;
            }
        }

        // Paso 1: Permiso framcit.imagen
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

        // Normalización de URL para la clave de cache
        String normalizedUrl;
        try {
            normalizedUrl = UrlNormalizer.normalize(url);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(Component.text(Messages.ERROR_MALFORMED_URL));
            return Command.SINGLE_SUCCESS;
        }

        QuotaService quota = resolveQuotaService(plugin, imageStore, fallbackQuotaService);

        // Paso 2: Slot disponible (Spec 003 Sección 4, F11.8, F11.10).
        // Sin slot, se corta aquí: no se comprueba saldo, no se descarga nada, no se cobra nada.
        // Regla 2.3 (URL que ya posees) se evalúa aquí: canAcquireImage retorna true si ya la posee (F11.12).
        if (quota != null) {
            int maxSlots = quota.getMaxSlots(player);
            if (maxSlots == 0 && !quota.isOwnerOfUrl(player.getUniqueId(), normalizedUrl)) {
                player.sendMessage(Component.text(Messages.QUOTA_DEFAULT_ZERO));
                return Command.SINGLE_SUCCESS;
            }
            if (!quota.canAcquireImage(player, normalizedUrl)) {
                int used = quota.getUsedSlots(player.getUniqueId());
                player.sendMessage(Component.text(Messages.quotaFullRequest(used, maxSlots)));
                return Command.SINGLE_SUCCESS;
            }
        }

        int effectiveMaxFrames = (quota != null)
            ? quota.getEffectiveMaxFrames(player)
            : (plugin != null ? plugin.getPluginConfig().getMaxFrames() : FraMCitConfig.defaults().getMaxFrames());

        EconomyService eco = (plugin != null && plugin.getEconomyService() != null)
            ? plugin.getEconomyService()
            : (fallbackEconomyService != null ? fallbackEconomyService : NoOpEconomyService.INSTANCE);

        // Paso 3: Acierto de cache (F11.11). El recuento de frames se conoce sin descargar nada.
        if (imageStore != null) {
            ImageIndexEntry cached = imageStore.findByNormalizedUrl(normalizedUrl);
            if (cached != null && !cached.isOrphan()) {
                // Comprobación de cupo de frames antes de descargar y antes de mirar saldo
                if (cached.getFrameCount() > effectiveMaxFrames) {
                    player.sendMessage(Component.text(Messages.quotaTooManyFrames(cached.getFrameCount(), effectiveMaxFrames)));
                    return Command.SINGLE_SUCCESS;
                }

                MapView existingMap = null;
                try {
                    existingMap = Bukkit.getMap(cached.getMapId());
                } catch (Throwable ignored) {}

                if (existingMap != null) {
                    // Paso 4: Saldo suficiente (spec 002)
                    EconomyCheckResult checkResult = eco.checkCanPay(player);
                    if (checkResult instanceof EconomyCheckResult.InsufficientFunds inf) {
                        player.sendMessage(Component.text(Messages.insufficientFunds(inf.formattedPrice(), inf.formattedBalance())));
                        return Command.SINGLE_SUCCESS;
                    }

                    deliverCachedMap(plugin, eco, quota, player, url, cached, existingMap, imageStore);
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

        // Paso 4: Saldo suficiente antes de descargar (Spec 002, F10.7)
        EconomyCheckResult checkResult = eco.checkCanPay(player);
        if (checkResult instanceof EconomyCheckResult.InsufficientFunds inf) {
            player.sendMessage(Component.text(Messages.insufficientFunds(inf.formattedPrice(), inf.formattedBalance())));
            return Command.SINGLE_SUCCESS;
        }

        // F4.9: Mensaje inmediato de "Descargando imagen..." solo en caso de cache miss
        player.sendMessage(Component.text(Messages.DOWNLOADING));

        // Paso 5: Descarga y decodificación con effectiveMaxFrames resuelto en hilo principal (F11.8)
        if (imageService != null) {
            imageService.process(url, effectiveMaxFrames)
                .thenAccept(result -> {
                    // Salto explícito de vuelta al hilo principal
                    if (plugin != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            handleResult(plugin, eco, quota, player, url, normalizedUrl, result, imageStore);
                        });
                    } else {
                        handleResult(null, eco, quota, player, url, normalizedUrl, result, imageStore);
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
        return handleList(sender, null, page, imageStore, null);
    }

    /**
     * Maneja el subcomando /imagen listar [jugador] [pagina] (F7.8, F11.18).
     */
    public static int handleList(
        CommandSender sender,
        String targetPlayerName,
        int page,
        ImageStore imageStore,
        QuotaService fallbackQuotaService
    ) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (imageStore == null) {
            sender.sendMessage(Component.text(Messages.ADMIN_LIST_EMPTY));
            return Command.SINGLE_SUCCESS;
        }

        List<ImageIndexEntry> entries;
        String quotaHeaderExtra = "";

        if (targetPlayerName != null && !targetPlayerName.isBlank()) {
            Player onlinePlayer = findOnlinePlayer(targetPlayerName);
            UUID targetUuid = resolveTargetUuid(targetPlayerName, imageStore);

            final UUID finalUuid = targetUuid;
            entries = imageStore.getIndexManager().getAllEntries().stream()
                .filter(e -> (finalUuid != null && e.hasOwner(finalUuid)) || (e.getAuthorName() != null && e.getAuthorName().equalsIgnoreCase(targetPlayerName)))
                .toList();

            if (entries.isEmpty()) {
                sender.sendMessage(Component.text(Messages.ADMIN_LIST_EMPTY));
                return Command.SINGLE_SUCCESS;
            }

            QuotaService quota = resolveQuotaService(null, imageStore, fallbackQuotaService);
            if (quota != null && finalUuid != null) {
                int used = quota.getUsedSlots(finalUuid);
                String maxStr = (onlinePlayer != null)
                    ? (quota.isSlotsUnlimited(onlinePlayer) ? "ilimitado" : String.valueOf(quota.getMaxSlots(onlinePlayer)))
                    : "?";
                quotaHeaderExtra = String.format(" (Cupo: %d/%s)", used, maxStr);
            }
        } else {
            entries = new ArrayList<>(imageStore.getIndexManager().getAllEntries());
        }

        if (entries.isEmpty()) {
            sender.sendMessage(Component.text(Messages.ADMIN_LIST_EMPTY));
            return Command.SINGLE_SUCCESS;
        }

        List<ImageIndexEntry> sortedEntries = new ArrayList<>(entries);
        sortedEntries.sort(Comparator.comparingInt(ImageIndexEntry::getMapId));

        int totalEntries = sortedEntries.size();
        int totalPages = (int) Math.ceil((double) totalEntries / ITEMS_PER_PAGE);

        if (page < 1 || page > totalPages) {
            sender.sendMessage(Component.text(
                Messages.ADMIN_LIST_PAGE_OUT_OF_RANGE.replace("{total_pages}", String.valueOf(totalPages))
            ));
            return Command.SINGLE_SUCCESS;
        }

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, totalEntries);

        if (targetPlayerName != null && !targetPlayerName.isBlank()) {
            sender.sendMessage(Component.text(String.format("--- Imágenes de %s%s (Página %d/%d) ---", targetPlayerName, quotaHeaderExtra, page, totalPages)));
        } else {
            sender.sendMessage(Component.text(
                Messages.ADMIN_LIST_HEADER
                    .replace("{page}", String.valueOf(page))
                    .replace("{total_pages}", String.valueOf(totalPages))
            ));
        }

        for (int i = startIndex; i < endIndex; i++) {
            ImageIndexEntry e = sortedEntries.get(i);
            String line = Messages.ADMIN_LIST_ITEM
                .replace("{id}", String.valueOf(e.getMapId()))
                .replace("{author}", e.getAuthorName() != null ? e.getAuthorName() : "Desconocido")
                .replace("{date}", e.getCreatedAt() != null ? e.getCreatedAt() : "-")
                .replace("{url}", e.getOriginalUrl());
            sender.sendMessage(Component.text(line));
        }

        if (page < totalPages) {
            if (targetPlayerName != null && !targetPlayerName.isBlank()) {
                sender.sendMessage(Component.text(String.format("Usa /imagen listar %s %d para ver más.", targetPlayerName, page + 1)));
            } else {
                sender.sendMessage(Component.text(
                    Messages.ADMIN_LIST_FOOTER
                        .replace("{next_page}", String.valueOf(page + 1))
                        .replace("{page}", String.valueOf(page + 1))
                ));
            }
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen mias (F11.15).
     */
    public static int handleMias(
        CommandSender sender,
        FraMCitPlugin plugin,
        ImageStore imageStore,
        QuotaService fallbackQuotaService
    ) {
        if (!sender.hasPermission("framcit.imagen")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(Messages.ONLY_PLAYER));
            return Command.SINGLE_SUCCESS;
        }

        QuotaService quota = resolveQuotaService(plugin, imageStore, fallbackQuotaService);
        if (quota == null) {
            player.sendMessage(Component.text(Messages.QUOTA_MY_LIST_EMPTY));
            return Command.SINGLE_SUCCESS;
        }

        List<ImageIndexEntry> owned = quota.getOwnedImages(player.getUniqueId());
        if (owned.isEmpty()) {
            player.sendMessage(Component.text(Messages.QUOTA_MY_LIST_EMPTY));
            return Command.SINGLE_SUCCESS;
        }

        List<ImageIndexEntry> sortedOwned = new ArrayList<>(owned);
        sortedOwned.sort(Comparator.comparingInt(ImageIndexEntry::getMapId));

        int used = quota.getUsedSlots(player.getUniqueId());
        String maxStr = quota.isSlotsUnlimited(player) ? "ilimitado" : String.valueOf(quota.getMaxSlots(player));

        player.sendMessage(Component.text(String.format("--- Mis imágenes (Usados %d de %s) ---", used, maxStr)));

        for (ImageIndexEntry entry : sortedOwned) {
            String tipo = entry.getFrameCount() > 1 ? ("animada (" + entry.getFrameCount() + " frames)") : "estática";
            String date = entry.getCreatedAt() != null ? entry.getCreatedAt() : "-";
            player.sendMessage(Component.text(String.format("#%d | %s | %s | %s", entry.getMapId(), tipo, date, entry.getOriginalUrl())));
        }

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen borrar <id> [confirmar] (F11.16, F11.17).
     */
    public static int handleBorrar(
        CommandSender sender,
        int id,
        boolean confirmed,
        FraMCitPlugin plugin,
        ImageStore imageStore,
        QuotaService fallbackQuotaService
    ) {
        if (!sender.hasPermission("framcit.imagen")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(Messages.ONLY_PLAYER));
            return Command.SINGLE_SUCCESS;
        }

        if (imageStore == null) {
            player.sendMessage(Component.text(Messages.quotaIdNotFound(id)));
            return Command.SINGLE_SUCCESS;
        }

        ImageIndexEntry entry = imageStore.findByMapId(id);
        if (entry == null) {
            player.sendMessage(Component.text(Messages.quotaIdNotFound(id)));
            return Command.SINGLE_SUCCESS;
        }

        if (!entry.hasOwner(player.getUniqueId())) {
            player.sendMessage(Component.text(Messages.quotaIdNotOwned(id)));
            return Command.SINGLE_SUCCESS;
        }

        QuotaService quota = resolveQuotaService(plugin, imageStore, fallbackQuotaService);
        boolean shared = (quota != null) && quota.hasOtherOwners(id, player.getUniqueId());

        if (!confirmed) {
            if (shared) {
                player.sendMessage(Component.text(Messages.quotaDeleteConfirmShared(id)));
            } else {
                player.sendMessage(Component.text(Messages.quotaDeleteConfirm(id)));
            }
            return Command.SINGLE_SUCCESS;
        }

        // Ejecutar liberación de cupo
        if (quota != null) {
            quota.removeOwnership(id, player.getUniqueId());
        } else {
            entry.removeOwner(player.getUniqueId());
            imageStore.saveIndex();
        }

        if (!shared) {
            // Último poseedor: borrar archivo .fmc, desalojar de cache LRU y quitar de index.json
            imageStore.getLruCache().remove(id);
            Path fmcPath = imageStore.getImagesFolder().resolve(id + ".fmc");
            try {
                Files.deleteIfExists(fmcPath);
            } catch (Exception ignored) {}

            imageStore.getIndexManager().removeEntry(id);
            imageStore.saveIndex();

            if (plugin != null && plugin.getAnimationManager() != null) {
                plugin.getAnimationManager().removeMap(id);
            }

            try {
                MapView mapView = Bukkit.getMap(id);
                if (mapView != null) {
                    for (MapRenderer r : mapView.getRenderers()) {
                        mapView.removeRenderer(r);
                    }
                }
            } catch (Throwable ignored) {}
        }

        int used = (quota != null) ? quota.getUsedSlots(player.getUniqueId()) : 0;
        int max = (quota != null) ? quota.getMaxSlots(player) : 0;
        player.sendMessage(Component.text(Messages.quotaDeleted(id, used, max)));
        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen uso [jugador] (F11.19).
     */
    public static int handleUso(
        CommandSender sender,
        String targetPlayerName,
        FraMCitPlugin plugin,
        ImageStore imageStore,
        QuotaService fallbackQuotaService
    ) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (imageStore == null) {
            sender.sendMessage(Component.text("No hay datos de almacenamiento disponibles."));
            return Command.SINGLE_SUCCESS;
        }

        List<ImageIndexEntry> entries;
        UUID targetUuid = null;
        if (targetPlayerName != null && !targetPlayerName.isBlank()) {
            targetUuid = resolveTargetUuid(targetPlayerName, imageStore);
            final UUID fUuid = targetUuid;
            entries = imageStore.getIndexManager().getAllEntries().stream()
                .filter(e -> (fUuid != null && e.hasOwner(fUuid)) || (e.getAuthorName() != null && e.getAuthorName().equalsIgnoreCase(targetPlayerName)))
                .toList();
        } else {
            entries = new ArrayList<>(imageStore.getIndexManager().getAllEntries());
        }

        int totalImages = entries.size();
        int totalFrames = entries.stream().mapToInt(ImageIndexEntry::getFrameCount).sum();

        // Medir espacio real ocupado en disco por los .fmc
        long diskBytes = 0;
        for (ImageIndexEntry e : entries) {
            Path fmc = imageStore.getImagesFolder().resolve(e.getMapId() + ".fmc");
            try {
                if (Files.exists(fmc)) {
                    diskBytes += Files.size(fmc);
                }
            } catch (Exception ignored) {}
        }

        // Medir memoria ocupada ahora mismo por el cache LRU
        long memBytes = 0;
        int loadedCount = 0;
        if (targetPlayerName != null && !targetPlayerName.isBlank()) {
            for (ImageIndexEntry e : entries) {
                if (imageStore.getLruCache().containsKey(e.getMapId())) {
                    loadedCount++;
                    memBytes += imageStore.getLruCache().getEntryBytes(e.getMapId());
                }
            }
        } else {
            loadedCount = imageStore.getLruCache().size();
            memBytes = imageStore.getLruCache().getCurrentBytes();
        }
        int diskOnlyCount = totalImages - loadedCount;

        // Contar cuántas imágenes están animándose en este momento
        long animatingCount = 0;
        if (plugin != null && plugin.getAnimationManager() != null) {
            final List<Integer> entryMapIds = entries.stream().map(ImageIndexEntry::getMapId).toList();
            animatingCount = plugin.getAnimationManager().getAllStates().stream()
                .filter(PlayerMapState::isGazing)
                .map(PlayerMapState::getMapId)
                .filter(entryMapIds::contains)
                .distinct()
                .count();
        }

        String targetHeader = (targetPlayerName != null && !targetPlayerName.isBlank())
            ? (" (" + targetPlayerName + ")")
            : " (Global)";

        sender.sendMessage(Component.text("--- Consumo de recursos de FraMCit" + targetHeader + " ---"));
        sender.sendMessage(Component.text(String.format("Imágenes: %d | Frames totales: %d", totalImages, totalFrames)));
        sender.sendMessage(Component.text(String.format("Disco: %.2f MB (%d bytes en archivos .fmc)", (double) diskBytes / (1024 * 1024), diskBytes)));
        sender.sendMessage(Component.text(String.format("Memoria activa en LRU: %.2f MB (%d bytes) en %d imágenes cargadas (%d solo en disco)", (double) memBytes / (1024 * 1024), memBytes, loadedCount, diskOnlyCount)));
        sender.sendMessage(Component.text(String.format("Animaciones activas: %d imágenes (consumo de tick)", animatingCount)));

        return Command.SINGLE_SUCCESS;
    }

    /**
     * Maneja el subcomando /imagen purgar-jugador <jugador> [confirmar] (F11.20).
     */
    public static int handlePurgarJugador(
        CommandSender sender,
        String targetPlayerName,
        boolean confirmed,
        FraMCitPlugin plugin,
        ImageStore imageStore,
        QuotaService fallbackQuotaService
    ) {
        if (!sender.hasPermission("framcit.admin")) {
            sender.sendMessage(Component.text(Messages.NO_PERMISSION));
            return Command.SINGLE_SUCCESS;
        }

        if (imageStore == null) {
            sender.sendMessage(Component.text(Messages.ADMIN_PURGE_NOT_FOUND));
            return Command.SINGLE_SUCCESS;
        }

        UUID targetUuid = resolveTargetUuid(targetPlayerName, imageStore);

        final UUID fUuid = targetUuid;
        List<ImageIndexEntry> owned = (fUuid != null)
            ? imageStore.getIndexManager().getAllEntries().stream().filter(e -> e.hasOwner(fUuid)).toList()
            : List.of();

        if (owned.isEmpty()) {
            sender.sendMessage(Component.text(String.format("El jugador %s no posee ninguna imagen.", targetPlayerName)));
            return Command.SINGLE_SUCCESS;
        }

        QuotaService quota = resolveQuotaService(plugin, imageStore, fallbackQuotaService);

        if (!confirmed) {
            int willDeleteFiles = 0;
            long willFreeBytes = 0;
            int sharedRetained = 0;
            for (ImageIndexEntry e : owned) {
                boolean hasOthers = (quota != null) && quota.hasOtherOwners(e.getMapId(), fUuid);
                if (hasOthers) {
                    sharedRetained++;
                } else {
                    willDeleteFiles++;
                    Path fmc = imageStore.getImagesFolder().resolve(e.getMapId() + ".fmc");
                    try {
                        if (Files.exists(fmc)) willFreeBytes += Files.size(fmc);
                    } catch (Exception ignored) {}
                }
            }

            sender.sendMessage(Component.text(String.format(
                "El jugador %s posee %d imágenes. Se borrarían %d archivos de disco liberando %.2f MB (%d se conservarán por posesión compartida). Usa /imagen purgar-jugador %s confirmar para proceder.",
                targetPlayerName,
                owned.size(),
                willDeleteFiles,
                (double) willFreeBytes / (1024 * 1024),
                sharedRetained,
                targetPlayerName
            )));
            return Command.SINGLE_SUCCESS;
        }

        // Ejecutar purga de imágenes del jugador
        int purgedCount = owned.size();
        for (ImageIndexEntry e : owned) {
            int mapId = e.getMapId();
            boolean hasOthers = (quota != null) && quota.hasOtherOwners(mapId, fUuid);

            if (quota != null) {
                quota.removeOwnership(mapId, fUuid);
            } else {
                e.removeOwner(fUuid);
            }

            if (!hasOthers) {
                imageStore.getLruCache().remove(mapId);
                Path fmcPath = imageStore.getImagesFolder().resolve(mapId + ".fmc");
                try {
                    Files.deleteIfExists(fmcPath);
                } catch (Exception ignored) {}

                imageStore.getIndexManager().removeEntry(mapId);

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
            }
        }

        imageStore.saveIndex();

        // F11.20: Registro en el log: quién lo ejecutó, sobre quién y cuántas imágenes borró
        if (plugin != null) {
            plugin.getLogger().info(String.format(
                "Purgadas %d imágenes del jugador %s (%s) por el administrador %s.",
                purgedCount,
                targetPlayerName,
                fUuid != null ? fUuid.toString() : "desconocido",
                sender.getName()
            ));
        }

        sender.sendMessage(Component.text(String.format("Se purgaron %d imágenes del jugador %s.", purgedCount, targetPlayerName)));
        return Command.SINGLE_SUCCESS;
    }

    private static Player findOnlinePlayer(String name) {
        if (Bukkit.getServer() == null || name == null) return null;
        try {
            return Bukkit.getPlayerExact(name);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID findOfflinePlayerUuid(String name) {
        if (Bukkit.getServer() == null || name == null) return null;
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(name);
            return (op != null) ? op.getUniqueId() : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static UUID resolveTargetUuid(String targetPlayerName, ImageStore imageStore) {
        if (targetPlayerName == null || targetPlayerName.isBlank()) return null;
        Player online = findOnlinePlayer(targetPlayerName);
        if (online != null) return online.getUniqueId();
        if (imageStore != null && imageStore.getIndexManager() != null) {
            for (ImageIndexEntry e : imageStore.getIndexManager().getAllEntries()) {
                if (targetPlayerName.equalsIgnoreCase(e.getAuthorName())) {
                    try {
                        return UUID.fromString(e.getAuthorUuid());
                    } catch (Exception ignored) {}
                }
            }
        }
        return findOfflinePlayerUuid(targetPlayerName);
    }

    private static QuotaService resolveQuotaService(
        FraMCitPlugin plugin,
        ImageStore imageStore,
        QuotaService fallbackQuotaService
    ) {
        if (plugin != null && plugin.getQuotaService() != null) {
            return plugin.getQuotaService();
        }
        if (fallbackQuotaService != null) {
            return fallbackQuotaService;
        }
        if (imageStore != null) {
            return new QuotaService(imageStore, () -> plugin != null ? plugin.getPluginConfig() : FraMCitConfig.defaults());
        }
        return null;
    }

    /**
     * Entrega un mapa ya existente desde la cache sin crear un nuevo MapView ni encolar descargas (F5.5, F11.11, F11.12).
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
        deliverCachedMap(plugin, eco, null, player, rawUrl, cached, mapView, imageStore);
    }

    private static void deliverCachedMap(
        FraMCitPlugin plugin,
        EconomyService eco,
        QuotaService quota,
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

        // Registrar posesión compartida o reiterada en el índice (F11.5, F11.11, F11.12)
        if (quota != null) {
            quota.addOwnership(cached.getMapId(), player.getUniqueId());
        } else {
            cached.addOwner(player.getUniqueId());
            if (imageStore != null) {
                imageStore.saveIndex();
            }
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

    public static void handleResult(
        FraMCitPlugin plugin,
        EconomyService eco,
        Player player,
        String rawUrl,
        String normalizedUrl,
        ImageProcessResult result,
        ImageStore imageStore
    ) {
        QuotaService quota = (plugin != null && plugin.getQuotaService() != null) ? plugin.getQuotaService() : null;
        handleResult(plugin, eco, quota, player, rawUrl, normalizedUrl, result, imageStore);
    }

    /**
     * Procesa el resultado de la imagen en el hilo principal de Bukkit (F10.8, F10.9, F10.10, F10.14, F11.13, F11.14).
     */
    public static void handleResult(
        FraMCitPlugin plugin,
        EconomyService eco,
        QuotaService quota,
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
                if (failure.cause() instanceof TooManyFramesException tmfe) {
                    player.sendMessage(Component.text(Messages.quotaTooManyFrames(tmfe.getFrameCount(), tmfe.getLimit())));
                } else {
                    player.sendMessage(Component.text(failure.userMessage()));
                }
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

        // Paso 8: Slot disponible otra vez, justo antes de entregar (Spec 003 Sección 4, F11.13).
        // Si el jugador llenó su cupo mientras se descargaba, no se entrega el mapa, NO SE COBRA, y se le explica por qué.
        if (quota != null && !quota.canAcquireImage(player, normalizedUrl)) {
            player.sendMessage(Component.text(Messages.QUOTA_FULL_DELIVERY));
            if (plugin != null) {
                plugin.getLogger().info(String.format("Entrega cancelada para %s (%s): cupo lleno al entregar.", player.getName(), rawUrl));
            }
            return;
        }

        // Paso 9: Cobrar AL ENTREGAR el mapa, nunca antes (Spec 002, F10.8)
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
            // Paso 10: Entrega del item (F4.3 - F4.6)
            MapView mapView = Bukkit.createMap(player.getWorld());
            for (MapRenderer r : mapView.getRenderers()) {
                mapView.removeRenderer(r);
            }
            int mapId = mapView.getId();
            boolean contextual = success.image().isAnimated();
            mapView.addRenderer(new FraMCitRenderer(mapId, imageStore, success.image(), contextual));
            mapView.setTrackingPosition(false);
            mapView.setUnlimitedTracking(false);

            ItemStack item = MapItemBuilder.buildMapItem(mapView, success.finalUri(), rawUrl, player);

            HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack dropped : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropped);
                }
                player.sendMessage(Component.text(Messages.INVENTORY_FULL));
            }

            if (!successCharge.isFree()) {
                player.sendMessage(Component.text(Messages.chargeSuccess(successCharge.formattedAmount())));
            }

            if (success.image().isAnimated()) {
                player.sendMessage(Component.text(Messages.animatedSuccess(success.image().getFrameCount())));
            } else {
                player.sendMessage(Component.text(Messages.STATIC_SUCCESS));
            }

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

            // Guardar imagen en .fmc y actualizar index.json con el jugador como propietario (F11.5, F11.7)
            if (imageStore != null) {
                String norm = (normalizedUrl != null) ? normalizedUrl : UrlNormalizer.normalize(rawUrl);
                List<Integer> delays = success.image().getFrames().stream()
                    .map(ConvertedFrame::getDelayMs)
                    .toList();

                List<String> owners = new ArrayList<>();
                owners.add(player.getUniqueId().toString());

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
                    false,
                    owners
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
