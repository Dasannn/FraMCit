package com.dasannn.framcit;

import com.dasannn.framcit.command.ImageCommand;
import com.dasannn.framcit.image.TwelveMonkeysRegistrar;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.net.Downloader;
import com.dasannn.framcit.service.ImageService;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class FraMCitPlugin extends JavaPlugin {

    private DownloadQueue downloadQueue;
    private Downloader downloader;
    private ImageService imageService;

    @Override
    public void onEnable() {
        TwelveMonkeysRegistrar.register(getLogger());

        this.downloader = new Downloader();
        this.downloadQueue = new DownloadQueue();
        this.imageService = new ImageService(downloader, downloadQueue);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            ImageCommand.register(event.registrar(), this, imageService);
        });

        getLogger().info("FraMCit habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        if (downloadQueue != null) {
            downloadQueue.close();
        }
        getLogger().info("FraMCit deshabilitado.");
    }

    public ImageService getImageService() {
        return imageService;
    }

    public DownloadQueue getDownloadQueue() {
        return downloadQueue;
    }

    public Downloader getDownloader() {
        return downloader;
    }
}
