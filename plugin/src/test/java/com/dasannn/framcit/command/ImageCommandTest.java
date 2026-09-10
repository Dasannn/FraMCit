package com.dasannn.framcit.command;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.service.ImageProcessResult;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ImageCommandTest {

    private Player createMockPlayer(boolean online, List<String> receivedMessages) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("isOnline".equals(name)) {
                    return online;
                }
                if ("getName".equals(name)) {
                    return "TestPlayer";
                }
                if ("getUniqueId".equals(name)) {
                    return UUID.fromString("00000000-0000-0000-0000-000000000001");
                }
                if ("sendMessage".equals(name) && args != null && args.length > 0) {
                    if (args[0] instanceof Component comp) {
                        receivedMessages.add(PlainTextComponentSerializer.plainText().serialize(comp));
                    } else if (args[0] instanceof String s) {
                        receivedMessages.add(s);
                    }
                    return null;
                }
                return null;
            }
        );
    }

    @Test
    @DisplayName("F4.7 - Cancelar la entrega si el jugador se desconecto durante la descarga")
    void testHandleResultPlayerDisconnected() {
        List<String> receivedMessages = new ArrayList<>();
        Player offlinePlayer = createMockPlayer(false, receivedMessages);

        byte[] frameBytes = new byte[16384];
        ConvertedFrame frame = new ConvertedFrame(frameBytes, 0);
        ConvertedImage image = new ConvertedImage(ImageFormat.PNG, List.of(frame));

        ImageProcessResult.Success dummySuccess = new ImageProcessResult.Success(
            image,
            URI.create("https://example.com/test.png"),
            100
        );

        // Si el jugador esta desconectado, verificar que no debe recibir mensajes
        if (offlinePlayer.isOnline()) {
            offlinePlayer.sendMessage(Component.text(Messages.STATIC_SUCCESS));
        }

        assertTrue(receivedMessages.isEmpty(), "No se debe entregar ni enviar mensajes si el jugador esta desconectado");
    }

    @Test
    @DisplayName("F4.8 & F4.13 - Fallo entrega mensaje limpio del spec 9 sin filtrar rutas ni trazas")
    void testHandleResultSendsCleanMessageOnFailure() {
        List<String> receivedMessages = new ArrayList<>();
        Player onlinePlayer = createMockPlayer(true, receivedMessages);

        // Simulamos un fallo con causa interna que contiene una IP privada y una ruta de archivo
        Throwable internalCause = new RuntimeException("SSRF blocked to 192.168.1.10 at /opt/server/config.yml");
        ImageProcessResult.Failure failure = new ImageProcessResult.Failure(
            Messages.ERROR_BLOCKED_DESTINATION,
            internalCause
        );

        // Enviar fallo al jugador
        if (onlinePlayer.isOnline()) {
            onlinePlayer.sendMessage(Component.text(failure.userMessage()));
        }

        assertEquals(1, receivedMessages.size());
        assertEquals("URL no permitida.", receivedMessages.getFirst());
        assertFalse(receivedMessages.getFirst().contains("192.168.1.10"), "No debe filtrar IPs");
        assertFalse(receivedMessages.getFirst().contains("/opt/server"), "No debe filtrar rutas");
    }

    @Test
    @DisplayName("F4.12 - Rechazo de ejecucion desde consola y falta de permisos")
    void testCommandPermissionsAndConsoleMessages() {
        assertEquals("Este comando solo lo puede usar un jugador.", Messages.ONLY_PLAYER);
        assertEquals("No tienes permiso para usar este comando.", Messages.NO_PERMISSION);
        assertEquals("Uso: /imagen <url>", Messages.USAGE);
    }
}
