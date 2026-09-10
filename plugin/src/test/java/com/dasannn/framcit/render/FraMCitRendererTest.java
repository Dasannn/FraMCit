package com.dasannn.framcit.render;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import org.bukkit.entity.Player;
import org.bukkit.map.MapCanvas;
import org.bukkit.map.MapCursorCollection;
import org.bukkit.map.MapFont;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.awt.Image;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FraMCitRendererTest {

    static class StubMapCanvas implements MapCanvas {
        final byte[] buffer = new byte[128 * 128];
        int setPixelCalls = 0;

        @Override
        public void setPixel(int x, int y, byte data) {
            buffer[y * 128 + x] = data;
            setPixelCalls++;
        }

        @Override public byte getPixel(int x, int y) { return buffer[y * 128 + x]; }
        @Override public byte getBasePixel(int x, int y) { return buffer[y * 128 + x]; }
        @Override public void setPixelColor(int x, int y, Color color) {}
        @Override public Color getPixelColor(int x, int y) { return Color.BLACK; }
        @Override public Color getBasePixelColor(int x, int y) { return Color.BLACK; }
        @Override public MapView getMapView() { return null; }
        @Override public MapCursorCollection getCursors() { return null; }
        @Override public void setCursors(MapCursorCollection cursors) {}
        @Override public void drawImage(int x, int y, Image image) {}
        @Override public void drawText(int x, int y, MapFont font, String text) {}
    }

    private Player createMockPlayer(UUID uuid, String name) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getUniqueId".equals(mName)) return uuid;
                if ("getName".equals(mName)) return name;
                if ("isOnline".equals(mName)) return true;
                return null;
            }
        );
    }

    @Test
    @DisplayName("F4.3 - Version estatica vuelca 16384 bytes una sola vez y no sobreescribe en ticks posteriores")
    void testStaticRenderingOnlyOnce() {
        byte[] frameBytes = new byte[16384];
        Arrays.fill(frameBytes, (byte) 42);
        ConvertedFrame frame = new ConvertedFrame(frameBytes, 0);
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG, List.of(frame));

        FraMCitRenderer renderer = new FraMCitRenderer(image);
        assertFalse(renderer.isContextual());
        assertFalse(renderer.isStaticRendered());

        StubMapCanvas canvas = new StubMapCanvas();

        // 1er render: debe volcar los 16384 bytes
        renderer.render(null, canvas, null);
        assertTrue(renderer.isStaticRendered());
        assertEquals(16384, canvas.setPixelCalls);
        assertEquals(42, canvas.getPixel(0, 0));
        assertEquals(42, canvas.getPixel(127, 127));

        // 2do render: no debe volver a pintar
        renderer.render(null, canvas, null);
        assertEquals(16384, canvas.setPixelCalls, "No debe pintar de nuevo en llamadas posteriores");
    }

    @Test
    @DisplayName("F6.1 - Dos jugadores sobre el mismo mapa contextual ven sus frames independientes")
    void testIndependentFramesPerPlayer() {
        byte[] frame0Bytes = new byte[16384];
        Arrays.fill(frame0Bytes, (byte) 11);
        byte[] frame1Bytes = new byte[16384];
        Arrays.fill(frame1Bytes, (byte) 22);

        ConvertedFrame frame0 = new ConvertedFrame(frame0Bytes, 100);
        ConvertedFrame frame1 = new ConvertedFrame(frame1Bytes, 100);
        ConvertedImage image = new ConvertedImage(ImageFormat.GIF, List.of(frame0, frame1));

        FraMCitRenderer renderer = new FraMCitRenderer(image, true);
        assertTrue(renderer.isContextual());

        Player player1 = createMockPlayer(UUID.randomUUID(), "Player1");
        Player player2 = createMockPlayer(UUID.randomUUID(), "Player2");

        // Configurar frame distinto por jugador
        renderer.setPlayerFrame(player1, 0);
        renderer.setPlayerFrame(player2, 1);

        StubMapCanvas canvas1 = new StubMapCanvas();
        renderer.render(null, canvas1, player1);
        assertEquals(11, canvas1.getPixel(0, 0));
        assertEquals(11, canvas1.getPixel(100, 100));

        StubMapCanvas canvas2 = new StubMapCanvas();
        renderer.render(null, canvas2, player2);
        assertEquals(22, canvas2.getPixel(0, 0));
        assertEquals(22, canvas2.getPixel(100, 100));
    }

    @Test
    @DisplayName("F6.1 - FraMCitRenderer usa FrameProvider global o inyectado")
    void testFrameProviderIntegration() {
        byte[] frame0Bytes = new byte[16384];
        Arrays.fill(frame0Bytes, (byte) 1);
        byte[] frame1Bytes = new byte[16384];
        Arrays.fill(frame1Bytes, (byte) 2);

        ConvertedImage image = new ConvertedImage(ImageFormat.GIF, List.of(
            new ConvertedFrame(frame0Bytes, 100),
            new ConvertedFrame(frame1Bytes, 100)
        ));

        Player player = createMockPlayer(UUID.randomUUID(), "PlayerTest");

        // Con FrameProvider inyectado
        FraMCitRenderer renderer = new FraMCitRenderer(
            10,
            null,
            image,
            true,
            (p, mapId) -> 1
        );

        StubMapCanvas canvas = new StubMapCanvas();
        renderer.render(null, canvas, player);
        assertEquals(2, canvas.getPixel(0, 0));
    }

    @Test
    @DisplayName("F6.2 - drawFrame con indices fuera de rango usa frame 0 con seguridad")
    void testDrawFrameBounds() {
        byte[] frameBytes = new byte[16384];
        Arrays.fill(frameBytes, (byte) 7);
        ConvertedFrame frame = new ConvertedFrame(frameBytes, 0);
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG, List.of(frame));

        FraMCitRenderer renderer = new FraMCitRenderer(image);
        StubMapCanvas canvas = new StubMapCanvas();

        // Indice negativo
        renderer.drawFrame(canvas, -1);
        assertEquals(16384, canvas.setPixelCalls);

        // Indice mayor a cantidad de frames
        renderer.drawFrame(canvas, 999);
        assertEquals(32768, canvas.setPixelCalls);
    }
}
