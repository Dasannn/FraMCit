package com.dasannn.framcit.render;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.map.MapView;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.IntFunction;
import java.util.function.LongSupplier;
import java.util.logging.Logger;

/**
 * Planificador de animaciones de mapas (F6.6, F6.7, F6.9, F6.12, F12.5, F12.6, F12.9).
 *
 * Corre cada tick. Recorre únicamente el índice de estados activos que entrega el manager,
 * avanza los pares cuyo retardo ya venció y llama a player.sendMap(view) exclusivamente a
 * ese jugador (ancho de banda y trabajo por tick proporcionales a los estados activos).
 * Bucle infinito ignorando repeticiones declaradas del GIF (F6.9).
 * Una excepción dentro del ciclo desactiva solo ese mapa, nunca el ciclo entero (F6.12).
 * Respeta la pausa por mirada prolongada (F12.5): si el par supera el umbral, congela el frame y omite el envío.
 */
public class AnimationScheduler implements Runnable {

    @FunctionalInterface
    public interface GazePauseChecker {
        boolean isGazePaused(UUID playerUuid, int mapId, UUID frameUuid);
    }

    private final AnimationManager manager;
    private final IntFunction<MapView> mapLookup;
    private final Function<UUID, Player> playerLookup;
    private final LongSupplier timeSupplier;
    private final GazePauseChecker gazePauseChecker;
    private final Logger logger;

    public AnimationScheduler(AnimationManager manager, Logger logger) {
        this(manager, Bukkit::getMap, Bukkit::getPlayer, System::currentTimeMillis, null, logger);
    }

    public AnimationScheduler(AnimationManager manager, GazePauseChecker gazePauseChecker, Logger logger) {
        this(manager, Bukkit::getMap, Bukkit::getPlayer, System::currentTimeMillis, gazePauseChecker, logger);
    }

    public AnimationScheduler(
        AnimationManager manager,
        IntFunction<MapView> mapLookup,
        Function<UUID, Player> playerLookup,
        LongSupplier timeSupplier,
        Logger logger
    ) {
        this(manager, mapLookup, playerLookup, timeSupplier, null, logger);
    }

    public AnimationScheduler(
        AnimationManager manager,
        IntFunction<MapView> mapLookup,
        Function<UUID, Player> playerLookup,
        LongSupplier timeSupplier,
        GazePauseChecker gazePauseChecker,
        Logger logger
    ) {
        this.manager = Objects.requireNonNull(manager, "manager cannot be null");
        this.mapLookup = Objects.requireNonNull(mapLookup, "mapLookup cannot be null");
        this.playerLookup = Objects.requireNonNull(playerLookup, "playerLookup cannot be null");
        this.timeSupplier = Objects.requireNonNull(timeSupplier, "timeSupplier cannot be null");
        this.gazePauseChecker = gazePauseChecker;
        this.logger = logger;
    }

    @Override
    public void run() {
        tick(timeSupplier.getAsLong());
    }

    /**
     * Ejecuta un tick del planificador a partir de una marca de tiempo dada (útil para pruebas unitarias).
     */
    public void tick(long currentTimeMs) {
        for (PlayerMapState state : manager.getActiveStates()) {
            if (!state.isGazing()) {
                continue;
            }

            int mapId = state.getMapId();
            if (manager.isMapDisabled(mapId)) {
                continue;
            }

            UUID playerUuid = state.getPlayerUuid();
            Player player = playerLookup.apply(playerUuid);
            if (player == null || !player.isOnline()) {
                manager.onPlayerQuit(playerUuid);
                continue;
            }

            // F12.5, F12.6, F12.8, F12.9: Pausa por mirada prolongada
            if (gazePauseChecker != null && gazePauseChecker.isGazePaused(playerUuid, mapId, state.getFrameUuid())) {
                continue;
            }

            // F6.7: Retardo no vencido: no avanza el frame
            if (currentTimeMs < state.getNextAdvanceTimeMs()) {
                continue;
            }

            try {
                int totalFrames = manager.getFrameCount(mapId);
                if (totalFrames <= 1) {
                    continue;
                }

                // F6.9: Bucle infinito al llegar al final
                int currentFrame = state.getFrameIndex();
                int nextFrame = (currentFrame + 1) % totalFrames;
                state.setFrameIndex(nextFrame);

                int rawDelay = manager.getFrameDelayMs(mapId, nextFrame);
                int effectiveDelay = AnimationManager.getEffectiveDelayMs(rawDelay);
                state.setNextAdvanceTimeMs(currentTimeMs + effectiveDelay);

                // F6.6: sendMap solo a ese jugador
                MapView mapView = mapLookup.apply(mapId);
                if (mapView != null) {
                    player.sendMap(mapView);
                }
            } catch (Throwable t) {
                // F6.12: Desactivar solo este mapa sin tumbar el ciclo entero
                manager.disableMap(mapId, t);
            }
        }
    }
}
