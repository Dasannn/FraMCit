# Informe de Rendimiento y Seguridad - Fase F8

**Fecha de emision**: 2026-09-10  
**Entorno de evaluacion**:
- **Microbenchmarks y aislamiento**: OpenJDK 25 (Temurin-25.0.4.1+1 LTS, 64-Bit Server VM), Paper API 26.2.build.123-stable. Suite automatizada: `com.dasannn.framcit.performance.F8PerformanceMeasurementTest` (178 tests pasando).
- **Servidor Paper 26.2 local**: Servidor de pruebas real ejecutado en el puerto 25565 con Paper 26.2.build.123, Java 25 (Temurin), mundos generados.

---

## 1. Resumen Ejecutivo

La fase F8 se enfoca en dos pilares independientes:
1. **Medicion y ajuste de rendimiento**: Basado en la regla directriz de la fase (*"NO optimices nada sin haberlo medido antes; optimizar sin dato es adivinar"*).
2. **Cierre formal del riesgo de reenlace de DNS (R6 / F8.9)**: Resolucion tecnica y aceptacion justificada del riesgo residual.

### Estado general de los Criterios de Aceptacion (Spec 11)

| Criterio | Descripcion | Requisito Spec | Resultado / Verificacion | Estado |
|---|---|---|---|---|
| **1** | Descarga y colocacion de imagenes | Imagen descargada y colocada en marco | Verificado en servidor Paper 26.2 real por el owner (cierra riesgo A1 de conexion saliente) | **Verificado por el owner** |
| **7** | URL que no responde nunca | No bloquea el servidor, timeout y TPS intacto | Medido 0.000 ms de bloqueo en hilo principal. Guia de prueba en servidor real provista para el owner | **Pendiente de prueba en servidor con jugador** |
| **9** | 20 GIFs visibles, 10 jugadores mirando | Sin caida medible de TPS | CPU aislada: 0.032 - 0.053 ms/tick (techo proyectado ~19.98 TPS). Guia de prueba en servidor real provista para el owner | **Pendiente de prueba en servidor con jugador** |
| **10** | Quitar jar y arrancar servidor | Sin errores ni corrupcion de mundos | Verificado en servidor Paper 26.2 local: 0 errores, mundos intactos, arranque limpio sin plugin | **Verificado en servidor Paper 26.2 local** |

---

## 2. Metodologia: Distincion entre lo Medido, Proyectado y No Medido

Para mantener una rigurosa honestidad tecnica, este informe separa estrictamente lo que se midio experimentalmente en microbenchmarks de lo que constituye una proyeccion teorica y de lo que no puede medirse sin jugadores ni stack de red real.

### 2.1. LO MEDIDO (Coste de CPU de nuestro codigo en aislamiento)

Las siguientes metricas corresponden al coste exclusivo del codigo de FraMCit ejecutado sobre la JVM, con calentamiento previo de JIT (warmup):

1. **Bucle de `AnimationScheduler` (F8.11)**:
   - Recorrido tick a tick de las sesiones activas en memoria:
     - 200 estados registrados (10 activos): **0.551 µs a 1.450 µs** por tick (0.00055 - 0.00145 ms).
     - 1,000 estados registrados (20 activos): **1.261 µs a 2.114 µs** por tick (0.00126 - 0.00211 ms).
   - Comprobar la condicion booleana `!state.isGazing()` toma ~1-2 microsegundos para hasta un millar de marcos registrados.

2. **Copia en memoria de frames (`FraMCitRenderer` / `System.arraycopy`) (F8.1 & F8.2)**:
   - Transferencia del array de 16,384 bytes (128x128 píxeles indexados) al buffer del mapa:
     - 20 GIFs activos simultaneos: **32.339 µs a 53.298 µs** por tick (**0.032 a 0.053 ms**).
     - Percentil 95 (P95): **80.700 µs a 156.900 µs** (0.08 - 0.16 ms).
     - Percentil 99 (P99): **120.300 µs a 250.700 µs** (0.12 - 0.25 ms).

3. **Consumo de memoria del cache LRU (`LruMemoryCache`) (F8.4)**:
   - Configurado en 128 MB (`134,217,728` bytes).
   - Memoria real ocupada:
     - 20 GIFs tipicos (30 frames c/u, 491.5 KB por GIF): **9.38 MB** (`9,830,400` bytes) -> **7.32%** del limite.
     - 20 GIFs maximos (100 frames c/u, 1.64 MB por GIF): **31.25 MB** (`32,768,000` bytes) -> **24.41%** del limite.
   - La memoria se asigna bajo demanda y no retiene memoria al arrancar. El techo de 128 MB es seguro y no requiere ajuste.

4. **Drenado del pool de hilos unificado (`DownloadQueue`) (F8.10)**:
   - 20 lecturas concurrentes de disco (`.fmc`) compitiendo con descargas de red sobre los 4 workers del pool:
     - Tiempo total de drenado de las 20 lecturas de disco: **176.47 ms a 204.53 ms** (~9 ms por archivo de 30 frames).
     - Rechazos por cola llena (`QueueFullException`): **0 rechazos** (capacidad: 4 en ejecucion + 20 en cola = 24).
     - Latencia observada para la descarga concurrente de un jugador: **193.15 ms a 229.05 ms** (incluyendo 50 ms de I/O de red simulada).

5. **Despacho del comando `/imagen` en hilo principal (F8.6)**:
   - Tiempo de CPU en hilo principal al parsear y encolar: **0.430 ms a 0.688 ms**.
   - Bloqueo síncrono del hilo principal esperando respuesta de red: **0.000 ms** (0 nanosegundos).

6. **Aislamiento del hilo principal (F8.3 - Regla 1 de la Constitution)**:
   - 100% de las operaciones de red (`Downloader.download`), decodificacion (`ImageDecoder.decode`), conversion a paleta (`MapConverter.convert`) y acceso a disco (`FmcFormat.writeToFile` y `readImageFromFile`) se ejecutan en los hilos de trabajo `framcit-net-*`. Cero llamadas bloqueantes en el hilo principal de Bukkit.

---

### 2.2. LO PROYECTADO (Estimacion aritmetica de techo teorico)

En los microbenchmarks de F8.2 se reporto una cifra de *"19.98 - 20.00 TPS"*. Es fundamental aclarar el origen y caracter de dicha cifra:

- **Formula utilizada**:
  $$\text{MSPT}_{\text{FraMCit}} = \text{Coste CPU medido} \approx 0.053\text{ ms}$$
  $$\text{TPS}_{\text{proyectado}} = \frac{50.000\text{ ms}}{50.000\text{ ms} + \text{MSPT}_{\text{FraMCit}}} \times 20 = \frac{50}{50.053} \times 20 \approx 19.9788 \approx 19.98\text{ TPS}$$
- **Aclaracion obligatoria**: Este calculo es una **proyeccion aritmetica ideal** que evalua el impacto marginal que nuestro bucle de CPU agrega a un tick perfecto de 50 ms. **NO es un TPS observado en un servidor en produccion**. No contempla:
  - Fluctuaciones del recolector de basura (GC) de la JVM.
  - Carga del procesador por otros plugins.
  - Ticks de entidades de Minecraft, iluminacion o generacion de chunks.

---

### 2.3. LO NO MEDIDO (Limitaciones del entorno de prueba automatizado)

Por diseno de los tests unitarios y de aislamiento, los siguientes aspectos no fueron medidos:

1. **Envio de paquetes por socket de red (`sendMap`)**:
   - En el arnes de pruebas (`F8PerformanceMeasurementTest`), las llamadas a `Player.sendMap(MapView)` se interceptaron mediante un proxy dinamico:
     ```java
     if ("sendMap".equals(mName)) return null;
     ```
   - El coste real del motor de Paper serializando el paquete de mapa de Minecraft (`ClientboundMapItemDataPacket`), la compresion por zlib/Netty y el envio a traves del socket TCP (16 KB brutos por frame por jugador) **NO se midio**.
2. **Coste del raytracing de Bukkit contra chunks del mundo (`GazeTracker`)**:
   - En el test F8.5, el metodo `World.rayTraceBlocks` o la busqueda de entidades devuelve un mock predefinido. El coste real que incurre Bukkit al atravesar la estructura de voxels y secciones de chunks reales en memoria **NO se midio**.
3. **Presion sobre el Garbage Collector con red activa**:
   - El volumen de paquetes Netty y arrays temporales asignados por segundo cuando 10 clientes reales reciben 10 frames por segundo no se reproduce en un mock de testing.

---

### 2.4. LO VERIFICADO EN SERVIDOR REAL

#### Criterio 1 (Descarga y colocacion de imagenes)
- **Probado por**: Owner en un servidor Paper 26.2 real.
- **Resultado**: Las imagenes se descargan a traves de peticiones HTTP/HTTPS salientes y los mapas se entregan y renderizan en los marcos correctamente.
- **Impacto**: Se **cierra el riesgo A1** (el entorno de ejecucion dispone de salida a internet y conexion saliente no bloqueada).

#### Criterio 10 (Desinstalacion limpia y persistencia de mundos)
- **Probado por**: Evaluacion directa en servidor Paper 26.2 local (`framcit-testserver`, Temurin 25 LTS).
- **Evidencia en logs reales**:
  1. *Arranque con FraMCit v1.0.0 instalado*:
     ```text
     [20:07:57 INFO]: [FraMCit] Enabling FraMCit v1.0.0
     [20:07:57 INFO]: [FraMCit] Cache LRU inicializada con 128 MB de limite.
     [20:07:57 INFO]: [FraMCit] Carpeta de almacenamiento: ...\framcit-testserver\plugins\FraMCit\images
     [20:07:58 INFO]: [FraMCit] FraMCit habilitado correctamente.
     [20:07:58 INFO]: Done (40.124s)! For help, type "help"
     ```
     *Nota de seguridad F7.10*: No aparecio ninguna advertencia de TTL=0, confirmando que la JVM Temurin 25 LTS mantiene activa la cache de DNS positiva por defecto.
  2. *Parada limpia con el comando `stop`*:
     ```text
     [20:08:16 INFO]: [FraMCit] Disabling FraMCit v1.0.0
     [20:08:16 INFO]: [FraMCit] FraMCit deshabilitado.
     [20:08:18 INFO]: ThreadedAnvilChunkStorage (world): All chunks are saved
     [20:08:18 INFO]: Closing Server
     ```
  3. *Arranque con el jar retirado del directorio `plugins/`*:
     ```text
     [20:08:47 INFO]: [PluginInitializerManager] Initialized 0 plugins
     [20:09:51 INFO]: Done preparing level "world" (0.945s)
     [20:09:51 INFO]: Done (71.300s)! For help, type "help"
     ```
- **Resultado**: 0 errores, 0 excepciones, mundos completamente intactos. Los mapas son items vanilla `Material.FILLED_MAP` con datos en `PersistentDataContainer` oficial; al faltar el renderer contextual de FraMCit quedan en blanco sin provocar anomalias en Paper.

---

## 3. Resoluciones de Diseno

### F8.10: Contienda por el pool de hilos (disco vs red)
- **Hallazgo**: `ImageStore` comparte el pool `DownloadQueue` (4 hilos, cola de 20). En las mediciones, 20 lecturas de disco concurrentes se drenaron en solo 176.47 ms (~8.8 ms por imagen). La peticion concurrente de un jugador no sufrio ningun rechazo y se resolvio en 193 ms.
- **Decision**: **No modificar codigo**. Las lecturas de disco locales son demasiado breves para causar inanicion o contienda destructiva. Mantener un pool unificado simplifica la arquitectura y ahorra hilos de sistema operativo.

### F8.11: Coste por tick de `AnimationScheduler` al recorrer estados
- **Hallazgo**: El bucle itera sobre todos los pares (jugador, mapa) en sesion para verificar si el jugador esta mirando. Con 200 estados registrados, el recorrido toma 0.55 µs; con 1,000 estados, 1.26 µs.
- **Decision**: **No modificar codigo**. Mantener una estructura de datos secundaria sincronizada para estados activos frente a inactivos agregaria complejidad y sobrecarga de sincronizacion superior a los ~2 microsegundos que toma iterar la coleccion completa.

---

## 4. Cierre de Seguridad: Reenlace de DNS (R6 / F8.9)

### Resolucion tecnica formal
En la seccion 6.1 de `docs/constitution.md` se investigaron las alternativas tecnicas en Java 25 para fijar la direccion IP en `HttpClient`:
1. *Reescribir la URL con la IP*: Invalida la verificacion del certificado TLS/SNI en URLs HTTPS salvo que se desactive la comprobacion de nombres de host (lo cual abriria una vulnerabilidad Man-in-the-Middle critica).
2. *Proveedores SPI (`InetAddressResolverProvider`)*: Modifican la resolucion de nombres globalmente para toda la JVM, interfiriendo de forma inaceptable con el motor de Paper, la autenticacion de Mojang y el resto de plugins.
3. *Cliente HTTP artesanal*: Reimplementar un motor HTTP sobre sockets crudos dispararia el riesgo de vulnerabilidades de parseo HTTP.

### Aceptacion documentada del riesgo residual
- **Descripcion con precision tecnica**: La cache de DNS por defecto de la JVM (tipicamente 30 segundos) **no elimina el riesgo a cero**, pero **lo mitiga significativamente** al estrechar la ventana de ataque a un instante muy breve tras la expiracion del TTL de la cache. Cada 30 segundos la cache expira y se abre una ventana temporal si el atacante reintenta continuadamente la peticion.
- **Aceptacion**: Se asume formalmente este riesgo residual dado que las alternativas conllevan riesgos de seguridad e inestabilidad mayores.
- **Salvaguarda F7.10**: Al arrancar, FraMCit inspecciona el TTL efectivo de la JVM y emite una alerta severa en la consola si la cache esta desactivada (`networkaddress.cache.ttl=0`). En la prueba en servidor Paper 26.2 con Temurin 25 LTS se constato que la alerta no se disparo, confirmando la proteccion activa de la JVM.

---

## 5. Guia para el Owner: Verificacion de Criterios Pendientes en Servidor

Dado que los Criterios 7 y 9 requieren observacion directa con red y clientes reales, se detallan las instrucciones paso a paso para que el owner pueda verificarlos en su servidor Paper 26.2.

---

### Prueba 1: Criterio 7 — URL que nunca responde (Timeout sin congelar el servidor)

**Objetivo**: Confirmar que una peticion que sufre timeout no congela el hilo principal del servidor, mantiene el TPS intacto y notifica al jugador tras agotar el tiempo limite.

**Metodo**: Utilizaremos una direccion IP publica valida y enrutable que no tenga ningun servicio escuchando en un puerto inusual (por ejemplo `http://8.8.8.8:81/test.png` o `http://1.1.1.1:81/test.png`).  
*¿Por que funciona esto?*: `UrlGuard` valida que la IP es publica y legitima (no pertenece a redes privadas RFC 1918 ni de prueba RFC 5737 que se bloquean inmediatamente). La peticion pasa al `Downloader` en segundo plano, el cual intenta la conexion TCP al puerto 81. Como ningun servidor responde, espera hasta agotar el `connectTimeout` (5 segundos) sin afectar a nadie mas.

**Instrucciones paso a paso**:
1. Conectarse al servidor Paper 26.2 con un cliente de Minecraft. Asegurarse de tener permisos de administrador o el permiso `framcit.usar`.
2. En la consola o en el juego, consultar el TPS de referencia:
   ```text
   /tps
   ```
   (o `/spark tps` si tienes Spark instalado).
3. Ejecutar en el chat del juego:
   ```text
   /imagen http://8.8.8.8:81/test.png
   ```
4. **Verificaciones visuales inmediatas**:
   - De inmediato (en menos de 1 segundo), debes ver en el chat:  
     `[FraMCit] Descargando imagen...`
   - Mientras tanto, camina, salta o interactua con bloques. **No debe haber ningun tiron, micro-corte ni lag** en el servidor.
   - Si miras `/tps`, el servidor debe mantenerse a **20.00 TPS**.
5. **Verificacion al expirar el timeout**:
   - A los ~5 segundos, el servidor debe responderte en el chat:  
     `[FraMCit] El sitio tardo demasiado en responder.`
6. **Criterio cumplido si**: El mensaje de espera es inmediato, no hubo congelamiento del juego durante los 5 segundos de espera y se recibio el mensaje de timeout.

---

### Prueba 2: Criterio 9 — 20 GIFs animados colgados y rendimiento del servidor

**Objetivo**: Confirmar que tener 20 animaciones GIF colgadas simultaneamente no degrada el rendimiento del servidor cuando son observadas por jugadores.

**Metodo adaptado para 1 jugador**:  
Si no dispones de 10 cuentas de jugador simultaneas para la prueba, montar 20 cuadros animados y mirarlos con 1 jugador permite validar en condiciones reales todo el bucle de `AnimationScheduler`, el refresco de frames y el renderizado interno de Paper. Si el TPS se mantiene estable en 20.00 con 1 jugador mirando los 20 cuadros, el impacto del scheduler queda verificado; la unica variable adicional con 10 jugadores es el ancho de banda del socket de red por cliente adicional.

**Instrucciones paso a paso**:
1. Construir una pared lisa en el mundo de juego (por ejemplo de 5x4 bloques).
2. Colocar **20 marcos de items** (`ItemFrame`) juntos en la pared.
3. Descargar un GIF animado corto (de entre 15 y 30 frames) con `/imagen <url_del_gif>` para recibir el mapa inicial.
4. Colocar el mapa en los 20 marcos (puedes usar copias del mismo mapa creado o descargar varios GIFs distintos para cada marco).
5. Posicionarse a unos 4-6 bloques de distancia, mirando directamente al centro de la pared para que **los 20 marcos esten dentro de tu campo de vision**.
6. Iniciar una captura de rendimiento:
   - Si dispones de Spark: `/spark sampler --start`
   - Si no dispones de Spark, vigilar continuamente con `/tps`.
7. Permanecer mirando los 20 GIFs en movimiento continuo durante **60 segundos**.
8. Si usaste Spark, detener el muestreo con `/spark sampler --stop` y abrir el enlace generado.
9. **Criterio cumplido si**:
   - El comando `/tps` muestra **20.00** de forma continua.
   - En el reporte de `/spark` (si se uso), el tiempo consumido por el paquete `com.dasannn.framcit` en el tick de servidor es inferior a **0.20 ms** (menos del 0.4% del presupuesto de 50 ms por tick).
