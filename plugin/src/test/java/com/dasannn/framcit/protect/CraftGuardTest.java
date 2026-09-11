package com.dasannn.framcit.protect;

import com.dasannn.framcit.command.MapItemBuilder;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import io.papermc.paper.event.player.CartographyItemEvent;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.CartographyInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CraftGuardTest {

    private CraftGuard craftGuard;

    @BeforeEach
    void setUp() {
        craftGuard = new CraftGuard();
    }

    private ItemStack createMockFraMCitMap() {
        Map<String, Object> pdcStore = new HashMap<>();
        pdcStore.put(MapItemBuilder.KEY_FRAMCIT.toString(), (byte) 1);

        PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
            PersistentDataContainer.class.getClassLoader(),
            new Class<?>[]{PersistentDataContainer.class},
            (proxy, method, args) -> {
                if ("has".equals(method.getName())) {
                    return pdcStore.containsKey(args[0].toString());
                }
                return null;
            }
        );

        MapMeta meta = (MapMeta) Proxy.newProxyInstance(
            MapMeta.class.getClassLoader(),
            new Class<?>[]{MapMeta.class},
            (proxy, method, args) -> {
                if ("getPersistentDataContainer".equals(method.getName())) {
                    return pdc;
                }
                return null;
            }
        );

        return new ItemStack() {
            @Override
            public Material getType() {
                return Material.FILLED_MAP;
            }

            @Override
            public boolean hasItemMeta() {
                return true;
            }

            @Override
            public ItemMeta getItemMeta() {
                return meta;
            }
        };
    }

    private ItemStack createMockVanillaMap() {
        PersistentDataContainer pdc = (PersistentDataContainer) Proxy.newProxyInstance(
            PersistentDataContainer.class.getClassLoader(),
            new Class<?>[]{PersistentDataContainer.class},
            (proxy, method, args) -> {
                if ("has".equals(method.getName())) return false;
                return null;
            }
        );

        MapMeta meta = (MapMeta) Proxy.newProxyInstance(
            MapMeta.class.getClassLoader(),
            new Class<?>[]{MapMeta.class},
            (proxy, method, args) -> {
                if ("getPersistentDataContainer".equals(method.getName())) return pdc;
                return null;
            }
        );

        return new ItemStack() {
            @Override
            public Material getType() {
                return Material.FILLED_MAP;
            }

            @Override
            public boolean hasItemMeta() {
                return true;
            }

            @Override
            public ItemMeta getItemMeta() {
                return meta;
            }
        };
    }

    private ItemStack createMockOtherItem(Material material) {
        return new ItemStack() {
            @Override
            public Material getType() {
                return material;
            }

            @Override
            public boolean hasItemMeta() {
                return false;
            }
        };
    }

    @Test
    @DisplayName("F12.12 - isFraMCitMap identifica mapas con PDC de FraMCit correctamente")
    void testIsFraMCitMap() {
        ItemStack framcitMap = createMockFraMCitMap();
        ItemStack vanillaMap = createMockVanillaMap();
        ItemStack paper = createMockOtherItem(Material.PAPER);

        assertTrue(CraftGuard.isFraMCitMap(framcitMap));
        assertFalse(CraftGuard.isFraMCitMap(vanillaMap));
        assertFalse(CraftGuard.isFraMCitMap(paper));
        assertFalse(CraftGuard.isFraMCitMap(null));
    }

    @Test
    @DisplayName("F12.12 - PrepareItemCraftEvent vacia el resultado si la matriz contiene un mapa FraMCit")
    void testPrepareItemCraftWithFraMCitMap() {
        ItemStack framcitMap = createMockFraMCitMap();
        ItemStack paper = createMockOtherItem(Material.PAPER);
        ItemStack expectedResult = createMockVanillaMap();

        ItemStack[] matrix = new ItemStack[]{framcitMap, paper};
        final ItemStack[] slotResult = new ItemStack[]{expectedResult};

        CraftingInventory craftingInv = (CraftingInventory) Proxy.newProxyInstance(
            CraftingInventory.class.getClassLoader(),
            new Class<?>[]{CraftingInventory.class},
            (proxy, method, args) -> {
                if ("getMatrix".equals(method.getName())) return matrix;
                if ("getResult".equals(method.getName())) return slotResult[0];
                if ("setResult".equals(method.getName())) {
                    slotResult[0] = (ItemStack) args[0];
                    return null;
                }
                return null;
            }
        );

        InventoryView view = (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class<?>[]{InventoryView.class},
            (proxy, method, args) -> null
        );

        PrepareItemCraftEvent event = new PrepareItemCraftEvent(craftingInv, view, false);
        craftGuard.onPrepareItemCraft(event);

        assertNull(slotResult[0], "El resultado de la mesa de crafteo debe quedar vacio (null)");
    }

    @Test
    @DisplayName("F12.12 - PrepareItemCraftEvent no altera el resultado si los ingredientes son vainilla")
    void testPrepareItemCraftWithoutFraMCitMap() {
        ItemStack vanillaMap = createMockVanillaMap();
        ItemStack paper = createMockOtherItem(Material.PAPER);
        ItemStack expectedResult = createMockVanillaMap();

        ItemStack[] matrix = new ItemStack[]{vanillaMap, paper};
        final ItemStack[] slotResult = new ItemStack[]{expectedResult};

        CraftingInventory craftingInv = (CraftingInventory) Proxy.newProxyInstance(
            CraftingInventory.class.getClassLoader(),
            new Class<?>[]{CraftingInventory.class},
            (proxy, method, args) -> {
                if ("getMatrix".equals(method.getName())) return matrix;
                if ("getResult".equals(method.getName())) return slotResult[0];
                if ("setResult".equals(method.getName())) {
                    slotResult[0] = (ItemStack) args[0];
                    return null;
                }
                return null;
            }
        );

        InventoryView view = (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class<?>[]{InventoryView.class},
            (proxy, method, args) -> null
        );

        PrepareItemCraftEvent event = new PrepareItemCraftEvent(craftingInv, view, false);
        craftGuard.onPrepareItemCraft(event);

        assertSame(expectedResult, slotResult[0], "Crafteos vanilla no deben ser alterados");
    }

    @Test
    @DisplayName("F12.11 & F12.13 - PrepareResultEvent vacia el resultado en mesa de cartografia con mapa FraMCit")
    void testPrepareResultEventWithFraMCitMap() {
        ItemStack framcitMap = createMockFraMCitMap();
        ItemStack paper = createMockOtherItem(Material.PAPER);
        ItemStack computedResult = createMockVanillaMap();

        ItemStack[] contents = new ItemStack[]{framcitMap, paper, computedResult};

        CartographyInventory cartographyInv = (CartographyInventory) Proxy.newProxyInstance(
            CartographyInventory.class.getClassLoader(),
            new Class<?>[]{CartographyInventory.class},
            (proxy, method, args) -> {
                if ("getContents".equals(method.getName())) return contents;
                if ("getItem".equals(method.getName())) {
                    int idx = (int) args[0];
                    return (idx >= 0 && idx < contents.length) ? contents[idx] : null;
                }
                return null;
            }
        );

        InventoryView view = (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class<?>[]{InventoryView.class},
            (proxy, method, args) -> {
                if ("getTopInventory".equals(method.getName())) return cartographyInv;
                return null;
            }
        );

        PrepareResultEvent event = new PrepareResultEvent(view, computedResult);
        craftGuard.onPrepareResult(event);

        assertNull(event.getResult(), "El resultado en PrepareResultEvent debe ser null al usar mapa FraMCit");
    }

    @Test
    @DisplayName("F12.13 - CartographyItemEvent se cancela y deniega si incluye mapa FraMCit")
    void testCartographyItemEventCancelled() {
        ItemStack framcitMap = createMockFraMCitMap();
        ItemStack paper = createMockOtherItem(Material.PAPER);

        ItemStack[] contents = new ItemStack[]{framcitMap, paper, null};

        CartographyInventory cartographyInv = (CartographyInventory) Proxy.newProxyInstance(
            CartographyInventory.class.getClassLoader(),
            new Class<?>[]{CartographyInventory.class},
            (proxy, method, args) -> {
                if ("getContents".equals(method.getName())) return contents;
                return null;
            }
        );

        InventoryView view = (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class<?>[]{InventoryView.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getTopInventory".equals(name)) return cartographyInv;
                if ("convertSlot".equals(name)) return args[0];
                if ("getItem".equals(name)) {
                    int slot = (int) args[0];
                    return (slot >= 0 && slot < contents.length) ? contents[slot] : null;
                }
                return null;
            }
        );

        CartographyItemEvent event = new CartographyItemEvent(
            view,
            InventoryType.SlotType.RESULT,
            2,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );

        craftGuard.onCartographyItem(event);

        assertTrue(event.isCancelled(), "CartographyItemEvent debe ser cancelado");
        assertEquals(Event.Result.DENY, event.getResult());
    }

    @Test
    @DisplayName("F12.14 & F12.15 - InventoryClickEvent en slot de resultado de cartografia se cancela con mapa FraMCit")
    void testInventoryClickOnCartographyResultCancelled() {
        ItemStack framcitMap = createMockFraMCitMap();
        ItemStack glassPane = createMockOtherItem(Material.GLASS_PANE);

        ItemStack[] contents = new ItemStack[]{framcitMap, glassPane, null};

        CartographyInventory cartographyInv = (CartographyInventory) Proxy.newProxyInstance(
            CartographyInventory.class.getClassLoader(),
            new Class<?>[]{CartographyInventory.class},
            (proxy, method, args) -> {
                if ("getContents".equals(method.getName())) return contents;
                return null;
            }
        );

        InventoryView view = (InventoryView) Proxy.newProxyInstance(
            InventoryView.class.getClassLoader(),
            new Class<?>[]{InventoryView.class},
            (proxy, method, args) -> {
                String name = method.getName();
                if ("getTopInventory".equals(name)) return cartographyInv;
                if ("convertSlot".equals(name)) return args[0];
                if ("getItem".equals(name)) {
                    int slot = (int) args[0];
                    return (slot >= 0 && slot < contents.length) ? contents[slot] : null;
                }
                return null;
            }
        );

        InventoryClickEvent clickEvent = new InventoryClickEvent(
            view,
            InventoryType.SlotType.RESULT,
            2,
            ClickType.LEFT,
            InventoryAction.PICKUP_ALL
        );

        craftGuard.onInventoryClick(clickEvent);

        assertTrue(clickEvent.isCancelled(), "El click en slot de resultado de cartografia debe ser cancelado");
        assertEquals(Event.Result.DENY, clickEvent.getResult());
    }
}
