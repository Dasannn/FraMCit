package com.dasannn.framcit.quota;

import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionTierTest {

    private static class MockPermissible implements Permissible {
        private final Set<String> permissions = new HashSet<>();

        public void addPermission(String perm) {
            permissions.add(perm);
        }

        @Override
        public boolean isPermissionSet(String name) {
            return permissions.contains(name);
        }

        @Override
        public boolean isPermissionSet(Permission perm) {
            return permissions.contains(perm.getName());
        }

        @Override
        public boolean hasPermission(String name) {
            return permissions.contains(name);
        }

        @Override
        public boolean hasPermission(Permission perm) {
            return permissions.contains(perm.getName());
        }

        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, String name, boolean value, int ticks) { return null; }
        @Override public PermissionAttachment addAttachment(Plugin plugin, int ticks) { return null; }
        @Override public void removeAttachment(PermissionAttachment attachment) {}
        @Override public void recalculatePermissions() {}
        @Override public Set<PermissionAttachmentInfo> getEffectivePermissions() { return Set.of(); }
        @Override public boolean isOp() { return false; }
        @Override public void setOp(boolean value) {}
    }

    @Test
    @DisplayName("Sin ningún nodo se aplica el valor por defecto")
    void testDefaultFallback() {
        MockPermissible player = new MockPermissible();
        int slots = PermissionTier.resolveSlots(player, 10, 1);
        assertEquals(1, slots);

        int frames = PermissionTier.resolveEffectiveFrames(player, 100, 100, 100);
        assertEquals(100, frames);
    }

    @Test
    @DisplayName("Varios nodos: gana el mayor y no se suman (Spec 003 seccion 3)")
    void testHighestWinsAndDoesNotSum() {
        MockPermissible player = new MockPermissible();
        player.addPermission("framcit.slots.1");
        player.addPermission("framcit.slots.3");

        int slots = PermissionTier.resolveSlots(player, 10, 1);
        assertEquals(3, slots, "Tener framcit.slots.1 y 3 debe dar 3, no 4 ni 1");

        MockPermissible playerFrames = new MockPermissible();
        playerFrames.addPermission("framcit.frames.10");
        playerFrames.addPermission("framcit.frames.50");

        int frames = PermissionTier.resolveEffectiveFrames(playerFrames, 100, 100, 100);
        assertEquals(50, frames, "Tener framcit.frames.10 y 50 debe dar 50, no 60");
    }

    @Test
    @DisplayName("Nodo framcit.slots.ilimitado otorga slots ilimitados")
    void testUnlimitedSlots() {
        MockPermissible player = new MockPermissible();
        player.addPermission("framcit.slots.ilimitado");
        player.addPermission("framcit.slots.2");

        assertTrue(PermissionTier.hasUnlimitedSlots(player));
        assertEquals(PermissionTier.UNLIMITED, PermissionTier.resolveSlots(player, 10, 1));
    }

    @Test
    @DisplayName("F11.4 [SEC]: El cupo de frames NUNCA supera el límite global de config.yml")
    void testFramesNeverExceedGlobalLimit() {
        MockPermissible player = new MockPermissible();
        player.addPermission("framcit.frames.500");

        // Supongamos maximo-nodo busca hasta 500, pero config.yml tiene límite global 100
        int frames = PermissionTier.resolveEffectiveFrames(player, 500, 100, 100);
        assertEquals(100, frames, "Conceder framcit.frames.500 no debe superar el techo global de 100");

        // Si el límite global es 80 y el jugador tiene nodo 100
        MockPermissible player100 = new MockPermissible();
        player100.addPermission("framcit.frames.100");
        int framesCapped = PermissionTier.resolveEffectiveFrames(player100, 100, 80, 80);
        assertEquals(80, framesCapped, "Debe quedar topado al límite global de 80");
    }

    @Test
    @DisplayName("Null permissible devuelve valor por defecto")
    void testNullPermissible() {
        assertEquals(1, PermissionTier.resolveSlots(null, 10, 1));
        assertFalse(PermissionTier.hasUnlimitedSlots(null));
        assertEquals(50, PermissionTier.resolveEffectiveFrames(null, 100, 50, 100));
    }
}
