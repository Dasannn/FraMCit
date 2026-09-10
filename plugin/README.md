# FraMCit - Plugin

Plugin de Paper para convertir URLs de imagenes y GIFs en mapas de Minecraft para item frames.

## Requisitos de compilacion

- **Java**: Java 21+ instalado en el sistema (el build utiliza Gradle Toolchains con Foojay resolver para aprovisionar automáticamente Java 25 si no está instalado).
- **Gradle**: Wrapper incluido en el proyecto (`gradlew` / `gradlew.bat`).

## Como compilar

Ejecuta desde la carpeta `plugin/`:

### Windows (PowerShell / CMD)
```powershell
.\gradlew shadowJar
```

### Linux / macOS
```bash
./gradlew shadowJar
```

El archivo compilado se generará en:
```
plugin/build/libs/FraMCit-1.0.0.jar
```

## Como probar en el servidor Paper 26.2

1. Copia el archivo generado `FraMCit-1.0.0.jar` a la carpeta `plugins/` de tu servidor local Paper 26.2:
   ```
   <ruta-de-tu-servidor-paper>/plugins/FraMCit-1.0.0.jar
   ```
2. Inicia el servidor Paper 26.2.
3. Verifica en la consola que aparezca el mensaje de activación sin advertencias ni errores:
   ```
   [FraMCit] FraMCit habilitado correctamente.
   ```
4. Entra al servidor con un cliente de Minecraft y prueba en el chat:
   ```
   /imagen hola
   ```
   O usando los alias:
   ```
   /img hola
   /image hola
   ```
   Debe responder en el chat con el mensaje de marcador:
   `[FraMCit] Descargando imagen: hola`
