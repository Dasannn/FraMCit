package com.dasannn.framcit.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ConfigSecurityTest {

    @Test
    @DisplayName("F7.2 [SEC] - Los límites de seguridad NO aparecen en config.yml")
    void testSecurityFloorNotConfigurableInConfigYaml() {
        InputStream in = getClass().getResourceAsStream("/config.yml");
        assertNotNull(in, "El archivo config.yml debe existir en resources");

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));

        // Verificar que ninguna clave configure esquemas ni IPs internas
        for (String key : yaml.getKeys(true)) {
            String lower = key.toLowerCase();
            assertFalse(lower.contains("scheme") || lower.contains("esquema"),
                "El esquema no debe ser configurable por archivo: " + key);
            assertFalse(lower.contains("loopback"),
                "Loopback no debe ser configurable por archivo: " + key);
            assertFalse(lower.contains("private") || lower.contains("privad"),
                "Rangos privados no deben ser configurables por archivo: " + key);
            assertFalse(lower.contains("internal") || lower.contains("intern"),
                "IPs internas no deben ser configurables por archivo: " + key);
            assertFalse(lower.contains("whitelist") || lower.contains("blacklist"),
                "Listas de IPs o dominios no deben existir en config.yml: " + key);
        }
    }

    @Test
    @DisplayName("F7.2 [SEC] - FraMCitConfig no expone ningún método ni campo para relajar el piso de seguridad")
    void testFraMCitConfigHasNoSecurityBypassMethods() {
        for (Method method : FraMCitConfig.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase();
            assertFalse(name.contains("loopback"), "FraMCitConfig no debe contener métodos sobre loopback: " + method.getName());
            assertFalse(name.contains("scheme"), "FraMCitConfig no debe contener métodos sobre esquemas: " + method.getName());
            assertFalse(name.contains("privateip"), "FraMCitConfig no debe contener métodos sobre IPs privadas: " + method.getName());
        }

        for (Field field : FraMCitConfig.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            assertFalse(name.contains("loopback"), "FraMCitConfig no debe contener campos sobre loopback: " + field.getName());
            assertFalse(name.contains("scheme"), "FraMCitConfig no debe contener campos sobre esquemas: " + field.getName());
        }
    }
}
