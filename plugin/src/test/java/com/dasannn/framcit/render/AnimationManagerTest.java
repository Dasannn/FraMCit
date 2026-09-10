package com.dasannn.framcit.render;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class AnimationManagerTest {

    private AnimationManager manager;
    private AtomicLong fakeClock;

    @BeforeEach
    void setUp() {
        fakeClock = new AtomicLong(1000L);
        manager = new AnimationManager(null, null);
        manager.setTimeSupplier(fakeClock::get);
    }

    @Test
    @DisplayName("F6.5 & F6.8 - Apartar la vista conserva el frame y al volver a mirar continua en el mismo frame")
    void testGazePreservesFrameIndex() {
        UUID player = UUID.randomUUID();
        int mapId = 1;

        // Comienza a mirar
        manager.updatePlayerGaze(player, mapId, UUID.randomUUID(), "world", 10, 20);
        PlayerMapState state = manager.getState(player, mapId);
        assertNotNull(state);
        assertTrue(state.isGazing());
        assertEquals(0, state.getFrameIndex());

        // Simulamos que avanza al frame 3
        state.setFrameIndex(3);

        // Aparta la vista
        manager.clearPlayerGaze(player);
        assertFalse(state.isGazing());
        assertEquals(3, state.getFrameIndex(), "El frame debe conservarse al apartar la vista");

        // Vuelve a mirar el mismo mapa
        manager.updatePlayerGaze(player, mapId, UUID.randomUUID(), "world", 10, 20);
        assertTrue(state.isGazing());
        assertEquals(3, state.getFrameIndex(), "Al volver a mirar debe seguir exactamente en el frame 3, no reiniciar a cero");
    }

    @Test
    @DisplayName("F6.11 - [CRITICO] Limpieza al desconectar un jugador deja el estado vacio")
    void testPlayerQuitLeavesEmptyState() {
        UUID player1 = UUID.randomUUID();
        UUID player2 = UUID.randomUUID();

        manager.updatePlayerGaze(player1, 1, UUID.randomUUID(), "world", 0, 0);
        manager.updatePlayerGaze(player1, 2, UUID.randomUUID(), "world", 0, 0);
        manager.updatePlayerGaze(player2, 1, UUID.randomUUID(), "world", 0, 0);

        assertEquals(3, manager.getStateCount());

        // Desconexión de player1
        manager.onPlayerQuit(player1);

        // player1 no debe tener ningún estado
        assertNull(manager.getState(player1, 1));
        assertNull(manager.getState(player1, 2));
        assertEquals(1, manager.getStateCount());

        // Desconexión de player2: debe quedar totalmente vacío
        manager.onPlayerQuit(player2);
        assertEquals(0, manager.getStateCount(), "El mapa de estado debe quedar completamente vacío tras desconectar todos los jugadores");
    }

    @Test
    @DisplayName("F6.11 - [CRITICO] Limpieza al descargarse el chunk elimina marcos en ese chunk")
    void testChunkUnloadCleansStates() {
        UUID player = UUID.randomUUID();
        UUID frame1 = UUID.randomUUID();
        UUID frame2 = UUID.randomUUID();

        // frame1 en chunk (5, 5)
        manager.updatePlayerGaze(player, 1, frame1, "world", 5, 5);
        // frame2 en chunk (10, 10)
        manager.updatePlayerGaze(player, 2, frame2, "world", 10, 10);

        assertEquals(2, manager.getStateCount());

        // Se descarga el chunk (5, 5) en "world"
        manager.onChunkUnload("world", 5, 5);

        assertNull(manager.getState(player, 1), "El estado del marco en chunk descargado debe eliminarse");
        assertNotNull(manager.getState(player, 2), "El estado de otros chunks no debe eliminarse");
    }

    @Test
    @DisplayName("F6.11 - [CRITICO] Limpieza al romperse el marco")
    void testFrameBreakCleansState() {
        UUID player = UUID.randomUUID();
        UUID frameId = UUID.randomUUID();

        manager.updatePlayerGaze(player, 1, frameId, "world", 2, 3);
        assertNotNull(manager.getState(player, 1));

        // Se rompe el marco
        manager.onFrameBreak(frameId);

        assertNull(manager.getState(player, 1), "Al romperse el marco, el estado debe eliminarse sin fugas");
        assertEquals(0, manager.getStateCount());
    }

    @Test
    @DisplayName("F6.12 - Desactivacion aislada de un mapa por excepcion no tumba los demas")
    void testDisableMapIsIsolated() {
        UUID player = UUID.randomUUID();

        manager.updatePlayerGaze(player, 1, UUID.randomUUID(), "world", 0, 0);
        manager.updatePlayerGaze(player, 2, UUID.randomUUID(), "world", 0, 0);

        assertFalse(manager.isMapDisabled(1));
        assertFalse(manager.isMapDisabled(2));

        // Desactivar mapa 1 por excepcion
        manager.disableMap(1, new RuntimeException("Simulated error on map 1"));

        assertTrue(manager.isMapDisabled(1));
        assertFalse(manager.isMapDisabled(2), "El mapa 2 no debe verse afectado");

        // El estado del mapa roto se limpia inmediatamente
        assertNull(manager.getState(player, 1));
        assertNotNull(manager.getState(player, 2));

        // Intentos posteriores de mirar el mapa roto son ignorados
        manager.updatePlayerGaze(player, 1, UUID.randomUUID(), "world", 0, 0);
        assertNull(manager.getState(player, 1));
    }

    @Test
    @DisplayName("F6.7 - Tope de 10 fps (minimo 100 ms) y correccion de retardos")
    void testFpsCapAndDelays() {
        // Retardo menor a 100ms se ajusta a 100ms (10 fps maximo)
        assertEquals(100, AnimationManager.getEffectiveDelayMs(0));
        assertEquals(100, AnimationManager.getEffectiveDelayMs(-50));
        assertEquals(100, AnimationManager.getEffectiveDelayMs(20));
        assertEquals(100, AnimationManager.getEffectiveDelayMs(50));
        assertEquals(100, AnimationManager.getEffectiveDelayMs(100));

        // Retardos mayores a 100ms se respetan
        assertEquals(200, AnimationManager.getEffectiveDelayMs(200));
        assertEquals(500, AnimationManager.getEffectiveDelayMs(500));
        assertEquals(1000, AnimationManager.getEffectiveDelayMs(1000));
    }

    @Test
    @DisplayName("ARCHITECTURE 5.1 - Garantia de ancho de banda: un jugador anima como maximo un mapa a la vez")
    void testBandwidthGuaranteeOnlyOneActiveMapPerPlayer() {
        UUID player = UUID.randomUUID();

        // Jugador mira mapa 1
        manager.updatePlayerGaze(player, 1, UUID.randomUUID(), "world", 1, 1);
        assertTrue(manager.getState(player, 1).isGazing());

        // Jugador pasa a mirar mapa 2
        manager.updatePlayerGaze(player, 2, UUID.randomUUID(), "world", 2, 2);

        // Mapa 1 debe quedar inactivo (congelado)
        assertFalse(manager.getState(player, 1).isGazing(), "Mapa anterior debe congelarse");
        // Mapa 2 debe quedar activo
        assertTrue(manager.getState(player, 2).isGazing());
    }
}
