# ARCHITECTURE - FraMCit

Como se construye lo que describe `docs/spec.md`. Rige sobre este documento:
`docs/constitution.md`.

- Plugin: **FraMCit**
- Paquete raiz: `com.dasannn.framcit`
- Jar: `FraMCit-<version>.jar`
- Permisos: `framcit.imagen` (todos), `framcit.admin` (solo operadores)

## 1. Stack

| Pieza | Eleccion | Verificado |
|---|---|---|
| Servidor | Paper 26.2 | API PaperMC, canal STABLE, build 123 (2026-09-09) |
| Java | 25 (toolchain de Gradle fijado a 25) | Docs Paper: 26.1+ requiere Java 25 |
| API | `io.papermc.paper:paper-api:26.2.build.123-stable` | repo.papermc.io, 2026-09-10 |
| Build | Gradle, Kotlin DSL, plugin Shadow | - |
| Dependencia | `com.twelvemonkeys.imageio:imageio-webp:3.15.0` (+ `imageio-core`) | Maven Central, 2026-09-10 |
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
- `AnimationScheduler` - avanza frames y llama a `sendMap` solo donde hace falta.

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
3. `AnimationScheduler` corre cada tick. Para cada par en estado "mirando" cuyo retardo ya
   vencio, avanza el indice y llama a `player.sendMap(view)`. El renderer contextual lee el
   indice de ese jugador y vuelca al canvas los bytes ya convertidos: es una copia de
   memoria, sin decodificar ni escalar nada.
4. Al dejar de mirar, el par se marca inactivo. El indice de frame se conserva, asi que al
   volver a mirar la animacion sigue donde estaba, como pide el spec 4.1.
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

## 6. Almacenamiento

```
plugins/FraMCit/
  config.yml
  messages.yml
  index.json          <- id de mapa, url, autor, fecha, formato, frames, retardos
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
4. Registrar comandos y arrancar los dos ciclos (`GazeTracker`, `AnimationScheduler`).

**Apagado**
1. Parar los ciclos.
2. Cerrar el pool de descargas; las descargas en curso se abandonan (nada a medias llega a
   disco: se escribe a un archivo temporal y se renombra al terminar).
3. Volcar `index.json` si hay cambios pendientes.

**Desinstalacion**: sin renderer, los mapas quedan en blanco. No se toca ningun dato del
mundo. Cumple la constitution 5.5.

## 8. Configuracion

`config.yml` contiene, con los valores por defecto del spec 5: limites de descarga, frames,
fps, dimensiones, timeouts, redirecciones, descargas simultaneas, rango de vision del
raytrace, periodo del `GazeTracker`, y tamano del cache en memoria.

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
2. **Manual en servidor**, guiado por los 10 criterios de aceptacion del spec 11. Hay cosas
   (TPS, animacion al mirar, reinicio) que solo se comprueban en un servidor real.

## 11. Riesgos

| Id | Riesgo | Plan |
|---|---|---|
| R1 | ~~TwelveMonkeys puede no leer WEBP animado~~ **Cerrado en F0**: los lee todos, pero no expone los retardos. WEBP animado va a 10 fps fijos (spec 3.4 y 4.2) | - |
| R2 | Los ids de mapa de un mundo son finitos | El cache por URL ya evita el derroche. `/imagen listar` y `/imagen purgar` dan control al staff |
| R3 | `sendMap` envia el mapa entero | Acotado por diseno: un mapa animado por jugador (seccion 5.1). Palanca: bajar fps |
| R4 | El raytrace puede no alcanzar marcos en chunks lejanos | Rango por defecto 24 bloques, dentro del rango de entidades cargadas |
| R5 | Un cambio de API entre builds de Paper 26.2 | Solo API publica, sin NMS: el riesgo es bajo. La version exacta de la API queda fijada en el build |
| **R6** | **Reenlace de DNS**: `HttpClient` resuelve el nombre otra vez al conectar, asi que la conexion puede acabar en una IP que `UrlGuard` nunca valido | Hoy lo tapa la cache de DNS de la JVM, que no es garantia de diseno. Aviso al arrancar si la cache esta desactivada (F7.10) y fijado de IP pendiente (F8.9). Ver constitution 6.1 |
