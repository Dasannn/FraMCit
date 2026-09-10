package com.dasannn.framcit;

import com.dasannn.framcit.command.ImageCommand;
import com.dasannn.framcit.image.TwelveMonkeysRegistrar;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.net.Downloader;
import com.dasannn.framcit.service.ImageService;
import com.dasannn.framcit.store.ImageStore;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class FraMCitPlugin extends JavaPlugin {

    private DownloadQueue downloadQueue;
    private Downloader downloader;
    private ImageService imageService;
    private ImageStore imageStore;

    @Override
    public void onEnable() {
        TwelveMonkeysRegistrar.register(getLogger());

        this.downloader = new Downloader();
        this.downloadQueue = new DownloadQueue();
        this.imageStore = new ImageStore(getDataFolder(), downloadQueue, getLogger());
        this.imageStore.loadIndex();
        this.imageStore.relinkMaps();

        this.imageService = new ImageService(downloader, downloadQueue);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            ImageCommand.register(event.registrar(), this, imageService, imageStore);
        });

        getLogger().info("FraMCit habilitado correctamente.");
    }

    @Override
    public void onDisable() {
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
}
