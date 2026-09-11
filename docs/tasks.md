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

- [x] **F5.1** Formato `.fmc`: cabecera con magia, version de formato y numero de frames,
      seguida de los frames en crudo.
- [x] **F5.2** Escritura a archivo temporal y renombrado atomico, para no dejar archivos a
      medias.
- [x] **F5.3** `index.json`: id de mapa, URL original, URL normalizada, autor, fecha, formato,
      frames, retardos.
- [x] **F5.4** Normalizacion de URL para la clave de cache (host en minusculas, barra final).
- [x] **F5.5** Acierto de cache: segunda peticion de la misma URL, sin descarga.
- [x] **F5.6** Al arrancar: leer el indice y re-vincular el renderer a cada `MapView`,
      quitando antes los renderers que traiga.
- [x] **F5.7** Ids de mapa que ya no existen: registrar y marcar como huerfanos, sin abortar.
- [x] **F5.8** Indice corrupto: renombrar a `.bak`, avisar en el log, arrancar vacio. Nunca
      impedir el arranque del servidor.
- [x] **F5.9** `.fmc` ausente o corrupto: ese mapa en blanco, log, y el resto sigue.
- [x] **F5.10** Cache LRU en memoria acotado en MB, con carga perezosa desde disco fuera del
      hilo principal.
- [x] **F5.11** Volcado del indice al apagar y tras cada cambio.
- [x] **F5.12** Prueba manual: reiniciar el servidor y comprobar que los cuadros siguen
      iguales.
- [x] **F5.13** Prueba manual: borrar un `.fmc` a mano y comprobar que solo ese mapa se rompe.
- [x] **F5.14** El `.fmc` guarda bytes de paleta de mapa, que dependen de una API marcada
      para eliminacion (R7). Dejar constancia en la cabecera del formato de con que version
      de paleta se escribio, y mantener el acceso a esa API acotado a `MapConverter` y
      `FraMCitRenderer`, para que el dia que Paper la elimine solo haya que tocar esos dos
      sitios y migrar los archivos por su version.

**Fase terminada cuando**: criterios de aceptacion 4 y 8 cumplidos.

---

## F6 - Animacion de GIF

Rama: `feat/f6-animacion` - Worktree: `../framcit-f6-animacion`

- [x] **F6.1** `FraMCitRenderer` contextual, con indice de frame propio por jugador.
- [x] **F6.2** Volcado del frame al canvas como copia de memoria, sin trabajo de conversion
      en el render.
- [x] **F6.3** `GazeTracker`: raytrace desde los ojos cada 5 ticks, un rayo por jugador.
- [x] **F6.4** Detectar `ItemFrame` con mapa de FraMCit, y que ningun bloque solido se
      interponga.
- [x] **F6.5** Estado por par (jugador, mapa): mirando o no, indice de frame, reloj propio.
- [x] **F6.6** `AnimationScheduler` cada tick: avanzar solo los pares que tocan y llamar a
      `sendMap` solo a ese jugador.
- [x] **F6.7** Respetar el retardo de cada frame con el tope de fps.
- [x] **F6.8** Congelar al apartar la vista y reanudar en el mismo frame.
- [x] **F6.9** Bucle infinito, ignorando el numero de repeticiones que declare el GIF.
- [x] **F6.10** Animar tambien el mapa sostenido en la mano principal.
- [x] **F6.11** Limpiar el estado al desconectarse un jugador, al descargarse el chunk y al
      romperse el marco. Sin fugas de memoria.
- [x] **F6.12** Una excepcion en el ciclo desactiva solo ese mapa, nunca el ciclo entero.
- [x] **F6.13** Prueba manual con dos clientes: animacion independiente para cada uno.
- [x] **F6.14** Prueba manual: apartar la vista congela; volver a mirar sigue donde estaba.

**Fase terminada cuando**: criterios de aceptacion 2 y 3 cumplidos con dos clientes a la vez.

---

## F7 - Configuracion y administracion

Rama: `feat/f7-config-admin` - Worktree: `../framcit-f7-config-admin`

- [x] **F7.1** `config.yml` con los valores del spec 5, mas rango de vision, periodo del
      tracker y tamano de cache.
- [x] **F7.2** [SEC] Confirmar que los limites de seguridad **no** son configurables por
      archivo.
- [x] **F7.3** `messages.yml` con todos los textos, editable sin recompilar.
- [x] **F7.4** Valores por defecto sanos si falta una clave o trae basura, avisando en el log.
- [x] **F7.5** `/imagen recargar` con permiso `framcit.admin`.
- [x] **F7.6** `/imagen info` mirando a un marco: autor, fecha, URL, id de mapa.
- [x] **F7.7** `/imagen purgar <url|id>`: borra del cache y del disco, con confirmacion.
- [x] **F7.8** `/imagen listar [pagina]` paginado.
- [x] **F7.9** Autocompletado de los subcomandos.
- [x] **F7.10** [SEC] Al arrancar, leer el TTL de cache de DNS efectivo de la JVM y avisar
      en el log si esta desactivado (`0`). Con la cache desactivada, la proteccion de
      `UrlGuard` es vulnerable al reenlace de DNS (R6, constitution 6.1).

**Fase terminada cuando**: cambiar un limite y recargar surte efecto sin reiniciar, y
`/imagen info` responde correctamente.

---

## F8 - Rendimiento

Rama: `feat/f8-rendimiento` - Worktree: `../framcit-f8-rendimiento`

- [x] **F8.1** Montar el escenario: 20 GIFs colgados en un mismo sitio.
- [x] **F8.2** Medir TPS con varios jugadores mirandolos (medido coste de CPU aislado: MSPT 0.032 - 0.053 ms; TPS aritmetico proyectado 19.98 - 20.00). **Verificado en servidor real el 2026-09-10: `/tps` 20 / 19 / 19 con 20 GIFs colgados. Criterio 9 cumplido.**
- [x] **F8.3** Revisar que ni red, ni decodificacion, ni disco tocan el hilo principal (100% en pool `framcit-net-*`).
- [x] **F8.4** Medir el uso de memoria del cache LRU y ajustar el valor por defecto (20 GIFs = 9.38 MB a 31.25 MB; 128 MB mantenido como techo bajo demanda).
- [x] **F8.5** Medir el coste real del `GazeTracker` y ajustar el periodo si hace falta (1.9 µs cada 5 ticks; periodo de 5 ticks mantenido).
- [x] **F8.6** Prueba: URL que nunca responde, con el TPS a la vista (0.000 ms de bloqueo síncrono medido; prueba con jugador en servidor documentada para el owner).
- [x] **F8.7** Prueba: quitar el jar y arrancar, sin errores ni corrupcion (verificado en servidor Paper 26.2 local: mapas vanilla FILLED_MAP, 0 errores, mundos intactos).
- [x] **F8.8** Anotar los numeros medidos en el informe de la fase (`docs/f8-rendimiento.md`).
- [x] **F8.10** Medir la contienda por el pool: las lecturas de disco de `ImageStore`
      comparten los 4 hilos y la cola de 20 con las descargas de los jugadores (drena 20 cargas en 176 ms, 0 rechazos de /imagen; pool unificado suficiente).
- [x] **F8.11** Medir el coste por tick del `AnimationScheduler`, que recorre **todos** los
      estados (jugador, mapa), no solo los activos (0.55 µs para 200 estados, 1.26 µs para 1000 estados; recorrido completo mantenido).
- [x] **F8.9** [SEC] **Cerrar R6 (reenlace de DNS).** Investigado en Java 25. Se opta por la via
      documentada de **aceptar el riesgo residual por escrito** (constitution 6.1),
      dado que las alternativas tecnicas (romper verificacion TLS, SPI global en toda la JVM, o cliente HTTP
      artesanal sobre SSLSocket) introducen riesgos superiores. Mitigado significativamente por la cache DNS
      de 30 s de la JVM (aunque no elimina el riesgo al expirar el TTL) y la alerta al arrancar de F7.10
      (verificada inactiva en Temurin 25 LTS).

**Fase terminada cuando**: criterio de aceptacion 10 verificado en servidor real, criterios 7 y 9 medidos en aislamiento y documentados con guia de validacion para el owner, y numeros registrados en `docs/f8-rendimiento.md`.

---

## F10 - Permisos cerrados y cobro por imagen (spec 002)

Rama: `feat/f10-economia` - Worktree: `../framcit-f10-economia`

Implementa `docs/spec-002-economia.md` y la enmienda de la constitution 4.2.

- [x] **F10.1** `framcit.imagen` pasa a `default: op` en `paper-plugin.yml`.
- [x] **F10.2** Declarar `framcit.gratis` con `default: false`.
- [x] **F10.3** Vault como dependencia **blanda** (`soft-depend`), nunca obligatoria.
      El plugin debe arrancar y funcionar sin Vault instalado.
- [x] **F10.4** Capa de economia que resuelve el servicio de Vault al arrancar y tolera
      las tres ausencias: sin Vault, con Vault pero sin plugin de economia, y con precio
      cero. En los tres casos las imagenes son gratis.
- [x] **F10.5** Una sola linea en el log al arrancar diciendo si el cobro esta activo y
      por que. Informativa, no un aviso: no tener economia es normal.
- [x] **F10.6** `economia.precio-por-imagen` en `config.yml`, por defecto 0. Negativo o
      invalido se trata como cero, con aviso. El plugin nunca da dinero.
- [x] **F10.7** Comprobar saldo **antes** de descargar. Sin saldo, no se descarga nada.
- [x] **F10.8** Cobrar **al entregar** el mapa, nunca antes. Si algo falla, no se cobra.
- [x] **F10.9** Saldo perdido entre la comprobacion y el cobro: no se entrega el mapa, se
      explica por que y se descarta lo creado.
- [x] **F10.10** Fallo del plugin de economia al cobrar: no se entrega el mapa, traza
      completa al log y mensaje generico al jugador.
- [x] **F10.11** Acierto de cache: se cobra el precio completo, con comprobacion de saldo
      previa aunque no haya descarga.
- [x] **F10.12** `framcit.gratis`: ni se comprueba saldo ni se menciona el precio.
- [x] **F10.13** Mensajes del spec 002 seccion 8 en `messages.yml`. Cuando el cobro esta
      desactivado no debe aparecer ninguno.
- [x] **F10.14** El log obligatorio incorpora el importe cobrado, o `gratis`.
- [x] **F10.15** Que el precio se muestre con el formato del plugin de economia, no con
      uno inventado por FraMCit.
- [x] **F10.16** Tests: sin Vault, con Vault sin economia, precio 0, precio configurado,
      saldo justo, saldo insuficiente, saldo perdido a mitad, fallo de la economia,
      acierto de cache, y jugador exento.
- [x] **F10.17** Prueba manual en el servidor local. **Verificado por el owner el
      2026-09-10: el plugin arranca y funciona sin Vault ni plugin de economia**, que es
      el caso real del servidor de destino (constitution 3.1). Los criterios del spec 002
      que exigen una economia instalada quedan sin ejercitar hasta que ese servidor monte
      la suya.

**Fase terminada cuando**: los 11 criterios del spec 002 se cumplen, y el plugin sigue
arrancando y funcionando en un servidor sin Vault.

---

## F11 - Cupos por jugador (spec 003)

Rama: `feat/f11-cupos` - Worktree: `../framcit-f11-cupos`

Implementa `docs/spec-003-slots.md`. Independiente de F12: no comparten codigo.

- [x] **F11.1** `PermissionTier`: dada una familia de nodos (`framcit.slots.`,
      `framcit.frames.`), devolver el numero mas alto concedido. Gana el mayor, no se
      suman. Sin ningun nodo, el valor por defecto de `config.yml`.
- [x] **F11.2** Declarar en `paper-plugin.yml`: `framcit.slots.1/2/3` (`false`),
      `framcit.slots.ilimitado` (`op`), y `framcit.frames.10/25/50/100` (`false`).
- [x] **F11.3** `config.yml`: `slots.por-defecto` (1), `slots.maximo-nodo` (10),
      `frames.por-defecto` (100, igual al limite global) y `frames.maximo-nodo` (100).
      Un valor invalido cae al por defecto con aviso, nunca deja el comando inutilizable.
- [x] **F11.4** [SEC] El cupo de frames de un jugador **nunca** supera el limite global de
      `config.yml`. El efectivo es el menor de los dos. Conceder `framcit.frames.500` no
      sube el techo del servidor: es el piso de seguridad de la constitution 6.6.
- [x] **F11.5** Posesion dentro de `index.json`: lista de UUIDs por entrada. No se crea
      ningun archivo nuevo.
- [x] **F11.6** Migracion al cargar: una entrada sin lista de poseedores estrena una con su
      autor dentro. Autor desconocido: sin poseedores, y **no se borra**.
- [x] **F11.7** `QuotaService`: si a un jugador le cabe otra imagen, y cuantos frames puede
      tener la que pide.
- [x] **F11.8** Los cupos se resuelven en el **hilo principal** antes de despachar al pool.
      Lo que viaja es un numero, no el jugador.
- [x] **F11.9** [BUG] `ImageDecoder` deja de llevar el limite de frames en un campo
      estatico y lo recibe por parametro. Con 4 descargas simultaneas, dos jugadores con
      cupos distintos se pisan el limite: el de cupo 10 colaria 50. Test que lo demuestre.
- [x] **F11.10** Orden de comprobaciones del spec 003 seccion 4, integrado con el cobro del
      spec 002. Sin slot no se mira el saldo ni se descarga nada.
- [x] **F11.11** Acierto de cache: ocupa slot igual, y el cupo de frames se comprueba
      **antes de descargar y antes de mirar el saldo**, porque el recuento ya esta en el
      indice.
- [x] **F11.12** URL que el jugador **ya posee**: entrega otra copia del item y el cupo no
      se mueve. Se sigue cobrando (spec 002).
- [x] **F11.13** Re-comprobar el slot justo antes de entregar. Si se lleno durante la
      descarga: no se entrega, **no se cobra**, y se explica por que.
- [x] **F11.14** Rechazo por cupo de frames: no se cobra y no queda nada en disco.
- [x] **F11.15** `/imagen mias`: lo que posee quien lo ejecuta, con su cupo usado.
- [x] **F11.16** `/imagen borrar <id>` con `confirmar` obligatorio, y los dos textos de
      aviso (ultimo poseedor o posesion compartida).
- [x] **F11.17** El archivo de disco se borra **solo** cuando no queda ningun poseedor. Con
      otros poseedores, no cambia nada para ellos.
- [x] **F11.18** `/imagen listar [jugador] [pagina]`.
- [x] **F11.19** `/imagen uso [jugador]`: imagenes, frames, disco medido, memoria **cargada
      ahora mismo** en el cache LRU, y cuantas se estan animando. Ningun numero puede ser
      una proyeccion presentada como medida.
- [x] **F11.20** `/imagen purgar-jugador <jugador>` con `confirmar` obligatorio, respetando
      la posesion compartida, y con registro en el log de quien lo ejecuto y sobre quien.
- [x] **F11.21** Mensajes de la seccion 9 del spec 003 en `messages.yml`.
- [x] **F11.22** Bajar un cupo por debajo de lo que un jugador ya tiene **no borra nada**.
- [x] **F11.23** Tests: resolucion de nodos, tope global, posesion compartida, borrado del
      ultimo poseedor, migracion, limite de frames por peticion con dos cupos a la vez, y
      el orden de comprobaciones sin cobrar de mas.
- [x] **F11.24** Prueba manual: los 24 criterios de aceptacion del spec 003.

**Fase terminada cuando**: los 24 criterios del spec 003 se cumplen, y bajar un cupo nunca
destruye contenido.

---

## F12 - Pausa por mirada y mapa inerte (enmiendas del spec 001)

Rama: `feat/f12-pausa-inerte` - Worktree: `../framcit-f12-pausa-inerte`

Implementa las secciones 2.4 y 4.4 del spec 001. Independiente de F11.

- [ ] **F12.1** `config.yml`: `animacion.pausa-por-mirada`, por defecto 5 minutos. A cero
      la regla se desactiva y la animacion no se detiene nunca por este motivo.
- [ ] **F12.2** `GazeTracker` guarda, por par (jugador, marco), el tick en que empezo a
      mirar. Mientras siga mirando no se toca.
- [ ] **F12.3** Al dejar de mirar, la entrada se borra. Eso es lo que hace que el contador
      sea de mirada **seguida** y no acumulada: no hay nada que acumular.
- [ ] **F12.4** El estado de un jugador se vacia entero al desconectarse.
- [ ] **F12.5** `AnimationScheduler` no envia frames a un par que supere el umbral.
      Pregunta, no lleva cuenta propia.
- [ ] **F12.6** Al pausarse, el mapa se queda en el frame en el que estaba. No se reinicia
      al primero ni se pone en blanco.
- [ ] **F12.7** Se reanuda apartando la vista y volviendo a mirar. Ese es el unico
      disparador.
- [ ] **F12.8** Aplica igual al mapa sostenido en la mano (spec 001 seccion 4.3).
- [ ] **F12.9** Pausar **no** descarga la imagen ni la saca del cache LRU. Lo unico que se
      deja de hacer es enviar paquetes.
- [ ] **F12.10** Tests del contador: que se reinicie al apartar la vista, que no acumule, y
      que a cero la regla quede desactivada.
- [ ] **F12.11** [R8] **Verificar en el javadoc de Paper 26.2 el evento de la mesa de
      cartografia antes de escribir el listener.** No dar por bueno ningun nombre de
      evento sin comprobarlo. Anotar en el informe cual es y donde se verifico.
- [ ] **F12.12** `CraftGuard`: si un ingrediente lleva la marca de FraMCit, el resultado
      sale vacio. Mesa de crafteo, con `PrepareItemCraftEvent`.
- [ ] **F12.13** Lo mismo para la mesa de cartografia, con el evento verificado en F12.11.
      Cubre copiar, ampliar y bloquear.
- [ ] **F12.14** El item nunca se consume ni se destruye: el jugador recupera sus
      ingredientes tal cual.
- [ ] **F12.15** Se bloquea la categoria entera, no una lista de recetas. Una lista habria
      que mantenerla cada vez que Minecraft anada una receta que consuma mapas.
- [ ] **F12.16** La descripcion del item dice que no se puede copiar ni ampliar.
- [ ] **F12.17** Quitar el plugin devuelve esos mapas a comportarse como mapas normales:
      la restriccion no sobrevive al jar.
- [ ] **F12.18** Prueba manual: mesa de crafteo con mapa vacio, y mesa de cartografia
      copiando, ampliando y bloqueando. Ninguna produce nada.
- [ ] **F12.19** Prueba manual: mirar un GIF mas de 5 minutos sin apartar la vista y
      comprobar que se detiene; apartar la vista y volver, y comprobar que sigue desde el
      frame en el que estaba.

**Fase terminada cuando**: la animacion se pausa y se reanuda como dice la seccion 4.4, y
ninguna de las dos mesas produce nada a partir de un mapa de FraMCit.

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
| F8 rendimiento | 11 | F7 |
| F10 economia (spec 002) | 17 | F7 |
| F11 cupos (spec 003) | 24 | F10 |
| F12 pausa y mapa inerte | 19 | F6 |

Total: 165 tareas. F9 (Validacion en Aternos, 6 tareas) se elimino el 2026-09-10:
Aternos no admite plugins propios y dejo de ser el destino.

F11 y F12 se anadieron el 2026-09-10 y pueden ir en paralelo: no comparten codigo, solo
coinciden en `config.yml` y `messages.yml`.
