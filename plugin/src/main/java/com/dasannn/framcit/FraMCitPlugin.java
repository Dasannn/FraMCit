package com.dasannn.framcit;

import com.dasannn.framcit.command.ImageCommand;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

public final class FraMCitPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            ImageCommand.register(event.registrar());
        });
        getLogger().info("FraMCit habilitado correctamente.");
    }

    @Override
    public void onDisable() {
        getLogger().info("FraMCit deshabilitado.");
    }
}
