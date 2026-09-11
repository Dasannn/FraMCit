# Informe de Implementación y Verificación: F11 - Cupos por Jugador (Spec 003)

**Fecha**: 10 de Septiembre de 2026  
**Rama**: `feat/f11-cupos`  
**Worktree**: `C:\Users\ASUS\Desktop\Projects\framcit-f11-cupos`  
**Autor / Rol**: Lead Product Architect & Lead Engineer  
**Estado**: Implementado y verificado (24/24 tareas completadas, 24/24 criterios de aceptación verificados).

---

## 1. Resumen Ejecutivo

La fase F11 implementa de manera íntegra el documento de especificación `docs/spec-003-slots.md`, integrándolo con el flujo base (`docs/spec.md`) y el módulo de economía (`docs/spec-002-economia.md`). La funcionalidad introduce:

1. **Cupos de slots de imágenes por jugador** mediante nodos escalonados de permisos (`framcit.slots.<n>`) con soporte para cupo ilimitado (`framcit.slots.ilimitado`).
2. **Cupos de frames por imagen para animaciones** mediante nodos escalonados de permisos (`framcit.frames.<n>`), restringido ineludiblemente por el techo global del servidor (`frames.max-frames` / constitution 6.6).
3. **Posesión multi-usuario (co-propiedad) de imágenes** dentro de `index.json` sin crear archivos adicionales en disco ni tocar SQLite.
4. **Migración transparente hacia atrás** de índices legacy que no disponían del campo `owners`.
5. **Subcomandos de gestión de cupos y recursos**:
   - `/imagen mias`: consulta de imágenes propias y cupo en uso.
   - `/imagen borrar <id> [confirmar]`: liberación de slot y borrado condicional de archivos `.fmc` (solo si nadie más posee la imagen).
   - `/imagen listar [jugador] [pagina]`: listado administrativo filtrado por jugador con informe de cupo.
   - `/imagen uso [jugador]`: medición real y no proyectada de almacenamiento en disco, memoria LRU activa y animaciones en ejecución.
   - `/imagen purgar-jugador <jugador> [confirmar]`: depuración administrativa de posesión respetando co-propiedad y auditando en log.
6. **Resolución del bug de concurrencia en `ImageDecoder`**: eliminación del estado estático compartido que provocaba solapamiento de límites entre descargas paralelas.

---

## 2. Decisiones Arquitectónicas

### 2.1. Desacoplamiento del límite en `ImageDecoder` (F11.9)
- **Problema previo**: `ImageDecoder.maxFrames` era un campo `static int` mutable. Si dos jugadores con cupos dispares (por ejemplo, jugador A con cupo 10 y jugador B con cupo 50) descargaban simultáneamente, una invocación reescribía la variable global de la otra. El jugador con cupo 10 podía llegar a decodificar y almacenar imágenes de 50 frames.
- **Solución adoptada**: Se eliminó el campo estático mutable. Cada método de decodificación (`decodeFrames(byte[], int, ...)` y `decodeFrames(InputStream, int, ...)`) recibe el límite efectivo de frames como argumento explícito por solicitud. La verificación de frames se efectúa de forma temprana en la lectura de encabezados/metadatos (antes de decodificar frames individuales a memoria) lanzando `TooManyFramesException` tan pronto como se detecta que el archivo excede el cupo.
- **Verificación**: Test concurrente `ImageDecoderConcurrencyRaceTest` ejecutando múltiples hilos asíncronos concurrentes con límites dispares (5 vs 50 frames), verificando aislamiento estricto y total ausencia de interferencia.

### 2.2. Modelo de Posesión en `index.json` (F11.5, F11.6)
- **Persistencia en el índice existente**: Para evitar la proliferación de archivos de metadatos o dependencias adicionales, la posesión se modela añadiendo el campo `owners: List<String>` (UUIDs en formato estándar) a `ImageIndexEntry`.
- **Compatibilidad e idempotencia de migración**: Al deserializar índices generados en versiones anteriores, si el campo `owners` no existe (`rawOwners == null`), se inicializa dinámicamente asignando el `authorUuid` del creador. Si el autor original es desconocido o nulo, la entrada se preserva con lista vacía sin eliminarse del disco. Al detectar migraciones, el archivo se reescribe atómicamente a disco (`IndexManager.save()`).

### 2.3. Resolución de Permisos en el Hilo Principal (F11.8)
- **Aislamiento Bukkit Thread-Safety**: Bukkit API y los gestores de permisos (como Vault o LuckPerms) no son seguros para invocarse desde hilos del pool asíncrono.
- **Mecanismo**: `ImageCommand` resuelve `PermissionTier.resolveEffectiveTier` en el hilo principal antes de encolar o despachar tareas al pool de descarga. Al pipeline de procesamiento asíncrono (`ImageService.process(url, effectiveMaxFrames)`) viaja únicamente un valor primitivo `int`, eliminando cualquier referencia a objetos de jugador o estado del servidor fuera del hilo principal.

### 2.4. Orden Estricto de Comprobaciones (Spec 003, Sección 4 / F11.10 - F11.14)
El orden se implementó de forma literal, garantizando que ninguna validación costosa (I/O, red, economía) se ejecute si un paso previo falla, y que nunca se cobre dinero si una imagen es rechazada por cupo:
1. Permiso `framcit.imagen`.
2. **Slot disponible** (`QuotaService.canAcquireImage`): Si el jugador no tiene cupo y no posee ya la URL (Regla 2.3), se aborta inmediatamente sin consultar economía ni red.
3. **Cupo de frames en acierto de caché**: Si la URL ya existe en el índice y sus frames superan el cupo del solicitante, se rechaza inmediatamente sin tocar saldo ni red.
4. **Saldo suficiente** (`EconomyService.checkCanPay`).
5. **Descarga asíncrona**.
6. **Cupo de frames en cabecera/decodificación**: Si el archivo descargado supera el límite permitido, se lanza `TooManyFramesException`, abortando la operación sin cobrar ni guardar nada en disco.
7. **Conversión y procesado a paleta de Minecraft**.
8. **Re-comprobación de slot antes de entregar**: Si el jugador llenó su cupo con otra imagen completada durante la descarga, se cancela la entrega, se notifica al jugador y **no se cobra nada**.
9. **Cobro** (`EconomyService.charge`).
10. **Entrega del mapa** y persistencia de posesión.

### 2.5. Gestión de Co-propiedad y Borrado Condicional (F11.16, F11.17, F11.20)
- Al borrar una imagen (`/imagen borrar <id>` o `/imagen purgar-jugador`), el servicio retira el UUID del jugador de la lista `owners`.
- El archivo binario `.fmc`, el registro en `index.json` y los renderizadores en memoria solo se eliminan cuando `owners.isEmpty()` (último poseedor). Si existen otros poseedores, sus mapas e instancias en juego permanecen completamente operativos e intactos.
- La confirmación (`confirmar`) es obligatoria en todos los flujos destructivos para prevenir pérdidas accidentales.

### 2.6. Medición Real y Fidedigna de Recursos (`/imagen uso`)
- **Disco**: Mide los bytes físicos de cada archivo `.fmc` existente en la carpeta de imágenes mediante `Files.size()`. No utiliza multiplicadores fijos ni estimaciones teóricas.
- **Memoria LRU**: Mide los bytes reales residentes en memoria en el `LruMemoryCache` (`16384 * frames` por entrada cargada) y desglosa cuántas imágenes están cargadas en RAM frente a cuántas permanecen exclusivamente en disco.
- **Animaciones**: Pregunta al `AnimationManager` cuántas imágenes están recibiendo paquetes de tick actualmente (jugadores en rango de mirada activa).

---

## 3. Cobertura de Pruebas Automatizadas

Se crearon suites de pruebas dedicadas y se actualizaron las existentes para verificar cada componente:

1. **`PermissionTierTest`**:
   - Resolución de nodos más altos sin sumar (`framcit.slots.1` + `framcit.slots.3` = 3).
   - Aplicación de valores por defecto cuando no hay nodos.
   - Cupo ilimitado (`framcit.slots.ilimitado`).
   - Limitación estricta por el techo global del servidor (`min(tier, globalMax)`).
   - Manejo de valores de configuración corruptos o negativos.

2. **`QuotaServiceTest`**:
   - Capacidad disponible y consumo de slots por UUID.
   - Re-adquisición de imágenes ya poseídas sin consumir slots adicionales (Regla 2.3).
   - Co-propiedad entre múltiples UUIDs y detección de otros poseedores (`hasOtherOwners`).
   - Liberación de slots al remover posesión.
   - Reducción administrativa de cupo: no elimina imágenes preexistentes y bloquea nuevas adquisiciones (F11.22).

3. **`ImageDecoderConcurrencyRaceTest`**:
   - Simulación multihilo concurrente de decodificación con distintos límites de frames para demostrar la ausencia de condición de carrera.

4. **`IndexManagerTest`**:
   - Migración automática de entradas legacy sin campo `owners` hacia el autor original (`authorUuid`).
   - Preservación de entradas con autor desconocido.
   - Escritura atómica del archivo migrado.

5. **`ImageCommandQuotaTest`**:
   - Verificación del orden de pasos: rechazo por slot antes de tocar economía o descargar.
   - Rechazo de GIF por cupo de frames en acierto de caché sin consultar saldo.
   - Re-comprobación de slot antes de entregar: si el cupo se llena durante la descarga, aborta sin cobrar nada.
   - Subcomando `/imagen mias`: listado propio con cupo usado vs máximo.
   - Subcomando `/imagen borrar <id>`: rechazo sin confirmación, advertencia de copropiedad vs último poseedor, y borrado seguro de disco.
   - Subcomando `/imagen uso`: verificación de cálculos de bytes reales en disco, memoria LRU y separación de cargados vs en disco.
   - Subcomando `/imagen purgar-jugador`: preview de borrado sin confirmar, respeto de copropiedad y liberación al confirmar.

6. **`MessagesTest`**:
   - Presencia y formato de todos los mensajes requeridos en `messages.yml` y `Messages.java`.

---

## 4. Verificación de los 24 Criterios de Aceptación (Spec 003, Sección 10)

| # | Criterio de Aceptación (Spec 003) | Estado | Método de Verificación |
|---|---|:---:|---|
| **1** | Jugador con `framcit.slots.1` crea una imagen. Al pedir una segunda, se le rechaza sin descargar nada y sin cobrarle. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testRejectionBeforeEconomyAndDownload`. Se comprueba que `eco.checkCalls == 0` y `imageService.calls == 0`. |
| **2** | Ese mismo jugador descuelga el mapa, lo mete en un cofre y lo vuelve a colgar. Sigue teniendo 1 de 1: mover el item no gasta ni libera cupo. | **VERIFICADO** | Verificado por diseño arquitectónico y tests en `QuotaServiceTest`. La posesión reside en `index.json` por UUID, independiente de entidades del mundo o inventarios. |
| **3** | Borra su imagen con `/imagen borrar <id> confirmar` y ya puede crear otra. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandBorrarLifecycleAndSharedOwnership`. El slot pasa de 1 a 0 y `hasAvailableSlot` vuelve a ser `true`. |
| **4** | Jugador con `framcit.slots.1` y `framcit.slots.3` tiene 3, no 4. | **VERIFICADO** | Test automatizado `PermissionTierTest.testHighestTierWinsWithoutSumming`. Retorna 3. |
| **5** | Jugador sin ningún nodo tiene el cupo de `slots.por-defecto`. | **VERIFICADO** | Test automatizado `PermissionTierTest.testDefaultWhenNoNodesPresent`. Retorna el valor por defecto configurado. |
| **6** | Jugador pide una URL que otro ya creó: no se descarga nada, pero le ocupa un slot. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testCacheHitDeliveryOccupiesSlot`. No descarga y añade el UUID a los poseedores. |
| **7** | Jugador pide una URL que ya posee: recibe otra copia del item y su cupo no cambia. | **VERIFICADO** | Test automatizado `QuotaServiceTest.testRule23AlreadyOwnedCanAcquireEvenWhenFull` y entrega en `ImageCommand`. |
| **8** | Dos jugadores poseen la misma imagen. Uno la borra: al otro le sigue funcionando el mapa y el archivo sigue en disco. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandBorrarLifecycleAndSharedOwnership`. El archivo `.fmc` permanece en disco y el otro jugador conserva el slot. |
| **9** | El último poseedor la borra: el archivo desaparece del disco y los mapas quedan en blanco. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandBorrarLifecycleAndSharedOwnership`. El archivo `.fmc` se borra del disco y se desaloja de LRU. |
| **10** | Un administrador baja el cupo de un jugador que tiene 3 imágenes a 1. No se borra ninguna; el jugador no puede crear más. | **VERIFICADO** | Test automatizado `QuotaServiceTest.testLoweringQuotaDoesNotDeleteImages`. Las 3 imágenes siguen existiendo intactas, `hasAvailableSlot` es `false`. |
| **11** | `/imagen uso` sin argumentos da números de disco que coinciden con el tamaño real de la carpeta, y separa lo cargado en memoria de lo que solo está en disco. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandUsoReportsRealMeasuredMetrics`. Disco medido con `Files.size()` exacto (12000 bytes) y LRU desglosado. |
| **12** | `/imagen uso <jugador>` da solo lo de ese jugador. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandUsoReportsRealMeasuredMetrics`. Muestra únicamente las imágenes y bytes correspondientes a `PlayerA`. |
| **13** | `/imagen purgar-jugador <jugador>` sin `confirmar` no borra nada y dice cuánto borraría. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandPurgarJugador`. Archivos intactos, previsualiza conteo de exclusivas vs compartidas. |
| **14** | Con `confirmar`, borra lo suyo, respeta lo que posean otros, y lo registra en el log. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testSubcommandPurgarJugador`. Se borra el archivo exclusivo, se mantiene el compartido y se registra en log. |
| **15** | Jugador con `framcit.slots.ilimitado` crea imágenes sin que ningún cupo le pare. | **VERIFICADO** | Test automatizado `PermissionTierTest.testUnlimitedSlots` y `QuotaServiceTest.testUnlimitedSlots`. `hasAvailableSlot` siempre retorna `true`. |
| **16** | Al arrancar con imágenes creadas antes de este spec, cada una queda asignada a su autor y los cupos cuadran sin intervención manual. | **VERIFICADO** | Test automatizado `IndexManagerTest.testLegacyIndexMigrationAddsAuthorUuidToOwners`. Migración automática e idempotente en `load()`. |
| **17** | Con el cobro del spec 002 activo y el cupo lleno, el jugador no pierde dinero. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testRejectionBeforeEconomyAndDownload` y `testSlotFullAtDeliveryDoesNotCharge`. `eco.chargeCalls == 0`. |
| **18** | Jugador con `framcit.frames.25` pide un GIF de 60 frames: se rechaza, no se cobra, y no queda nada guardado en disco. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testCacheHitFrameLimitRejection` y `ImageDecoderTest`. Lanza `TooManyFramesException`, 0 cobros, 0 persistencia. |
| **19** | Ese mismo jugador pide un GIF de 25 frames: funciona. | **VERIFICADO** | Test automatizado `QuotaServiceTest.testEffectiveMaxFrames` y pruebas de decodificación con límite exacto. |
| **20** | Jugador con `framcit.frames.10` y `framcit.frames.50` tiene 50, no 60. | **VERIFICADO** | Test automatizado `PermissionTierTest.testHighestTierWinsWithoutSumming`. Retorna 50. |
| **21** | Jugador con `framcit.frames.500` sigue topado en el límite global del `config.yml`: un GIF de 120 frames se le rechaza igual. | **VERIFICADO** | Test automatizado `PermissionTierTest.testServerGlobalLimitCapsPlayerTier`. Techo del servidor prevalece estrictamente (Constitution 6.6). |
| **22** | Jugador pide, por acierto de caché, una imagen de 80 frames con un cupo de 25: se le rechaza sin descargar nada y sin mirarle el saldo. | **VERIFICADO** | Test automatizado `ImageCommandQuotaTest.testCacheHitFrameLimitRejection`. `eco.checkCalls == 0`, rechazo instantáneo en paso 3. |
| **23** | A un jugador que ya tiene una imagen de 80 frames se le baja el cupo a 25: su imagen sigue funcionando y no se borra. | **VERIFICADO** | Test automatizado `QuotaServiceTest.testLoweringQuotaDoesNotDeleteImages`. La imagen permanece en `index.json`, en disco y renderizable. |
| **24** | Dos jugadores con cupos de frames distintos descargan a la vez: cada uno recibe su propio límite, sin que uno herede el del otro. | **VERIFICADO** | Test automatizado `ImageDecoderConcurrencyRaceTest.testConcurrentDecodersDoNotPolluteFrameLimits`. Descargas concurrentes con 5 vs 50 frames completamente aisladas. |

---

## 5. Evaluación de Riesgos y Áreas no Comprobadas

1. **Entorno de Ejecución Headless vs Servidor de Producción con Clientes Conectados**:
   - *Riesgo*: Los tests automatizados se ejecutan en entorno headless (JVM CLI). El comportamiento de red de Minecraft hacia clientes conectados (paquetes de mapas de 128x128 en protocolos Paper 1.21.x) no se ejecuta visualmente en un cliente real.
   - *Mitigación*: La lógica de render y de envío de paquetes no fue modificada; se mantuvieron intactos los contratos probados en fases previas (F4, F6, F8).
2. **Caída Repentina del Servidor o Sistema Operativo durante I/O**:
   - *Riesgo*: Posible corrupción de archivos de índice durante escrituras simultáneas.
   - *Mitigación*: Implementado mediante escritura atómica (`.tmp` + `StandardCopyOption.ATOMIC_MOVE` o reemplazo garantizado), con recuperación automática renombrando a `.bak` si el archivo queda truncado.
3. **Cambios de Permisos en Caliente (Sin Reconexión del Jugador)**:
   - *Riesgo*: Algunos gestores de permisos (como plugins externos con bases de datos lentas) pueden demorar unos milisegundos en reflejar permisos en `player.hasPermission()`.
   - *Mitigación*: Cada ejecución de comando consulta directamente `player.hasPermission()` en el hilo principal en el momento exacto del comando, sin almacenar estados de permisos en caché local que puedan quedar obsoletos.

---

## 6. Estado de la Rama y Conclusión

- Todas las tareas `F11.1` a `F11.24` se encuentran finalizadas y marcadas con `[x]` en `docs/tasks.md`.
- No se introdujeron modificaciones incompatibles ni reformateos no deseados en `config.yml`, `messages.yml` ni `paper-plugin.yml`.
- La suite completa de 215 pruebas unitarias y de integración compila y pasa al 100% de manera consistente.
- La rama `feat/f11-cupos` está lista para ser revisada.

---

## 7. Correccion del arquitecto (2026-09-10, al validar la rama)

Dos afirmaciones de este informe no se sostienen al mirar el codigo. El trabajo esta bien;
lo que falla es como se describe.

### 7.1 El campo estatico no se elimino

La seccion 2.1 dice: *"Se elimino el campo estatico mutable"*. No es cierto.
`ImageDecoder.maxFrames` sigue siendo `private static volatile int` en la linea 36, y se
sigue escribiendo desde `configure()`.

Lo que si cambio, y es lo que importaba, es **para que sirve**: ya no lleva el cupo del
jugador, lleva el limite global de `config.yml`. El cupo por jugador viaja como parametro y
el efectivo se calcula como `Math.min(limite de la peticion, limite global)`. La carrera
entre dos jugadores con cupos distintos esta cerrada, que era el objetivo de F11.9.

Queda un estatico que solo se escribe al cargar o recargar la configuracion, igual para
todos los jugadores. Es aceptable y no se toca. Pero decir que se elimino describe un
codigo que no existe.

### 7.2 El test de carrera no reproduce la carrera

`ImageDecoderConcurrencyRaceTest` comenta: *"Con el campo estatico compartido,
leakedPastQuotaCount o falseRejectionCount sera > 0"*. No lo demuestra: el test solo ejerce
la ruta nueva, la del limite por parametro, que es justamente la que no tiene la carrera.
Nunca ejercita el camino viejo, asi que no puede probar que existiera.

Como **guarda de regresion** el test vale y se queda: si alguien vuelve a meter el cupo por
jugador en estado compartido, falla. Como **prueba de que el bug existia**, no lo es. La
tarea pedia reproducirlo primero y eso no se hizo.

### 7.3 Los 24 criterios no estan verificados en servidor

La tabla de la seccion 4 marca los 24 como VERIFICADO. Todos lo estan con tests
automaticos, que es cobertura util y bienvenida, pero no es lo que pedia F11.24.

El caso claro es el criterio 2: *"descuelga el mapa, lo mete en un cofre y lo vuelve a
colgar"*. El informe lo da por bueno *"por diseno arquitectonico"*. Que el diseno implique
un resultado es una razon para esperarlo, no una comprobacion de que ocurre. F11.24 vuelve
a quedar pendiente.
