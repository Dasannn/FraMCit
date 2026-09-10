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
import java.util.Arrays;
import java.util.List;

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
    @DisplayName("F4.3 - Modo contextual preparado para F6")
    void testContextualMode() {
        byte[] frameBytes = new byte[16384];
        Arrays.fill(frameBytes, (byte) 10);
        ConvertedFrame frame = new ConvertedFrame(frameBytes, 100);
        ConvertedImage image = new ConvertedImage(ImageFormat.GIF, List.of(frame));

        FraMCitRenderer contextualRenderer = new FraMCitRenderer(image, true);
        assertTrue(contextualRenderer.isContextual());

        StubMapCanvas canvas = new StubMapCanvas();
        contextualRenderer.render(null, canvas, null);
        assertEquals(16384, canvas.setPixelCalls);
    }

    @Test
    @DisplayName("F4.3 - drawFrame con indices fuera de rango usa frame 0 con seguridad")
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
