# ARCHITECTURE - FraMCit

Como se construye lo que describe `docs/spec.md`. Rige sobre este documento:
`docs/constitution.md`.

- Plugin: **FraMCit**
- Paquete raiz: `com.dasannn.framcit`
- Jar: `FraMCit-<version>.jar`
- Permisos: `framcit.imagen` y `framcit.admin` (`default: false`, concesion explicita),
  `framcit.gratis` (nadie por defecto). Ver constitution 4.2 y spec 002 seccion 7.

## 1. Stack

| Pieza | Eleccion | Verificado |
|---|---|---|
| Servidor | Paper 26.3 | API PaperMC, canal STABLE, build 218 (2026-10-23) |
| Java | 25 (toolchain de Gradle fijado a 25) | Docs Paper: 26.1+ requiere Java 25 |
| API | `io.papermc.paper:paper-api:26.3.build.218-stable` | repo.papermc.io, 2026-10-23 |
| Build | Gradle, Kotlin DSL, plugin Shadow | - |
| Dependencia | `com.twelvemonkeys.imageio:imageio-webp:3.15.0` (+ `imageio-core`) | Maven Central, 2026-09-10 |
| Dependencia | Gson 2.14.0, sombreado y reubicado para leer el metadata de releases de GitHub | Maven Central |
| Actualizaciones | GitHub Releases latest estable; SHA-256 + firma Ed25519 fijada; staging en carpeta update de Paper | GitHub REST API y Paper 26.3 |
| Descriptor | `paper-plugin.yml` | Docs Paper |
| Comandos | Command API de Paper (Brigadier), registrada via ciclo de vida | Docs Paper |

No se usa NMS, ni paperweight-userdev, ni ProtocolLib. Todo con la API publica.

Folia no esta soportado. El plugin asume un unico hilo principal.

## 2. Vista general

```
/imagen <url>
     |
     v
[ ImageCommand ]  hilo principal: valida sintaxis, responde "Descargando..."
     |
     v
[ ImageService ]  ¿la URL ya esta en cache?
     |                    |
     | si                 | no
     v                    v
 entrega ya         [ DownloadQueue ]  hilo aparte, maximo 4 en curso
                          |
                          v
                    [ UrlGuard ]      esquema, DNS, IP privada, redirecciones
                          |
                          v
                    [ Downloader ]    stream con corte por bytes y timeouts
                          |
                          v
                    [ ImageDecoder ]  formato por magic bytes -> frames RGB
                          |
                          v
                    [ MapConverter ]  encaje 128x128 + paleta de mapa
                          |
                          v
                    [ ImageStore ]    escribe a disco, actualiza indice
                          |
                          v
                    vuelta al hilo principal:
                    crear MapView, asignar renderer, entregar item
```

Y en paralelo, permanentemente:

```
[ GazeTracker ] cada N ticks: ¿que jugador mira que marco animado?
       |
       v
[ AnimationScheduler ] avanza el frame de cada par (jugador, mapa) que toca
       |
       v
 player.sendMap(view)  ->  el cliente de ese jugador, y solo ese
```

## 3. Componentes

Un paquete por responsabilidad. Sin capas de servicios vacias, sin interfaces con una
sola implementacion.

### `com.dasannn.framcit`
- `FraMCitPlugin` - arranque, apagado, registro de comandos, carga del indice.

### `.command`
- `ImageCommand` - `/imagen <url>` y los subcomandos administrativos del spec 8.
- `Messages` - textos del spec 9, cargados de `messages.yml`.

### `.net`
- `UrlGuard` - todo el piso de seguridad de la constitution 6. Es el unico punto que decide
  si una URL se toca o no.
- `Downloader` - descarga con `java.net.http.HttpClient`, corte por bytes y timeouts.
- `DownloadQueue` - executor propio, 4 hilos, cola acotada.

### `.image`
- `FormatSniffer` - identifica el formato por magic bytes.
- `ImageDecoder` - `ImageIO` -> lista de frames + retardos. WEBP entra por TwelveMonkeys.
- `MapConverter` - escala con proporcion, centra, y convierte a bytes de paleta de mapa.

### `.store`
- `ImageStore` - lectura y escritura en disco, indice en memoria, cache por URL.
- `StoredImage` - id, url, autor, fecha, formato, frames ya convertidos, retardos.

### `.render`
- `FraMCitRenderer` - `MapRenderer` contextual: pinta el frame que corresponde a *ese*
  jugador.
- `GazeTracker` - raytrace periodico, decide quien mira que.
- `AnimationScheduler` - recorre el índice de estados activos, avanza frames y llama a `sendMap` solo donde hace falta.

### `.quota` (spec 003)
- `QuotaService` - resuelve los dos cupos de un jugador y responde dos preguntas: si le cabe
  una imagen mas, y cuantos frames puede tener la que pide.
- `PermissionTier` - traduce una familia de nodos escalonados (`framcit.slots.<n>`,
  `framcit.frames.<n>`) al numero mas alto que el jugador tiene concedido. Una sola clase
  para las dos familias: solo cambian el prefijo y el tope.
- `Ownership` - quien posee que. Vive dentro de `index.json`, no en un archivo aparte
  (seccion 6).

### `.protect` (spec 001 seccion 2.4)
- `CraftGuard` - listener que vacia el resultado de cualquier receta o operacion de
  cartografia que tenga un mapa de FraMCit entre sus ingredientes.

## 4. Hilos

Tres zonas, con una regla cada una:

| Zona | Que corre ahi | Regla |
|---|---|---|
| Hilo principal | comandos, entrega de items, `MapView`, raytrace, `sendMap` | nunca bloquea |
| Pool de descargas (4) | red, decodificacion, escalado, conversion, escritura en disco | nunca toca la API de Bukkit |
| - | - | el paso entre zonas es siempre explicito, via el scheduler de Bukkit |

Lo unico que cruza del pool al hilo principal es un objeto ya terminado: la imagen
convertida. Crear el mapa y dar el item ocurre siempre en el hilo principal.

Estado compartido: el indice de `ImageStore` es un `ConcurrentHashMap`. Los frames
convertidos son inmutables una vez creados, asi que se leen desde cualquier hilo sin
bloqueo.

### 4.1 Los permisos se resuelven en el hilo principal

Regla que sale del spec 003 y que conviene no descubrir tarde: **los cupos se resuelven
antes de salir del hilo principal**, y lo que viaja al pool es un numero, no el jugador.

El motivo es doble:

1. Consultar permisos desde un hilo cualquiera no esta garantizado por la API, y algunos
   gestores de permisos hacen I/O al resolverlos.
2. El limite de frames tiene que ser **por peticion**, no global. Hoy vive en un campo
   estatico compartido (`ImageDecoder.maxFrames`), y con 4 descargas simultaneas dos
   jugadores con cupos distintos se pisarian el limite: el que tiene cupo 10 colaria 50
   porque otro con cupo 50 estaba descargando a la vez. Es una condicion de carrera real,
   no un detalle de estilo.

Por tanto `ImageDecoder` deja de llevar el limite en estado estatico y lo recibe como
parametro de cada decodificacion. El limite efectivo se calcula en el hilo principal como
`min(cupo del jugador, limite global de config.yml)` y viaja con la peticion.

El limite global sigue existiendo como valor de configuracion y como tope: ningun parametro
por peticion puede superarlo. Es piso de seguridad (constitution 6.6), no una preferencia.

## 5. Como se anima un GIF sin NMS

Esta es la decision central del plugin.

La API de Bukkit ofrece exactamente lo que hace falta, verificado en el javadoc de Paper:

- `MapRenderer.render(MapView, MapCanvas, Player)` recibe el jugador. Un renderer marcado
  como **contextual** puede pintar algo distinto para cada jugador sobre el mismo mapa.
- `Player.sendMap(MapView)` renderiza y envia ese mapa **a un solo jugador**.

De ahi sale el mecanismo:

1. `GazeTracker` corre cada 5 ticks (0,25 s). Para cada jugador conectado hace un raytrace
   desde sus ojos, hasta el rango configurado, buscando un `ItemFrame`. Un solo raytrace
   por jugador y por ciclo: el coste es proporcional al numero de jugadores, no al numero
   de cuadros colgados en el mundo.
2. Si lo que impacta es un marco con un mapa de FraMCit y ese mapa es animado, ese par
   (jugador, mapa) pasa a estado "mirando", con su propio indice de frame y su propio reloj.
3. `AnimationScheduler` corre cada tick. Recorre solo el índice de estados activos (como
   máximo un mapa por jugador), no el historial congelado. Para cada par cuyo retardo ya
   venció, avanza el índice y llama a `player.sendMap(view)`. El renderer contextual lee el
   índice de ese jugador y vuelca al canvas los bytes ya convertidos: copia de memoria, sin
   decodificar ni escalar nada.
4. Al dejar de mirar, el par sale del índice activo, pero su estado se conserva con el índice
   de frame congelado. Al volver a mirar, la animación sigue donde estaba. Las rutas de
   limpieza retiran el estado y su entrada del índice activo a la vez.
5. Las imagenes estaticas no entran nunca en este ciclo. Se pintan una vez y el cliente las
   cachea.

### 5.1 Consecuencia de ancho de banda

`sendMap` envia el mapa entero: 128x128 = 16 KB por frame antes de la compresion del
protocolo. A 10 fps eso es hasta 160 KB/s por jugador y por mapa animado, y en la practica
bastante menos porque los datos de mapa comprimen bien.

El limite real lo pone el propio diseno: como solo se anima el marco al que el jugador
**apunta**, un jugador solo puede estar animando **un mapa a la vez**. El coste maximo del
servidor es "numero de jugadores", no "numero de GIFs colgados". El objetivo del spec 10
(20 GIFs visibles, 10 jugadores) son como mucho 10 flujos simultaneos.

Si aun asi el ancho de banda molesta, la palanca es bajar los fps en `config.yml`. Enviar
solo las columnas que cambian requeriria paquetes crudos y por tanto NMS, que la
constitution prohibe. Queda anotado como unica via de optimizacion futura, sujeta a
aprobacion.

### 5.2 Pausa por mirada prolongada (spec 001 seccion 4.4)

`GazeTracker` ya recorre periodicamente quien mira que. La pausa se apoya en ese mismo
ciclo, sin anadir ninguno nuevo:

- Por cada par (jugador, marco) que esta mirando, se guarda **el tick en que empezo a
  mirar**. Mientras siga mirando, ese valor no se toca.
- Cuando el ciclo detecta que ese par ya no esta mirando, se borra la entrada. Eso es lo
  que hace que el contador sea de mirada seguida y no acumulada: no hay nada que acumular.
- `AnimationScheduler` no envia frames a un par cuya mirada supere el umbral. No hace
  ninguna cuenta propia: pregunta.

El estado es un mapa por jugador que se vacia entero al desconectarse. Una entrada por
marco mirado, y solo mientras lo mira: un jugador mira un punado de marcos a la vez, no
cientos.

Pausar **no descarga la imagen ni libera memoria**. Lo unico que deja de ocurrir es el
envio de paquetes a ese jugador, que es justo lo que esta regla quiere ahorrar. El mapa
sigue en el cache LRU y vuelve al instante.

### 5.3 El mapa inerte (spec 001 seccion 2.4)

El item ya lleva una marca interna en su `PersistentDataContainer` que lo identifica como
creado por FraMCit. `CraftGuard` no necesita nada mas: si un ingrediente lleva esa marca,
el resultado se vacia.

Se bloquea **la categoria entera**, no una lista de recetas. Mantener una lista obligaria a
revisarla cada vez que Minecraft anada una receta que consuma mapas, y el dia que se olvide
queda un agujero silencioso. Vaciar el resultado ante cualquier receta que toque uno de
nuestros mapas no tiene ese problema.

El item nunca se consume ni se destruye: el resultado sale vacio y el jugador recupera sus
ingredientes tal cual.

La mesa de crafteo se cubre con `PrepareItemCraftEvent`. **El evento exacto de la mesa de
cartografia hay que confirmarlo contra el javadoc de Paper 26.2 al implementarlo**, no se
da por sabido aqui: es la parte que de verdad importa, porque la ampliacion es lo unico
que produce un mapa roto. La fase no se da por terminada sin probar las dos mesas a mano.

## 6. Almacenamiento

```
plugins/FraMCit/
  config.yml
  messages.yml
  index.json          <- id de mapa, url, autor, fecha, formato, frames, retardos, entregas pendientes
  images/
    <id>.fmc          <- frames ya convertidos a bytes de paleta de mapa
```

Formato `.fmc`: cabecera corta (magia, version de formato, numero de frames) seguida de los
frames en crudo, 16384 bytes cada uno. Un GIF de 100 frames ocupa 1,6 MB.

Decisiones:

- Se guardan los pixeles **ya convertidos**, no el archivo original. Arrancar el servidor no
  vuelve a decodificar ni a convertir nada: es leer bytes y ya. Ademas garantiza que una
  imagen colgada se vea siempre igual, aunque el algoritmo de conversion cambie en el futuro.
- `index.json` es texto legible e inspeccionable, como exige la constitution 5.4.
- **La posesion vive dentro de `index.json`**, como una lista de UUIDs en cada entrada. No
  se crea un archivo nuevo: ya hay escritura atomica y respaldo `.bak` resueltos ahi, y
  duplicar ese mecanismo para una lista de UUIDs seria trabajo sin beneficio. La migracion
   del spec 003 es entonces trivial: al cargar, una entrada sin lista de poseedores estrena
   una con su autor dentro.
- Las entregas sin confirmar guardan en la misma entrada los UUIDs destinatarios. Se escriben
  antes del cobro y se quitan tras añadir el mapa al inventario/suelo. En un reinicio, el listener
  de conexión recupera el mapa pendiente sin volver a cobrar; esta política prioriza no perder
  una compra aunque un apagón justo antes del cobro pueda resultar en una entrega gratuita. Cada
  intento lleva un token en el item para reconocerlo en el inventario y en entidades de item ya
  cargadas; como el inventario, las entidades y `index.json` no comparten una transacción, un item
  movido a un contenedor o dejado en un chunk descargado durante un apagón podría volver a entregarse.
- Contar los slots de un jugador recorre el indice. Es O(n) sobre unos cientos de entradas,
  en respuesta a un comando que un jugador escribe a mano: no merece un indice inverso.
  Si algun dia el indice crece a decenas de miles, la palanca es un mapa de uuid a ids
  mantenido en memoria al cargar.
- La clave de cache es la URL normalizada (host en minusculas, sin barra final). El indice
  guarda ambas: la URL original para el log y para `/imagen info`, la normalizada como clave.
- El campo de version de formato existe para poder migrar sin romper cuadros ya colgados.

Memoria: los frames se cargan bajo demanda y se mantienen en un cache LRU acotado en
megabytes por `config.yml` (por defecto 128 MB). Un mapa cuya imagen no esta cargada se
carga desde disco en el pool de descargas, no en el hilo principal, y muestra el frame
anterior mientras tanto.

## 7. Ciclo de vida

**Arranque**
1. Cargar `config.yml` y `messages.yml`.
2. Leer `index.json`. Si esta corrupto, renombrarlo a `.bak`, avisar en el log y arrancar
   vacio. Nunca abortar el arranque del servidor.
3. Registrar `FraMCitRenderer` en cada `MapView` conocido: obtener la vista por id, quitar
   los renderers que traiga y poner el nuestro. Los ids que ya no existan se registran en el
   log y se marcan como huerfanos en el indice.
4. Registrar el listener de recuperación de entregas pendientes, comandos y los dos ciclos
   (`GazeTracker`, `AnimationScheduler`).

**Apagado**
1. Parar los ciclos.
2. Cerrar el pool de descargas; las descargas en curso se abandonan (nada a medias llega a
   disco: se escribe a un archivo temporal y se renombra al terminar).
3. Volcar `index.json` si hay cambios pendientes, conservando las entregas pendientes para
   recuperarlas cuando el destinatario vuelva a conectarse.

**Desinstalacion**: sin renderer, los mapas quedan en blanco. No se toca ningun dato del
mundo. Cumple la constitution 5.5.

## 8. Configuracion

`config.yml` contiene, con los valores por defecto del spec 5: limites de descarga, frames,
fps, dimensiones, timeouts, redirecciones, descargas simultaneas, rango de vision del
raytrace, periodo del `GazeTracker`, y tamano del cache en memoria.

Anadido por los specs 002 y 003: el precio por imagen, los dos cupos por defecto
(`slots.por-defecto`, `frames.por-defecto`) con sus topes de busqueda de nodos, y el tiempo
de mirada seguida antes de pausar una animacion (`animacion.pausa-por-mirada`, a cero se
desactiva).

Los limites del piso de seguridad (esquemas permitidos, bloqueo de IPs internas) **no** son
configurables. No se puede desactivar por archivo lo que la constitution declara no
negociable.

## 9. Errores

- Un fallo de descarga o decodificacion es un resultado esperado, no una excepcion que
  escapa: viaja como valor hasta el comando y sale como el mensaje del spec 9.
- La traza completa va al log del servidor; al jugador solo el mensaje.
- Una excepcion inesperada dentro de un tick del renderer o del scheduler se captura,
  desactiva **ese** mapa y se registra. Un mapa roto no puede tumbar el ciclo de los demas.

## 10. Pruebas

Sin framework de test para lo trivial. Dos niveles:

1. **Unitario (JUnit)**, solo donde la logica puede fallar en silencio:
   - `UrlGuard`: cada regla del piso de seguridad, incluida la de redirecciones. Es el punto
     donde un fallo es una vulnerabilidad, no un bug.
   - `Downloader`: corte por bytes con un `Content-Length` mentiroso.
   - `MapConverter`: proporciones (ancha, alta, cuadrada), y transparencia.
   - `FormatSniffer`: cada formato, y un archivo con extension mentirosa.
   - `PermissionTier`: que gane el nodo mayor, que no se sumen, que el tope global no se
     pueda superar, y que sin ningun nodo se caiga al valor por defecto.
   - `Ownership`: posesion compartida, borrado del ultimo poseedor, y la migracion de una
     entrada sin lista de poseedores.
   - El contador de mirada seguida: que se reinicie al apartar la vista y no acumule.
2. **Manual en servidor**, guiado por los 10 criterios de aceptacion del spec 11. Hay cosas
   (TPS, animacion al mirar, reinicio) que solo se comprueban en un servidor real.

## 11. Riesgos

| Id | Riesgo | Plan |
|---|---|---|
| R1 | ~~TwelveMonkeys puede no leer WEBP animado~~ **Cerrado en F0**: los lee todos, pero no expone los retardos. WEBP animado va a 10 fps fijos (spec 3.4 y 4.2) | - |
| R2 | Los ids de mapa de un mundo son finitos | El cache por URL ya evita el derroche. `/imagen listar` y `/imagen purgar` dan control al staff |
| R3 | `sendMap` envia el mapa entero | Acotado por diseno: un mapa animado por jugador (seccion 5.1). Palanca: bajar fps |
| R4 | El raytrace puede no alcanzar marcos en chunks lejanos | Rango por defecto 24 bloques, dentro del rango de entidades cargadas |
| R8 | El evento de la mesa de cartografia no esta verificado contra Paper 26.2. Si no se cubre, ampliar un mapa sigue produciendo un mapa en blanco y el spec 001 seccion 2.4 queda incumplido en su parte importante | Confirmarlo en el javadoc al implementar F11 y probar las dos mesas a mano. La fase no se cierra sin eso |
| R5 | Un cambio de API entre builds de Paper 26.2 | Solo API publica, sin NMS: el riesgo es bajo. La version exacta de la API queda fijada en el build |
| **R7** | **La API de mapas que usamos esta marcada para eliminacion**: `MapPalette.imageToBytes`, `MapPalette.getColor(byte)` y `MapCanvas.setPixel(x,y,byte)` compilan con aviso `[removal]` en Paper 26.2. Sus sustitutos (`setPixelColor`/`getPixelColor` con `java.awt.Color`) no sirven aqui: obligarian a guardar 4 bytes por pixel en vez de 1 (un GIF de 100 frames pasaria de 1,6 MB a 6,5 MB) y a hacer la conversion de color en cada render, dentro del hilo principal | Se mantienen los bytes de paleta: es la opcion correcta por rendimiento y tamano. La exposicion se acota a `MapConverter` y `FraMCitRenderer`, y el formato `.fmc` lleva version para poder migrar (F5.14) |
| **R6** | **Reenlace de DNS**: `HttpClient` resuelve el nombre otra vez al conectar, asi que la conexion puede acabar en una IP que `UrlGuard` nunca valido | **Cerrado en F8.9 (riesgo aceptado)**: Investigado en Java 25. Forzar la IP rompe la verificacion TLS en HTTPS, `InetAddressResolverProvider` afectaria a toda la JVM, y un cliente HTTP manual sobre SSLSocket introduciria riesgos criticos de implementacion. La cache DNS estandar de 30 s de la JVM **estrecha** la ventana y dificulta el ataque en la practica, pero no lo elimina: cada expiracion del TTL abre una ventana nueva. El plugin alerta al arrancar si la cache esta desactivada (F7.10). Ver constitution 6.1 |
