package com.dasannn.framcit;

import com.dasannn.framcit.command.ImageCommand;
import com.dasannn.framcit.command.Messages;
import com.dasannn.framcit.config.FraMCitConfig;
import com.dasannn.framcit.image.ImageDecoder;
import com.dasannn.framcit.image.TwelveMonkeysRegistrar;
import com.dasannn.framcit.net.DnsCacheChecker;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.net.Downloader;
import com.dasannn.framcit.net.UrlGuard;
import com.dasannn.framcit.render.AnimationManager;
import com.dasannn.framcit.render.AnimationScheduler;
import com.dasannn.framcit.render.FraMCitAnimationListener;
import com.dasannn.framcit.render.FraMCitRenderer;
import com.dasannn.framcit.render.GazeTracker;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.ImageStore;
import com.dasannn.framcit.economy.EconomyManager;
import com.dasannn.framcit.economy.EconomyService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.dasannn.framcit.quota.QuotaService;

import java.io.File;
import java.time.Duration;

public final class FraMCitPlugin extends JavaPlugin {

    private FraMCitConfig pluginConfig;
    private EconomyService economyService;
    private QuotaService quotaService;
    private DownloadQueue downloadQueue;
    private Downloader downloader;
    private ImageService imageService;
    private ImageStore imageStore;
    private AnimationManager animationManager;
    private GazeTracker gazeTracker;
    private AnimationScheduler animationScheduler;
    private BukkitTask gazeTrackerTask;
    private BukkitTask animationSchedulerTask;

    @Override
    public void onEnable() {
        // F7.10 / SEC R6: Comprobar TTL de DNS de la JVM y alertar si está en 0
        DnsCacheChecker.checkDnsCacheTtl(getLogger());

        // Registrar TwelveMonkeys para soporte de formato WEBP
        TwelveMonkeysRegistrar.register(getLogger());

        // F7.1 y F7.3: Guardar archivos por defecto config.yml y messages.yml si no existen
        saveDefaultConfig();
        File messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            saveResource("messages.yml", false);
        }

        // Cargar configuración y mensajes (F7.1, F7.3, F7.4)
        reloadPluginConfig();
        reloadMessages();

        // Inicializar componentes de red y persistencia con la configuración activa
        this.downloadQueue = new DownloadQueue(pluginConfig.getDownloadThreads(), pluginConfig.getQueueCapacity());
        this.imageStore = new ImageStore(getDataFolder().toPath(), downloadQueue, getLogger(), pluginConfig.getMaxCacheBytes());
        this.imageStore.loadIndex();
        this.imageStore.relinkMaps();

        this.animationManager = new AnimationManager(imageStore, getLogger());
        FraMCitRenderer.setGlobalFrameProvider(animationManager);

        this.gazeTracker = new GazeTracker(animationManager, imageStore);
        this.gazeTracker.setMaxDistance(pluginConfig.getGazeMaxDistance());
        this.animationScheduler = new AnimationScheduler(animationManager, getLogger());

        // Iniciar ciclos periódicos (ARCHITECTURE 5, F6.3, F6.6)
        long gazeTicks = Math.max(1L, pluginConfig.getGazePeriodTicks());
        this.gazeTrackerTask = Bukkit.getScheduler().runTaskTimer(this, gazeTracker, gazeTicks, gazeTicks);
        this.animationSchedulerTask = Bukkit.getScheduler().runTaskTimer(this, animationScheduler, 1L, 1L);

        // Registrar listener de eventos para prevención de fugas de memoria (F6.11)
        Bukkit.getPluginManager().registerEvents(new FraMCitAnimationListener(animationManager), this);

        this.imageService = new ImageService(downloader, downloadQueue);
        this.quotaService = new QuotaService(imageStore, () -> pluginConfig);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            ImageCommand.register(event.registrar(), this, imageService, imageStore);
        });

        getLogger().info("FraMCit habilitado correctamente.");
    }

    /**
     * Recarga la configuración de config.yml aplicando cambios al vuelo (F7.5).
     */
    public void reloadPluginConfig() {
        File configFile = new File(getDataFolder(), "config.yml");
        this.pluginConfig = FraMCitConfig.load(configFile, getLogger());

        // Aplicar límites de decodificación y animación al vuelo
        ImageDecoder.configure(
            pluginConfig.getMaxDimension(),
            pluginConfig.getMaxFrames(),
            pluginConfig.getDefaultDelayMs(),
            pluginConfig.getMinDelayMs()
        );
        AnimationManager.setMinFrameDelayMs(pluginConfig.getMinDelayMs());

        // Actualizar Downloader al vuelo
        this.downloader = new Downloader(
            new UrlGuard(),
            Duration.ofSeconds(pluginConfig.getConnectTimeoutSeconds()),
            Duration.ofSeconds(pluginConfig.getReadTimeoutSeconds()),
            pluginConfig.getMaxDownloadSizeBytes(),
            pluginConfig.getMaxRedirects()
        );

        if (imageService != null) {
            imageService.setDownloader(this.downloader);
        }

        // Actualizar distancia de visión de GazeTracker al vuelo
        if (gazeTracker != null) {
            gazeTracker.setMaxDistance(pluginConfig.getGazeMaxDistance());
        }

        // Actualizar límite de caché LRU en memoria al vuelo
        if (imageStore != null && imageStore.getLruCache() != null) {
            imageStore.getLruCache().setMaxBytes(pluginConfig.getMaxCacheBytes());
        }

        // F10.4, F10.5, F10.10: Resolver servicio de economía al vuelo
        this.economyService = EconomyManager.resolve(this, pluginConfig.getImagePrice(), getLogger());
    }

    /**
     * Recarga los mensajes de messages.yml (F7.3, F7.5).
     */
    public void reloadMessages() {
        File messagesFile = new File(getDataFolder(), "messages.yml");
        Messages.load(messagesFile, getLogger());
    }

    @Override
    public void onDisable() {
        if (gazeTrackerTask != null) {
            gazeTrackerTask.cancel();
        }
        if (animationSchedulerTask != null) {
            animationSchedulerTask.cancel();
        }
        if (animationManager != null) {
            animationManager.close();
        }
        FraMCitRenderer.setGlobalFrameProvider(null);

        if (imageStore != null) {
            imageStore.saveIndex();
        }
        if (downloadQueue != null) {
            downloadQueue.close();
        }
        getLogger().info("FraMCit deshabilitado.");
    }

    public FraMCitConfig getPluginConfig() {
        return pluginConfig;
    }

    public ImageService getImageService() {
        return imageService;
    }

    public ImageStore getImageStore() {
        return imageStore;
    }

    public DownloadQueue getDownloadQueue() {
        return downloadQueue;
    }

    public Downloader getDownloader() {
        return downloader;
    }

    public AnimationManager getAnimationManager() {
        return animationManager;
    }

    public GazeTracker getGazeTracker() {
        return gazeTracker;
    }

    public AnimationScheduler getAnimationScheduler() {
        return animationScheduler;
    }

    public EconomyService getEconomyService() {
        return economyService;
    }

    public QuotaService getQuotaService() {
        return quotaService;
    }
}
