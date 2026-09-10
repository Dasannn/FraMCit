package com.dasannn.framcit;

import com.dasannn.framcit.command.ImageCommand;
import com.dasannn.framcit.image.TwelveMonkeysRegistrar;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.net.Downloader;
import com.dasannn.framcit.render.AnimationManager;
import com.dasannn.framcit.render.AnimationScheduler;
import com.dasannn.framcit.render.FraMCitAnimationListener;
import com.dasannn.framcit.render.FraMCitRenderer;
import com.dasannn.framcit.render.GazeTracker;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.ImageStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FraMCitPlugin extends JavaPlugin {

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
        TwelveMonkeysRegistrar.register(getLogger());

        this.downloader = new Downloader();
        this.downloadQueue = new DownloadQueue();
        this.imageStore = new ImageStore(getDataFolder(), downloadQueue, getLogger());
        this.imageStore.loadIndex();
        this.imageStore.relinkMaps();

        this.animationManager = new AnimationManager(imageStore, getLogger());
        FraMCitRenderer.setGlobalFrameProvider(animationManager);

        this.gazeTracker = new GazeTracker(animationManager, imageStore);
        this.animationScheduler = new AnimationScheduler(animationManager, getLogger());

        // Iniciar ciclos periódicos (ARCHITECTURE 5, F6.3, F6.6)
        this.gazeTrackerTask = Bukkit.getScheduler().runTaskTimer(this, gazeTracker, 5L, 5L);
        this.animationSchedulerTask = Bukkit.getScheduler().runTaskTimer(this, animationScheduler, 1L, 1L);

        // Registrar listener de eventos para prevención de fugas de memoria (F6.11)
        Bukkit.getPluginManager().registerEvents(new FraMCitAnimationListener(animationManager), this);

        this.imageService = new ImageService(downloader, downloadQueue);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            ImageCommand.register(event.registrar(), this, imageService, imageStore);
        });

        getLogger().info("FraMCit habilitado correctamente.");
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
}
