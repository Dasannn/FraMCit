package com.dasannn.framcit.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class EconomyServiceTest {

    private static class TestLogHandler extends Handler {
        final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() throws SecurityException {}
    }

    private Player createMockPlayer(UUID uuid, String name, boolean hasGratis) {
        return (Player) Proxy.newProxyInstance(
            Player.class.getClassLoader(),
            new Class<?>[]{ Player.class },
            (proxy, method, args) -> {
                String mName = method.getName();
                if ("getUniqueId".equals(mName)) return uuid;
                if ("getName".equals(mName)) return name;
                if ("isOnline".equals(mName)) return true;
                if ("hasPermission".equals(mName) && args != null && args.length > 0) {
                    if ("framcit.gratis".equals(args[0])) {
                        return hasGratis;
                    }
                }
                return null;
            }
        );
    }

    @Test
    @DisplayName("F10.16 & F10.3 - Sin Vault: arranca y las imagenes son gratis")
    void testNoVaultInstalledStartsAndImagesAreFree() {
        Logger logger = Logger.getLogger("TestLogger-NoVault");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        PluginManager pm = (PluginManager) Proxy.newProxyInstance(
            PluginManager.class.getClassLoader(),
            new Class<?>[]{ PluginManager.class },
            (proxy, method, args) -> {
                if ("getPlugin".equals(method.getName()) && args.length > 0 && "Vault".equals(args[0])) {
                    return null; // Vault no instalado
                }
                return null;
            }
        );

        EconomyService eco = EconomyManager.resolve(pm, null, 10.0, logger);

        assertFalse(eco.isEnabled(), "Sin Vault el cobro debe estar deshabilitado");
        assertEquals(0.0, eco.getPrice());
        assertEquals("gratis", eco.formatPrice());

        Player player = createMockPlayer(UUID.randomUUID(), "Jugador1", false);
        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && a.isFree(), "checkCanPay debe permitir gratis");

        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.Success s && s.isFree() && "gratis".equals(s.formattedAmount()), "charge debe ser gratis");

        // F10.5: Una sola línea informativa en el log al arrancar
        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("Vault no está instalado")),
            "Debe registrar que Vault no está instalado");
    }

    @Test
    @DisplayName("F10.16 & F10.4 - Con Vault pero sin plugin de economia registrado: gratis")
    void testVaultInstalledWithoutEconomyProviderRegistered() {
        Logger logger = Logger.getLogger("TestLogger-NoEcoProvider");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        Plugin vaultPlugin = (Plugin) Proxy.newProxyInstance(
            Plugin.class.getClassLoader(),
            new Class<?>[]{ Plugin.class },
            (proxy, method, args) -> {
                if ("isEnabled".equals(method.getName())) return true;
                return null;
            }
        );

        PluginManager pm = (PluginManager) Proxy.newProxyInstance(
            PluginManager.class.getClassLoader(),
            new Class<?>[]{ PluginManager.class },
            (proxy, method, args) -> {
                if ("getPlugin".equals(method.getName()) && args.length > 0 && "Vault".equals(args[0])) {
                    return vaultPlugin;
                }
                return null;
            }
        );

        ServicesManager sm = (ServicesManager) Proxy.newProxyInstance(
            ServicesManager.class.getClassLoader(),
            new Class<?>[]{ ServicesManager.class },
            (proxy, method, args) -> null // Sin proveedor registrado
        );

        EconomyService eco = EconomyManager.resolve(pm, sm, 10.0, logger);

        assertFalse(eco.isEnabled());
        assertEquals("gratis", eco.formatPrice());

        Player player = createMockPlayer(UUID.randomUUID(), "Jugador1", false);
        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && a.isFree());

        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.Success s && s.isFree());

        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("no hay ningún plugin de economía registrado")),
            "Debe registrar que no hay plugin de economía registrado");
    }

    @Test
    @DisplayName("F10.16 & F10.4 - Precio 0: gratis y sin mensajes de dinero")
    void testPriceZeroImagesAreFreeAndNoMoneyMessages() {
        Logger logger = Logger.getLogger("TestLogger-PriceZero");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        EconomyService eco = EconomyManager.resolve((PluginManager) null, null, 0.0, logger);

        assertFalse(eco.isEnabled());
        assertEquals("gratis", eco.formatPrice());

        Player player = createMockPlayer(UUID.randomUUID(), "Jugador1", false);
        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && a.isFree());

        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.Success s && s.isFree());

        assertTrue(handler.records.stream().anyMatch(r -> r.getMessage().contains("el precio configurado es 0 (gratis)")),
            "Debe registrar que el precio configurado es 0");
    }

    @Test
    @DisplayName("F10.16 & F10.8 - Precio configurado: se cobra exactamente esa cantidad")
    void testConfiguredPriceExactAmountCharged() {
        TestEconomy testEco = new TestEconomy();
        UUID uuid = UUID.randomUUID();
        Player player = createMockPlayer(uuid, "JugadorPaga", false);
        testEco.setBalance(player, 50.0);

        VaultEconomyService eco = new VaultEconomyService(testEco, 15.0, Logger.getAnonymousLogger());
        assertTrue(eco.isEnabled());
        assertEquals(15.0, eco.getPrice());
        assertEquals("15.00 $", eco.formatPrice());

        // Comprobación previa de saldo
        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && !a.isFree(), "Debe permitir pero no ser gratis");

        // Cobro al entregar
        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.Success s && !s.isFree(), "Debe cobrar con éxito");
        assertEquals("15.00 $", ((ChargeResult.Success) charge).formattedAmount());

        // Verificar que se descontó exactamente 15.0
        assertEquals(35.0, testEco.getBalance(player), 0.001);
    }

    @Test
    @DisplayName("F10.16 - Saldo justo: pasa")
    void testExactBalancePasses() {
        TestEconomy testEco = new TestEconomy();
        UUID uuid = UUID.randomUUID();
        Player player = createMockPlayer(uuid, "JugadorJusto", false);
        testEco.setBalance(player, 20.0);

        VaultEconomyService eco = new VaultEconomyService(testEco, 20.0, Logger.getAnonymousLogger());

        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && !a.isFree());

        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.Success s && !s.isFree());
        assertEquals(0.0, testEco.getBalance(player), 0.001);
    }

    @Test
    @DisplayName("F10.16 & F10.7 - Saldo insuficiente: comprobacion previa falla y no se cobra")
    void testInsufficientBalancePreCheckFails() {
        TestEconomy testEco = new TestEconomy();
        UUID uuid = UUID.randomUUID();
        Player player = createMockPlayer(uuid, "JugadorPobre", false);
        testEco.setBalance(player, 5.0);

        VaultEconomyService eco = new VaultEconomyService(testEco, 20.0, Logger.getAnonymousLogger());

        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.InsufficientFunds inf);
        assertEquals("20.00 $", ((EconomyCheckResult.InsufficientFunds) check).formattedPrice());
        assertEquals("5.00 $", ((EconomyCheckResult.InsufficientFunds) check).formattedBalance());

        // Verificar que withdrawPlayer NUNCA fue llamado
        assertEquals(0, testEco.getWithdrawCalls());
        assertEquals(5.0, testEco.getBalance(player), 0.001);
    }

    @Test
    @DisplayName("F10.16 & F10.9 - Saldo perdido entre comprobacion y cobro: no se cobra y falla como InsufficientFunds")
    void testBalanceLostBetweenCheckAndCharge() {
        TestEconomy testEco = new TestEconomy();
        UUID uuid = UUID.randomUUID();
        Player player = createMockPlayer(uuid, "JugadorGastoso", false);
        testEco.setBalance(player, 20.0);

        VaultEconomyService eco = new VaultEconomyService(testEco, 20.0, Logger.getAnonymousLogger());

        // Comprobación inicial: tiene 20.0, pasa
        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && !a.isFree());

        // Simular que el jugador gastó dinero durante la descarga
        testEco.setBalance(player, 5.0);

        // Cobro al entregar: ya no le alcanza
        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.InsufficientFunds, "Debe retornar InsufficientFunds");
        assertEquals(5.0, testEco.getBalance(player), 0.001, "No debe tocar el saldo residual");
    }

    @Test
    @DisplayName("F10.16 & F10.10 - El plugin de economia lanza excepcion al cobrar: falla como Failure")
    void testEconomyPluginThrowsExceptionOnCharge() {
        TestEconomy testEco = new TestEconomy();
        UUID uuid = UUID.randomUUID();
        Player player = createMockPlayer(uuid, "JugadorError", false);
        testEco.setBalance(player, 50.0);
        testEco.setThrowOnWithdraw(true);

        VaultEconomyService eco = new VaultEconomyService(testEco, 20.0, Logger.getAnonymousLogger());

        EconomyCheckResult check = eco.checkCanPay(player);
        assertTrue(check instanceof EconomyCheckResult.Allowed);

        ChargeResult charge = eco.charge(player);
        assertTrue(charge instanceof ChargeResult.Failure f, "Debe retornar Failure ante excepción");
        assertNotNull(((ChargeResult.Failure) charge).cause());
        assertTrue(((ChargeResult.Failure) charge).errorMessage().contains("Simulated database crash"));
    }

    @Test
    @DisplayName("F10.16 & F10.12 - Jugador con framcit.gratis: no paga y no se le comprueba saldo")
    void testPlayerWithFramcitGratisExempt() {
        TestEconomy testEco = new TestEconomy();
        UUID uuid = UUID.randomUUID();
        Player playerExempt = createMockPlayer(uuid, "AdminExento", true);
        testEco.setBalance(playerExempt, 0.0);

        VaultEconomyService eco = new VaultEconomyService(testEco, 50.0, Logger.getAnonymousLogger());
        assertTrue(eco.isExempt(playerExempt));

        EconomyCheckResult check = eco.checkCanPay(playerExempt);
        assertTrue(check instanceof EconomyCheckResult.Allowed a && a.isFree(), "Debe ser gratuito para exento");

        ChargeResult charge = eco.charge(playerExempt);
        assertTrue(charge instanceof ChargeResult.Success s && s.isFree());
        assertEquals("gratis", ((ChargeResult.Success) charge).formattedAmount());

        // Verificar que NUNCA se consultó has() ni se llamó a withdrawPlayer()
        assertEquals(0, testEco.getHasCalls(), "No se debe comprobar saldo");
        assertEquals(0, testEco.getWithdrawCalls(), "No se debe llamar a withdrawPlayer");

        // Verificar que un jugador sin framcit.gratis sí debe pagar
        Player normalPlayer = createMockPlayer(UUID.randomUUID(), "JugadorNormal", false);
        assertFalse(eco.isExempt(normalPlayer));
        EconomyCheckResult normalCheck = eco.checkCanPay(normalPlayer);
        assertTrue(normalCheck instanceof EconomyCheckResult.InsufficientFunds);
    }

    @Test
    @DisplayName("F10.16 & F10.6 - Precio negativo o invalido en config: se trata como cero")
    void testNegativeOrInvalidPriceTreatedAsZero() {
        Logger logger = Logger.getLogger("TestLogger-NegativePrice");
        logger.setUseParentHandlers(false);
        TestLogHandler handler = new TestLogHandler();
        logger.addHandler(handler);

        EconomyService eco = EconomyManager.resolve((PluginManager) null, null, -10.0, logger);
        assertFalse(eco.isEnabled());
        assertEquals(0.0, eco.getPrice());
        assertEquals("gratis", eco.formatPrice());
    }
}
