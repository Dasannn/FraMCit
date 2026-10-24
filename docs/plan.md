# Plan de implementacion - FraMCit

Orden de construccion. Que se entrega en cada fase y como se comprueba que esta hecho.

Rigen sobre este documento: `docs/constitution.md`, `docs/spec.md`, `ARCHITECTURE.md`.

## 1. Metodo de trabajo

- **Una rama y un worktree por fase.** Nombre: `feat/f<N>-<slug>`, worktree en
  `../framcit-f<N>-<slug>`.
- Cada fase termina con la rama lista para revisar. **El merge lo hace el owner**, nunca el
  agente (AGENTS.MD).
- Ninguna fase empieza hasta que la anterior esta mergeada, salvo donde se indica que son
  independientes.
- Cada fase incluye sus propias pruebas. No hay una "fase de testing" al final.

## 2. Entorno

| Pieza | Estado |
|---|---|
| Paper 26.3 (Java 25) | Servidor de pruebas disponible en Crafty |
| Bucle de desarrollo | Build local; pruebas de integración en la copia Paper 26.3 cuando se autorice |

## 3. Bloqueante conocido antes de F1

**Repositorio anidado.** `plugin/` es un repositorio git independiente dentro de este
repositorio, con un unico commit vacio y sin remoto. Dos repos anidados rompen los
worktrees y el seguimiento de cambios.

Propuesta: borrar `plugin/.git` y quedarse con un solo repositorio, con el codigo en
`plugin/`. **Requiere confirmacion del owner antes de tocar nada.** Es una fase de un
minuto (F1) pero no se ejecuta sin permiso.

**Riesgo A1 - red saliente en Aternos: cerrado por obsolescencia (2026-09-10).** Aternos no
permite subir plugins propios, asi que dejo de ser el destino del plugin. La validacion se
hace contra un servidor Paper 26.2 local. El trafico saliente quedo comprobado ahi: las
descargas funcionan. F9 se elimina del plan.

## 4. Fases

### F0 - Spike de WEBP animado (riesgo R1)

Independiente del resto. Se puede hacer en paralelo con F1.

- Programa Java suelto, fuera del plugin, que use TwelveMonkeys 3.15.0 para leer tres WEBP
  animados reales y contar los frames que devuelve.
- **Hecho cuando**: sabemos si la libreria lee todos los frames o solo el primero, y el
  resultado esta anotado en `docs/spec.md` seccion 3.4 cerrando R1.
- Sin esto, F3 y F6 se disenan a ciegas para WEBP.

### F1 - Esqueleto y build

- Un solo repositorio (resuelto el bloqueante de la seccion 3).
- Proyecto Gradle con Kotlin DSL, toolchain Java 25, plugin Shadow.
- Dependencias: `paper-api:26.2.build.123-stable`, TwelveMonkeys `imageio-webp:3.15.0` +
  `imageio-core`, empaquetadas y reubicadas por Shadow.
- `paper-plugin.yml` y la clase `FraMCitPlugin`, que arranca y apaga sin hacer nada mas.
- `/imagen` registrado con la Command API de Paper: acepta el argumento y responde un
  marcador de posicion.
- **Hecho cuando**: `gradlew shadowJar` produce el jar, el Paper 26.2 local arranca con el
  plugin cargado sin avisos, y `/imagen hola` responde en el chat.

### F2 - Seguridad de red

Antes que cualquier funcionalidad. Es el punto donde un fallo es una vulnerabilidad.

- `UrlGuard`: esquema, resolucion DNS, bloqueo de loopback / link-local / rangos privados en
  IPv4 e IPv6, revalidacion en cada redireccion, limite de redirecciones.
- `Downloader` con `java.net.http.HttpClient`: timeouts de conexion y lectura, corte por
  bytes durante el stream ignorando `Content-Length`.
- `DownloadQueue`: 4 hilos, cola acotada, rechazo con mensaje cuando esta llena.
- Tests JUnit: una prueba por regla, incluidas `127.0.0.1`, `10.x`, `192.168.x`, `[::1]`,
  un host publico que redirige a una IP privada, y un servidor local que declara
  `Content-Length: 1000` y manda 500 MB.
- **Hecho cuando**: todos los tests pasan y ninguna regla del piso de seguridad de la
  constitution 6 queda sin cubrir por un test.

### F3 - Decodificacion y conversion a mapa

- `FormatSniffer` por magic bytes (PNG, JPEG, BMP, GIF, WEBP).
- `ImageDecoder`: frames y retardos. GIF animado por `ImageIO`, WEBP segun el resultado de
  F0. Aplicacion de los limites de frames y dimensiones antes de decodificar.
- `MapConverter`: escalado con proporcion, centrado, relleno transparente, conversion a la
  paleta de mapa.
- Tests JUnit: proporciones ancha / alta / cuadrada, transparencia, extension mentirosa,
  GIF de 101 frames rechazado, imagen de 5000x5000 rechazada.
- **Hecho cuando**: los tests pasan y una imagen de prueba convertida, volcada a PNG desde
  el test, se ve correcta a ojo.

### F4 - Mapa estatico de punta a punta

Primera fase con resultado visible dentro del juego.

- `ImageService` uniendo comando, cola, descarga, decodificacion y conversion.
- Creacion del `MapView`, `FraMCitRenderer` en su version estatica, item entregado con
  nombre, lore y marca interna.
- Mensajes del spec 9 y registro en el log de quien cargo que URL.
- Manejo de inventario lleno y de jugador desconectado a mitad.
- **Hecho cuando**: `/imagen <url de PNG>` da un mapa que muestra la imagen en un marco.
  Criterio de aceptacion 1 cumplido, y los mensajes de error del spec 9 se pueden provocar
  uno a uno.

### F5 - Persistencia y cache

- `ImageStore`: formato `.fmc`, `index.json`, escritura a temporal y renombrado.
- Cache por URL normalizada.
- Re-vinculacion de renderers al arrancar; indice corrupto renombrado a `.bak` sin abortar
  el arranque.
- Cache LRU en memoria acotado en MB, con carga desde disco fuera del hilo principal.
- **Hecho cuando**: criterios de aceptacion 4 (reinicio) y 8 (misma URL, una sola descarga)
  cumplidos. Borrar un `.fmc` a mano deja ese mapa en blanco y no rompe nada mas.

### F6 - Animacion de GIF

El nucleo del proyecto.

- `FraMCitRenderer` contextual, con indice de frame por jugador.
- `GazeTracker`: raytrace cada 5 ticks, rango configurable, deteccion de `ItemFrame` con
  mapa de FraMCit.
- `AnimationScheduler`: avance por reloj propio de cada par, tope de fps, `sendMap` solo a
  quien mira.
- Congelado al apartar la vista, reanudacion en el mismo frame.
- Mapa animado sostenido en la mano (spec 4.3).
- **Hecho cuando**: criterios de aceptacion 2 y 3 cumplidos, verificados con dos clientes
  conectados a la vez.

### F7 - Configuracion y comandos de administracion

- `config.yml` con todos los valores del spec 5 mas rango de vision, periodo del tracker y
  tamano de cache. Los limites de seguridad quedan fuera del archivo, a proposito.
- `messages.yml` con los textos del spec 9.
- `/imagen recargar`, `/imagen info`, `/imagen purgar`, `/imagen listar`, con permiso
  `framcit.admin`.
- **Hecho cuando**: cambiar un limite en `config.yml` y recargar surte efecto sin reiniciar,
  y `/imagen info` mirando a un cuadro dice quien lo puso y de que URL.

### F8 - Rendimiento

- Medicion con 20 GIFs colgados y varios jugadores mirando, usando `/timings` o `/spark`.
- Revision de que nada de red, decodificacion ni disco ocurre en el hilo principal.
- Ajuste de valores por defecto si la medicion lo pide.
- **Hecho cuando**: criterios de aceptacion 7, 9 y 10 cumplidos, con los numeros anotados en
  el propio informe de la fase.

### F11 - Cupos por jugador (spec 003)

Implementa `docs/spec-003-slots.md` entero: el cupo de imagenes poseidas y el cupo de
frames por imagen, los comandos que los acompanan, y el registro de posesion.

- Resolucion de nodos escalonados y los dos valores por defecto de `config.yml`.
- Posesion dentro de `index.json`, con migracion de lo que ya existe.
- El orden de comprobaciones de la seccion 4 del spec 003, integrado con el cobro del
  spec 002.
- Comandos de jugador (`mias`, `borrar`) y de staff (`uso`, `purgar-jugador`, `listar`
  filtrado).
- **Refactor obligatorio**: `ImageDecoder` deja de llevar el limite de frames en un campo
  estatico. Es una condicion de carrera con 4 descargas simultaneas, no una preferencia
  de estilo.
- **Hecho cuando**: los 24 criterios de aceptacion del spec 003 se cumplen.

### F12 - Pausa por mirada y mapa inerte (enmiendas del spec 001)

Implementa las secciones 2.4 y 4.4 del spec 001, anadidas el 2026-09-10.

- Pausa de la animacion tras una mirada seguida mas larga que el umbral, por jugador y por
  marco, apoyada en el ciclo que ya tiene `GazeTracker`.
- El mapa de FraMCit queda inerte para el crafteo y la cartografia.
- **Riesgo R8, y es el trabajo de verdad de esta fase**: el evento de la mesa de
  cartografia no esta verificado contra Paper 26.2. Confirmarlo en el javadoc antes de
  escribir el listener. Si no se cubre, ampliar un mapa sigue dando un mapa en blanco y la
  regla queda incumplida en lo que importa.
- **Hecho cuando**: la animacion se detiene y se reanuda como describe la seccion 4.4, y
  ninguna de las dos mesas produce nada a partir de un mapa de FraMCit.

F11 y F12 son **independientes**: no comparten codigo. Solo se tocan en `config.yml` y
`messages.yml`, que se resuelven al fusionar. Pueden ir en paralelo.

## 5. Orden y dependencias

```
F0 (spike WEBP) ----------------\
                                 v
F1 esqueleto -> F2 seguridad -> F3 imagen -> F4 estatico -> F5 persistencia -> F6 animacion
                                                                                    |
                                                                 F7 config/admin <--/
                                                                        |
                                                                 F8 rendimiento

F7 config/admin -> F10 economia (spec 002)
F10 economia ----> F11 cupos (spec 003)
F6 animacion ----> F12 pausa por mirada y mapa inerte
```

F11 y F12 vuelven a abrir el paralelismo: son ramas distintas del arbol, como lo fue F0.

F0 es el unico que corre en paralelo. El resto es una cadena: cada fase se apoya en la
anterior.

## 6. Que NO se hace

Fuera de este plan, y fuera del proyecto salvo aprobacion nueva: mosaicos multi-marco,
busqueda por palabras clave, moderacion de contenido, soporte de Folia, soporte
multi-version, y envio de columnas parciales por paquetes crudos.

## 7. Riesgos del plan

| Id | Riesgo | Fase | Plan |
|---|---|---|---|
| R1 | WEBP animado sin verificar | F0 | Spike antes de disenar el decodificador |
| A2 | `plugin/` es un repositorio anidado | F1 | Borrar `plugin/.git`, previa confirmacion del owner |
| A3 | El servidor de destino puede limitar RAM y disco | - | Ajustar el cache en memoria y avisar del consumo de `images/` cuando se conozca el destino |
| R8 | El evento de la mesa de cartografia no esta verificado contra Paper 26.2 | F12 | Confirmarlo en el javadoc antes de escribir el listener, y probar las dos mesas a mano |
