package com.dasannn.framcit.command;

import com.dasannn.framcit.image.ConvertedFrame;
import com.dasannn.framcit.image.ConvertedImage;
import com.dasannn.framcit.image.ImageFormat;
import com.dasannn.framcit.net.DownloadQueue;
import com.dasannn.framcit.store.ImageIndexEntry;
import com.dasannn.framcit.store.ImageStore;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class AdminCommandsTest {

    @TempDir
    Path tempDir;

    private DownloadQueue downloadQueue;
    private ImageStore imageStore;
    private final Logger logger = Logger.getLogger("AdminCommandsTest");

    @BeforeEach
    void setUp() {
        Messages.resetDefaults();
        this.downloadQueue = new DownloadQueue(2, 5);
        this.imageStore = new ImageStore(tempDir, downloadQueue, logger);
        this.imageStore.loadIndex();
    }

    @AfterEach
    void tearDown() {
        Messages.resetDefaults();
        if (imageStore != null) imageStore.flushAndClosePersistence(5000L);
        if (downloadQueue != null) {
            downloadQueue.close();
        }
    }

    private CommandSender createMockSender(boolean hasAdmin, List<String> receivedMessages) {
        return (CommandSender) Proxy.newProxyInstance(
            CommandSender.class.getClassLoader(),
            new Class<?>[]{ CommandSender.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("hasPermission".equals(name)) {
                    String perm = (String) args[0];
                    return "framcit.admin".equals(perm) ? hasAdmin : false;
                }
                if ("getName".equals(name)) {
                    return "AdminUser";
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

    private Player createMockPlayer(boolean hasAdmin, List<String> receivedMessages) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String name = method.getName();
                if ("hasPermission".equals(name)) {
                    String perm = (String) args[0];
                    return hasAdmin && ("framcit.admin".equals(perm) || "framcit.imagen".equals(perm));
                }
                if ("getName".equals(name)) {
                    return "AdminPlayer";
                }
                if ("isOnline".equals(name)) {
                    return true;
                }
                if ("getUniqueId".equals(name)) {
                    return UUID.fromString("00000000-0000-0000-0000-000000000099");
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
    @DisplayName("F7.5 - /imagen recargar requiere permiso framcit.admin y avisa sobre reinicio de pool")
    void testReloadPermissionAndMessage() {
        List<String> messagesNoPerm = new ArrayList<>();
        CommandSender userNoPerm = createMockSender(false, messagesNoPerm);
        ImageCommand.handleReload(userNoPerm, null);

        assertEquals(1, messagesNoPerm.size());
        assertEquals(Messages.NO_PERMISSION, messagesNoPerm.getFirst());

        List<String> messagesAdmin = new ArrayList<>();
        CommandSender admin = createMockSender(true, messagesAdmin);
        ImageCommand.handleReload(admin, null);

        assertEquals(1, messagesAdmin.size());
        assertTrue(messagesAdmin.getFirst().contains("recargados"));
        assertTrue(messagesAdmin.getFirst().contains("REINICIAR") || messagesAdmin.getFirst().contains("reiniciar"));
    }

    @Test
    @DisplayName("framcit.admin hereda framcit.imagen, pero no permisos gratis ni de cupo ilimitado")
    void testAdminPermissionOnlyImpliesImagePermission() throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream("paper-plugin.yml")) {
            assertNotNull(input, "paper-plugin.yml debe estar en los recursos de pruebas");
            Map<?, ?> descriptor = new org.yaml.snakeyaml.Yaml().load(new InputStreamReader(input, StandardCharsets.UTF_8));
            Map<?, ?> permissions = (Map<?, ?>) descriptor.get("permissions");
            Map<?, ?> admin = (Map<?, ?>) permissions.get("framcit.admin");
            Map<?, ?> children = (Map<?, ?>) admin.get("children");

            assertEquals(Boolean.TRUE, children.get("framcit.imagen"));
            assertFalse(children.containsKey("framcit.gratis"));
            assertFalse(children.containsKey("framcit.slots.ilimitado"));
        }
    }

    @Test
    @DisplayName("Un jugador con solo framcit.admin puede ejecutar /imagen; sin ambos permisos se rechaza")
    void testBaseImageCommandPermissionInheritance() {
        List<String> adminMessages = new ArrayList<>();
        Player adminOnly = createMockPlayer(true, adminMessages);
        ImageCommand.handleImage(adminOnly, "", null, null, null, null, null);
        assertEquals(List.of(Messages.USAGE), adminMessages);

        List<String> deniedMessages = new ArrayList<>();
        Player noPermission = createMockPlayer(false, deniedMessages);
        ImageCommand.handleImage(noPermission, "", null, null, null, null, null);
        assertEquals(List.of(Messages.NO_PERMISSION), deniedMessages);
    }

    @Test
    @DisplayName("F7.6 - /imagen info desde consola responde que solo lo puede usar un jugador")
    void testInfoConsoleRejected() {
        List<String> messages = new ArrayList<>();
        CommandSender console = createMockSender(true, messages);

        ImageCommand.handleInfo(console, null, imageStore);

        assertEquals(1, messages.size());
        assertEquals(Messages.ONLY_PLAYER, messages.getFirst());
    }

    @Test
    @DisplayName("F7.6 - /imagen info sin permiso es rechazado")
    void testInfoNoPermission() {
        List<String> messages = new ArrayList<>();
        Player player = createMockPlayer(false, messages);

        ImageCommand.handleInfo(player, null, imageStore);

        assertEquals(1, messages.size());
        assertEquals(Messages.NO_PERMISSION, messages.getFirst());
    }

    @Test
    @DisplayName("F7.7 - /imagen purgar sin permiso es rechazado")
    void testPurgeNoPermission() {
        List<String> messages = new ArrayList<>();
        CommandSender user = createMockSender(false, messages);

        ImageCommand.handlePurge(user, "1", null, imageStore);

        assertEquals(1, messages.size());
        assertEquals(Messages.NO_PERMISSION, messages.getFirst());
    }

    @Test
    @DisplayName("F7.7 - /imagen purgar sin objetivo muestra mensaje de uso")
    void testPurgeEmptyTargetShowsUsage() {
        List<String> messages = new ArrayList<>();
        CommandSender admin = createMockSender(true, messages);

        ImageCommand.handlePurge(admin, "", null, imageStore);

        assertEquals(1, messages.size());
        assertEquals(Messages.ADMIN_PURGE_USAGE, messages.getFirst());
    }

    @Test
    @DisplayName("F7.7 - /imagen purgar con objetivo inexistente muestra no encontrado")
    void testPurgeUnknownTarget() {
        List<String> messages = new ArrayList<>();
        CommandSender admin = createMockSender(true, messages);

        ImageCommand.handlePurge(admin, "999", null, imageStore);

        assertEquals(1, messages.size());
        assertEquals(Messages.ADMIN_PURGE_NOT_FOUND, messages.getFirst());
    }

    @Test
    @DisplayName("F7.7 - /imagen purgar sin 'confirmar' solicita confirmación y no borra nada")
    void testPurgeWithoutConfirmationDoesNotDelete() throws IOException {
        // Crear entrada en store y archivo en disco
        int mapId = 42;
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId,
            "https://example.com/test.png",
            "example.com/test.png",
            "Player1",
            "uuid-1",
            "2026-09-10",
            "PNG",
            1,
            List.of(),
            1,
            false
        );
        imageStore.getIndexManager().addEntry(entry);
        imageStore.saveIndex();

        Path fmcFile = imageStore.getImagesFolder().resolve(mapId + ".fmc");
        Files.createDirectories(fmcFile.getParent());
        Files.writeString(fmcFile, "dummy-fmc-data");

        ConvertedImage dummyImg = new ConvertedImage(ImageFormat.PNG, List.of(new ConvertedFrame(new byte[16384], 0)));
        imageStore.getLruCache().put(mapId, dummyImg);

        List<String> messages = new ArrayList<>();
        CommandSender admin = createMockSender(true, messages);

        // Ejecutar sin confirmar
        ImageCommand.handlePurge(admin, "42", null, imageStore);

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("confirmar"), "Debe pedir confirmación");
        assertTrue(Files.exists(fmcFile), "El archivo en disco NO debe haberse borrado");
        assertNotNull(imageStore.findByMapId(mapId), "El índice aún debe contener la entrada");
        assertNotNull(imageStore.getIfPresent(mapId), "El cache en memoria aún debe contener la imagen");
    }

    @Test
    @DisplayName("F7.7 - /imagen purgar con 'confirmar' borra de disco, cache e índice")
    void testPurgeWithConfirmationDeletesEverything() throws IOException {
        int mapId = 77;
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId,
            "https://example.com/pic.gif",
            "example.com/pic.gif",
            "Player2",
            "uuid-2",
            "2026-09-10",
            "GIF",
            2,
            List.of(100, 100),
            1,
            false
        );
        imageStore.getIndexManager().addEntry(entry);
        imageStore.saveIndex();

        Path fmcFile = imageStore.getImagesFolder().resolve(mapId + ".fmc");
        Files.createDirectories(fmcFile.getParent());
        Files.writeString(fmcFile, "dummy-fmc-data-77");

        ConvertedImage dummyImg = new ConvertedImage(ImageFormat.GIF, List.of(
            new ConvertedFrame(new byte[16384], 100),
            new ConvertedFrame(new byte[16384], 100)
        ));
        imageStore.getLruCache().put(mapId, dummyImg);

        List<String> messages = new ArrayList<>();
        CommandSender admin = createMockSender(true, messages);

        // Ejecutar con confirmación por ID
        ImageCommand.handlePurge(admin, "77 confirmar", null, imageStore);

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("purgada correctamente"), "Debe confirmar purga exitosa");
        assertFalse(Files.exists(fmcFile), "El archivo en disco DEBE haberse borrado");
        assertNull(imageStore.findByMapId(mapId), "El índice no debe contener la entrada");
        assertNull(imageStore.getIfPresent(mapId), "El cache en memoria no debe contener la imagen");
    }

    @Test
    @DisplayName("F7.7 - /imagen purgar por URL con confirmación funciona igual que por ID")
    void testPurgeByUrlWithConfirmation() throws IOException {
        int mapId = 88;
        String rawUrl = "https://example.com/art.png";
        String normUrl = com.dasannn.framcit.store.UrlNormalizer.normalize(rawUrl);
        ImageIndexEntry entry = new ImageIndexEntry(
            mapId,
            rawUrl,
            normUrl,
            "Artist",
            "uuid-artist",
            "2026-09-10",
            "PNG",
            1,
            List.of(),
            1,
            false
        );
        imageStore.getIndexManager().addEntry(entry);
        imageStore.saveIndex();

        Path fmcFile = imageStore.getImagesFolder().resolve(mapId + ".fmc");
        Files.createDirectories(fmcFile.getParent());
        Files.writeString(fmcFile, "art-bytes");

        List<String> messages = new ArrayList<>();
        CommandSender admin = createMockSender(true, messages);

        ImageCommand.handlePurge(admin, rawUrl + " confirmar", null, imageStore);

        assertEquals(1, messages.size());
        assertTrue(messages.getFirst().contains("purgada correctamente"));
        assertFalse(Files.exists(fmcFile));
        assertNull(imageStore.findByMapId(mapId));
    }

    @Test
    @DisplayName("F7.8 - /imagen listar con almacén vacío responde mensaje de vacío")
    void testListEmptyStore() {
        List<String> messages = new ArrayList<>();
        CommandSender admin = createMockSender(true, messages);

        ImageCommand.handleList(admin, 1, imageStore);

        assertEquals(1, messages.size());
        assertEquals(Messages.ADMIN_LIST_EMPTY, messages.getFirst());
    }

    @Test
    @DisplayName("F7.8 - /imagen listar: primera página, última y página fuera de rango")
    void testListPaginationFirstLastAndOutOfRange() {
        // Llenar con 12 entradas (con 5 por página: 3 páginas en total: p1=5, p2=5, p3=2)
        for (int i = 1; i <= 12; i++) {
            ImageIndexEntry entry = new ImageIndexEntry(
                i,
                "https://example.com/image" + i + ".png",
                "example.com/image" + i + ".png",
                "Author" + i,
                "uuid-" + i,
                "2026-09-10",
                "PNG",
                1,
                List.of(),
                1,
                false
            );
            imageStore.getIndexManager().addEntry(entry);
        }

        CommandSender admin = createMockSender(true, new ArrayList<>());

        // 1. Primera página (página 1)
        List<String> p1Messages = new ArrayList<>();
        CommandSender adminP1 = createMockSender(true, p1Messages);
        ImageCommand.handleList(adminP1, 1, imageStore);

        // Debe contener encabezado (Página 1/3)
        assertTrue(p1Messages.stream().anyMatch(m -> m.contains("Página 1/3") || m.contains("1/3")), "Debe indicar página 1/3");
        // Debe contener los primeros 5 items (#1 al #5)
        assertTrue(p1Messages.stream().anyMatch(m -> m.contains("#1 | Author1")));
        assertTrue(p1Messages.stream().anyMatch(m -> m.contains("#5 | Author5")));
        // NO debe contener el item #6
        assertFalse(p1Messages.stream().anyMatch(m -> m.contains("#6 | Author6")));
        // Debe tener pie que sugiera la página 2
        assertTrue(p1Messages.stream().anyMatch(m -> m.contains("/imagen listar 2")), "Debe sugerir ver página 2");

        // 2. Última página (página 3)
        List<String> p3Messages = new ArrayList<>();
        CommandSender adminP3 = createMockSender(true, p3Messages);
        ImageCommand.handleList(adminP3, 3, imageStore);

        assertTrue(p3Messages.stream().anyMatch(m -> m.contains("Página 3/3") || m.contains("3/3")), "Debe indicar página 3/3");
        assertTrue(p3Messages.stream().anyMatch(m -> m.contains("#11 | Author11")));
        assertTrue(p3Messages.stream().anyMatch(m -> m.contains("#12 | Author12")));
        assertFalse(p3Messages.stream().anyMatch(m -> m.contains("#10 | Author10")));
        // En la última página no debe haber pie sugiriendo siguiente página
        assertFalse(p3Messages.stream().anyMatch(m -> m.contains("/imagen listar 4")));

        // 3. Página fuera de rango (página 0 y página 4)
        List<String> p0Messages = new ArrayList<>();
        CommandSender adminP0 = createMockSender(true, p0Messages);
        ImageCommand.handleList(adminP0, 0, imageStore);
        assertEquals(1, p0Messages.size());
        assertTrue(p0Messages.getFirst().contains("fuera de rango"), "Página 0 debe avisar fuera de rango");
        assertTrue(p0Messages.getFirst().contains("3"), "Debe indicar total de 3 páginas");

        List<String> p4Messages = new ArrayList<>();
        CommandSender adminP4 = createMockSender(true, p4Messages);
        ImageCommand.handleList(adminP4, 4, imageStore);
        assertEquals(1, p4Messages.size());
        assertTrue(p4Messages.getFirst().contains("fuera de rango"), "Página 4 debe avisar fuera de rango");
    }

    @Test
    @DisplayName("F7.9 - Autocompletado de subcomandos con Brigadier según permisos")
    void testSubcommandsAutocompletion() throws Exception {
        com.mojang.brigadier.CommandDispatcher<CommandSourceStack> dispatcher = new com.mojang.brigadier.CommandDispatcher<>();
        var node = ImageCommand.createCommandNode(null, null, imageStore);
        dispatcher.getRoot().addChild(node);

        CommandSender adminSender = createMockSender(true, new ArrayList<>());
        CommandSourceStack adminStack = (CommandSourceStack) Proxy.newProxyInstance(
            CommandSourceStack.class.getClassLoader(),
            new Class<?>[]{ CommandSourceStack.class },
            (proxy, method, args) -> {
                if ("getSender".equals(method.getName())) {
                    return adminSender;
                }
                return null;
            }
        );

        CommandSender userSender = createMockSender(false, new ArrayList<>());
        CommandSourceStack userStack = (CommandSourceStack) Proxy.newProxyInstance(
            CommandSourceStack.class.getClassLoader(),
            new Class<?>[]{ CommandSourceStack.class },
            (proxy, method, args) -> {
                if ("getSender".equals(method.getName())) {
                    return userSender;
                }
                return null;
            }
        );

        // 1. Admin recibe sugerencias de los 4 subcomandos
        var parseAdmin = dispatcher.parse("imagen ", adminStack);
        var suggestionsAdmin = dispatcher.getCompletionSuggestions(parseAdmin).get();
        List<String> listAdmin = suggestionsAdmin.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();

        assertTrue(listAdmin.contains("recargar"), "Debe sugerir recargar");
        assertTrue(listAdmin.contains("info"), "Debe sugerir info");
        assertTrue(listAdmin.contains("purgar"), "Debe sugerir purgar");
        assertTrue(listAdmin.contains("listar"), "Debe sugerir listar");

        // 2. Comprobar que los subcomandos de administración exigen framcit.admin según canUse
        assertTrue(node.getChild("recargar").canUse(adminStack), "Admin puede usar recargar");
        assertFalse(node.getChild("recargar").canUse(userStack), "Usuario común NO puede usar recargar");

        assertTrue(node.getChild("info").canUse(adminStack), "Admin puede usar info");
        assertFalse(node.getChild("info").canUse(userStack), "Usuario común NO puede usar info");

        assertTrue(node.getChild("purgar").canUse(adminStack), "Admin puede usar purgar");
        assertFalse(node.getChild("purgar").canUse(userStack), "Usuario común NO puede usar purgar");

        assertTrue(node.getChild("listar").canUse(adminStack), "Admin puede usar listar");
        assertFalse(node.getChild("listar").canUse(userStack), "Usuario común NO puede usar listar");

        // 3. Sugerencias de argumentos para /imagen purgar
        imageStore.getIndexManager().addEntry(new ImageIndexEntry(
            200,
            "https://example.com/map200.png",
            "example.com/map200.png",
            "AutoTest",
            "uuid-200",
            "2026-09-10",
            "PNG",
            1,
            List.of(),
            1,
            false
        ));
        var parsePurgar = dispatcher.parse("imagen purgar ", adminStack);
        var suggestionsPurgar = dispatcher.getCompletionSuggestions(parsePurgar).get();
        List<String> listPurgar = suggestionsPurgar.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
        assertTrue(listPurgar.contains("200"), "Debe sugerir ID 200 en purgar");
        assertTrue(listPurgar.contains("https://example.com/map200.png"), "Debe sugerir URL en purgar");

        // 4. Sugerencias de argumentos para /imagen listar
        var parseListar = dispatcher.parse("imagen listar ", adminStack);
        var suggestionsListar = dispatcher.getCompletionSuggestions(parseListar).get();
        List<String> listListar = suggestionsListar.getList().stream().map(com.mojang.brigadier.suggestion.Suggestion::getText).toList();
        assertTrue(listListar.contains("1"), "Debe sugerir página 1 en listar");
    }
}
