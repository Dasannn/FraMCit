# Tareas - FraMCit

Desglose ejecutable de `docs/plan.md`. Una tarea = una unidad de trabajo con un resultado
comprobable.

Rigen sobre este documento: `docs/constitution.md`, `docs/spec.md`, `ARCHITECTURE.md`,
`docs/plan.md`.

## Convenciones

- Id de tarea: `F<fase>.<numero>`.
- Cada fase se trabaja en su propio worktree y rama:
  `git worktree add ../framcit-f<N>-<slug> -b feat/f<N>-<slug>`
- El merge lo hace siempre el owner. El agente no mergea ni trabaja en la rama principal.
- Una tarea marcada `[SEC]` toca el piso de seguridad: no se simplifica, no se pospone, y
  no se da por hecha sin su test.
- Estado: `[ ]` pendiente, `[x]` hecha, `[-]` descartada (con motivo escrito al lado).

---

## F0 - Spike de WEBP animado

Rama: `feat/f0-spike-webp` - Worktree: `../framcit-f0-spike-webp`
Independiente. Puede correr en paralelo con F1.

- [x] **F0.1** Proyecto Java suelto (fuera del plugin) con TwelveMonkeys
      `imageio-webp:3.15.0` e `imageio-core` como unicas dependencias.
- [x] **F0.2** Reunir tres WEBP animados reales de origen distinto y un WEBP estatico.
- [x] **F0.3** Leer cada uno con `ImageIO` y contar frames y retardos que devuelve el reader.
- [x] **F0.4** Anotar el resultado en `docs/spec.md` seccion 3.4, cerrando **R1** con un si
      o un no.
- [-] **F0.5** No aplica: TwelveMonkeys lee todos los frames (verificado en F0.3 y cerrado en F0.4).

**Fase terminada cuando**: R1 esta cerrado en el spec con evidencia, no con suposicion.

---

## F1 - Esqueleto y build

Rama: `feat/f1-esqueleto` - Worktree: `../framcit-f1-esqueleto`

- [x] **F1.1** Borrar `plugin/AGENTS.MD` y `plugin/claude.MD` (copias de los de la raiz).
- [x] **F1.2** Estructura Gradle en `plugin/`: `settings.gradle.kts`, `build.gradle.kts`,
      wrapper.
- [x] **F1.3** Toolchain Java 25 y plugin Shadow.
- [x] **F1.4** Repositorio `repo.papermc.io` y dependencia
      `io.papermc.paper:paper-api:26.2.build.123-stable` con alcance `compileOnly`.
- [x] **F1.5** Dependencias TwelveMonkeys (`imageio-webp`, `imageio-core`) empaquetadas y
      **reubicadas** por Shadow, para no chocar con otros plugins que las usen.
- [x] **F1.6** `paper-plugin.yml`: nombre `FraMCit`, version, `main`, `api-version`,
      permisos `framcit.imagen` (default true) y `framcit.admin` (default op).
- [x] **F1.7** Clase `FraMCitPlugin` con arranque y apagado, sin logica.
- [x] **F1.8** Registrar `/imagen <url>` con la Command API de Paper, con sus alias, que de
      momento solo devuelve un mensaje de marcador.
- [x] **F1.9** `.gitignore` para Gradle (`build/`, `.gradle/`).
- [x] **F1.10** README corto: como compilar y donde dejar el jar.

**Fase terminada cuando**: `gradlew shadowJar` genera el jar, el Paper 26.2 local arranca
con el plugin sin avisos en consola, y `/imagen hola` responde en el chat.

---

## F2 - Seguridad de red [SEC]

Rama: `feat/f2-seguridad-red` - Worktree: `../framcit-f2-seguridad-red`

- [x] **F2.1** [SEC] `UrlGuard`: aceptar solo `http` y `https`, rechazar todo lo demas.
- [x] **F2.2** [SEC] `UrlGuard`: resolver el host a IP y rechazar loopback, link-local,
      privadas y cualquier destino no publico, en IPv4 e IPv6.
- [x] **F2.3** [SEC] Redirecciones seguidas a mano, revalidando el destino en **cada** salto,
      con el limite configurado.
- [x] **F2.4** `Downloader` con `java.net.http.HttpClient`: timeout de conexion y de lectura.
- [x] **F2.5** [SEC] Corte por bytes durante el stream, sin confiar en `Content-Length`.
- [x] **F2.6** `DownloadQueue`: executor de 4 hilos, cola acotada, rechazo limpio cuando esta
      llena.
- [x] **F2.7** Tipo de resultado que lleva el fallo como valor (no como excepcion suelta)
      hasta el comando.
- [x] **F2.8** [SEC] Test: `127.0.0.1`, `localhost`, `10.0.0.1`, `192.168.1.1`,
      `172.16.0.1`, `[::1]`, `169.254.x`.
- [x] **F2.9** [SEC] Test: host publico que redirige a IP privada. Debe rechazarse en el
      salto, no antes ni despues.
- [x] **F2.10** [SEC] Test: servidor local que declara `Content-Length: 1000` y manda
      muchisimo mas. Debe cortarse en el limite.
- [x] **F2.11** Test: timeout con servidor que acepta y nunca responde.
- [x] **F2.12** Test: esquemas `file:`, `ftp:`, `jar:`, `data:` rechazados.

**Fase terminada cuando**: todos los tests pasan y cada regla de la constitution 6 tiene al
menos un test que la cubre.

---

## F3 - Decodificacion y conversion

Rama: `feat/f3-imagen` - Worktree: `../framcit-f3-imagen`

- [x] **F3.1** `FormatSniffer` por magic bytes: PNG, JPEG, BMP, GIF, WEBP. Nada de fiarse de
      la extension ni del `Content-Type`.
- [x] **F3.2** Registrar el reader de TwelveMonkeys al arrancar el plugin.
- [x] **F3.3** `ImageDecoder` para imagenes estaticas.
- [x] **F3.4** `ImageDecoder` para GIF animado: frames y retardo de cada uno, resolviendo los
      modos de composicion del GIF (frames parciales sobre el anterior).
- [x] **F3.5** WEBP animado segun el resultado de F0.
- [x] **F3.6** [SEC] Comprobar dimensiones y numero de frames **antes** de decodificar
      entero, leyendo solo la cabecera.
- [x] **F3.7** Normalizar retardos: minimo segun el tope de fps, valor por defecto si el GIF
      declara cero o basura.
- [x] **F3.8** `MapConverter`: escalar manteniendo proporcion, centrar en 128x128, rellenar
      con transparente.
- [x] **F3.9** `MapConverter`: conversion a paleta de mapa conservando la transparencia total.
- [x] **F3.10** Test: imagen ancha, alta y cuadrada. Sin deformacion, centrada.
- [x] **F3.11** Test: PNG con transparencia, que se mantiene transparente.
- [x] **F3.12** Test: PNG renombrado a `.jpg` se detecta como PNG.
- [x] **F3.13** Test: GIF de 101 frames rechazado, de 100 aceptado.
- [x] **F3.14** Test: imagen de 5000x5000 rechazada por cabecera, sin decodificarla.
- [x] **F3.15** Volcar una conversion a PNG desde un test para revisarla a ojo.

**Fase terminada cuando**: los tests pasan y la imagen volcada se ve bien.

---

## F4 - Mapa estatico de punta a punta

Rama: `feat/f4-mapa-estatico` - Worktree: `../framcit-f4-mapa-estatico`

- [x] **F4.1** `ImageService`: une comando, cola, descarga, decodificacion y conversion.
- [x] **F4.2** Salto explicito de vuelta al hilo principal al terminar el trabajo asincrono.
- [x] **F4.3** Crear el `MapView` y asignarle `FraMCitRenderer` en su version estatica.
- [x] **F4.4** Construir el item: nombre desde el archivo o el dominio, lore con origen,
      autor y fecha.
- [x] **F4.5** Marca interna en el item y en el mapa que identifica a FraMCit.
- [x] **F4.6** Entrega al inventario; si esta lleno, soltar al suelo y avisar por chat.
- [x] **F4.7** Cancelar la entrega si el jugador se desconecto durante la descarga.
- [x] **F4.8** `Messages` con todos los textos del spec 9, en espanol.
- [x] **F4.9** Mensaje inmediato de "Descargando imagen..." al ejecutar el comando.
- [x] **F4.10** Mensaje final que distingue imagen estatica de GIF y dice cuantos frames tiene.
- [x] **F4.11** [SEC] Log obligatorio: jugador, UUID, URL completa, tamano, formato, id de
      mapa.
- [x] **F4.12** Comprobacion de permiso y rechazo de ejecucion desde consola.
- [x] **F4.13** Que ningun mensaje al jugador filtre rutas, IPs internas ni trazas.
- [x] **F4.14** Prueba manual: provocar cada error del spec 9 uno a uno y comprobar el texto.

**Fase terminada cuando**: criterio de aceptacion 1 cumplido y la tabla de mensajes del spec
9 verificada entera.

---

## F5 - Persistencia y cache

Rama: `feat/f5-persistencia` - Worktree: `../framcit-f5-persistencia`

- [ ] **F5.1** Formato `.fmc`: cabecera con magia, version de formato y numero de frames,
      seguida de los frames en crudo.
- [ ] **F5.2** Escritura a archivo temporal y renombrado atomico, para no dejar archivos a
      medias.
- [ ] **F5.3** `index.json`: id de mapa, URL original, URL normalizada, autor, fecha, formato,
      frames, retardos.
- [ ] **F5.4** Normalizacion de URL para la clave de cache (host en minusculas, barra final).
- [ ] **F5.5** Acierto de cache: segunda peticion de la misma URL, sin descarga.
- [ ] **F5.6** Al arrancar: leer el indice y re-vincular el renderer a cada `MapView`,
      quitando antes los renderers que traiga.
- [ ] **F5.7** Ids de mapa que ya no existen: registrar y marcar como huerfanos, sin abortar.
- [ ] **F5.8** Indice corrupto: renombrar a `.bak`, avisar en el log, arrancar vacio. Nunca
      impedir el arranque del servidor.
- [ ] **F5.9** `.fmc` ausente o corrupto: ese mapa en blanco, log, y el resto sigue.
- [ ] **F5.10** Cache LRU en memoria acotado en MB, con carga perezosa desde disco fuera del
      hilo principal.
- [ ] **F5.11** Volcado del indice al apagar y tras cada cambio.
- [ ] **F5.12** Prueba manual: reiniciar el servidor y comprobar que los cuadros siguen
      iguales.
- [ ] **F5.13** Prueba manual: borrar un `.fmc` a mano y comprobar que solo ese mapa se rompe.
- [ ] **F5.14** El `.fmc` guarda bytes de paleta de mapa, que dependen de una API marcada
      para eliminacion (R7). Dejar constancia en la cabecera del formato de con que version
      de paleta se escribio, y mantener el acceso a esa API acotado a `MapConverter` y
      `FraMCitRenderer`, para que el dia que Paper la elimine solo haya que tocar esos dos
      sitios y migrar los archivos por su version.

**Fase terminada cuando**: criterios de aceptacion 4 y 8 cumplidos.

---

## F6 - Animacion de GIF

Rama: `feat/f6-animacion` - Worktree: `../framcit-f6-animacion`

- [ ] **F6.1** `FraMCitRenderer` contextual, con indice de frame propio por jugador.
- [ ] **F6.2** Volcado del frame al canvas como copia de memoria, sin trabajo de conversion
      en el render.
- [ ] **F6.3** `GazeTracker`: raytrace desde los ojos cada 5 ticks, un rayo por jugador.
- [ ] **F6.4** Detectar `ItemFrame` con mapa de FraMCit, y que ningun bloque solido se
      interponga.
- [ ] **F6.5** Estado por par (jugador, mapa): mirando o no, indice de frame, reloj propio.
- [ ] **F6.6** `AnimationScheduler` cada tick: avanzar solo los pares que tocan y llamar a
      `sendMap` solo a ese jugador.
- [ ] **F6.7** Respetar el retardo de cada frame con el tope de fps.
- [ ] **F6.8** Congelar al apartar la vista y reanudar en el mismo frame.
- [ ] **F6.9** Bucle infinito, ignorando el numero de repeticiones que declare el GIF.
- [ ] **F6.10** Animar tambien el mapa sostenido en la mano principal.
- [ ] **F6.11** Limpiar el estado al desconectarse un jugador, al descargarse el chunk y al
      romperse el marco. Sin fugas de memoria.
- [ ] **F6.12** Una excepcion en el ciclo desactiva solo ese mapa, nunca el ciclo entero.
- [ ] **F6.13** Prueba manual con dos clientes: animacion independiente para cada uno.
- [ ] **F6.14** Prueba manual: apartar la vista congela; volver a mirar sigue donde estaba.

**Fase terminada cuando**: criterios de aceptacion 2 y 3 cumplidos con dos clientes a la vez.

---

## F7 - Configuracion y administracion

Rama: `feat/f7-config-admin` - Worktree: `../framcit-f7-config-admin`

- [ ] **F7.1** `config.yml` con los valores del spec 5, mas rango de vision, periodo del
      tracker y tamano de cache.
- [ ] **F7.2** [SEC] Confirmar que los limites de seguridad **no** son configurables por
      archivo.
- [ ] **F7.3** `messages.yml` con todos los textos, editable sin recompilar.
- [ ] **F7.4** Valores por defecto sanos si falta una clave o trae basura, avisando en el log.
- [ ] **F7.5** `/imagen recargar` con permiso `framcit.admin`.
- [ ] **F7.6** `/imagen info` mirando a un marco: autor, fecha, URL, id de mapa.
- [ ] **F7.7** `/imagen purgar <url|id>`: borra del cache y del disco, con confirmacion.
- [ ] **F7.8** `/imagen listar [pagina]` paginado.
- [ ] **F7.9** Autocompletado de los subcomandos.
- [ ] **F7.10** [SEC] Al arrancar, leer el TTL de cache de DNS efectivo de la JVM y avisar
      en el log si esta desactivado (`0`). Con la cache desactivada, la proteccion de
      `UrlGuard` es vulnerable al reenlace de DNS (R6, constitution 6.1).

**Fase terminada cuando**: cambiar un limite y recargar surte efecto sin reiniciar, y
`/imagen info` responde correctamente.

---

## F8 - Rendimiento

Rama: `feat/f8-rendimiento` - Worktree: `../framcit-f8-rendimiento`

- [ ] **F8.1** Montar el escenario: 20 GIFs colgados en un mismo sitio.
- [ ] **F8.2** Medir TPS con varios jugadores mirandolos, con `/spark` o `/timings`.
- [ ] **F8.3** Revisar que ni red, ni decodificacion, ni disco tocan el hilo principal.
- [ ] **F8.4** Medir el uso de memoria del cache LRU y ajustar el valor por defecto.
- [ ] **F8.5** Medir el coste real del `GazeTracker` y ajustar el periodo si hace falta.
- [ ] **F8.6** Prueba: URL que nunca responde, con el TPS a la vista.
- [ ] **F8.7** Prueba: quitar el jar y arrancar, sin errores ni corrupcion.
- [ ] **F8.8** Anotar los numeros medidos en el informe de la fase.
- [ ] **F8.9** [SEC] **Cerrar R6 (reenlace de DNS).** Conectar a la IP que ya valido
      `UrlGuard` en lugar de al nombre, conservando la cabecera `Host` y el SNI para que
      TLS siga verificando el certificado contra el dominio real. Aplica tambien a cada
      salto de redireccion. Alternativa si resulta inviable: dejar por escrito que se
      acepta el riesgo, con el motivo. **No se puede pasar a F9 sin resolver esta tarea de
      una de las dos formas** (constitution 6.1).

**Fase terminada cuando**: criterios de aceptacion 7, 9 y 10 cumplidos, con numeros escritos.

---

## F9 - Validacion en Aternos

Rama: `feat/f9-aternos` - Worktree: `../framcit-f9-aternos`

- [ ] **F9.1** Subir el jar al Aternos con Paper 26.2 y arrancar.
- [ ] **F9.2** [A1] `/imagen <url publica>`. **Esta es la prueba que decide.** Si Aternos
      bloquea el trafico saliente, falla aqui.
- [ ] **F9.3** Si falla: documentar el error exacto y llevarselo al owner. No inventar
      rodeos sin aprobacion.
- [ ] **F9.4** Repasar los 10 criterios de aceptacion en ese servidor.
- [ ] **F9.5** Comprobar el consumo de disco de `images/` frente al limite de Aternos.
- [ ] **F9.6** Comprobar que la persistencia aguanta el apagado automatico por inactividad.

**Fase terminada cuando**: el plugin funciona en Aternos, o esta documentado con precision
que lo impide.

---

## Resumen

| Fase | Tareas | Depende de |
|---|---|---|
| F0 spike WEBP | 5 | - |
| F1 esqueleto | 10 | - |
| F2 seguridad | 12 | F1 |
| F3 imagen | 15 | F1, F0 |
| F4 estatico | 14 | F2, F3 |
| F5 persistencia | 13 | F4 |
| F6 animacion | 14 | F5 |
| F7 config y admin | 10 | F6 |
| F8 rendimiento | 9 | F7 |
| F9 Aternos | 6 | F8 |

Total: 108 tareas.
