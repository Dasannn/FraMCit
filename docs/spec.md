# Spec 001 - Imagenes y GIFs en marcos

Que hace el plugin, visto desde el jugador y desde el administrador.
No describe como se implementa: eso es ARCHITECTURE.md.

Rige sobre este documento: `docs/constitution.md`.

## 1. Resumen

Un jugador ejecuta `/imagen <url>`. El plugin descarga la imagen o GIF de esa URL, la
convierte a la paleta de colores de los mapas de Minecraft y entrega al jugador un item
mapa que muestra esa imagen. El jugador coloca el mapa en un item frame a mano.

Si la URL es un GIF animado, el mapa se anima, pero solo mientras algun jugador esta
mirando el marco donde esta colgado.

## 2. Comando

### 2.1 Sintaxis

```
/imagen <url>
```

Alias: `/img`, `/image`.

- Un unico argumento: la URL. Sin flags.
- Los subcomandos de jugador (`mias`, `borrar`) los anade el spec 003; los de
  administracion estan en la seccion 8.
- Solo ejecutable por un jugador. Desde consola responde que el comando requiere un
  jugador (no hay a quien darle el item).
- Permiso: `framcit.imagen`, por defecto **solo operadores** (`default: op`). El comando
  viene cerrado y el administrador lo abre a quien quiera con su gestor de permisos.
  (Cambiado en la enmienda del 2026-09-10; antes era `default: true`. Ver constitution 4.2
  y `docs/spec-002-economia.md` seccion 7.)
- Sin cooldown y sin lista blanca de dominios. El numero de imagenes que un jugador puede
  tener a la vez si esta limitado: ver `docs/spec-003-slots.md`.
- El uso del comando puede costar dinero del juego si el servidor tiene economia. Ver
  `docs/spec-002-economia.md`.

### 2.2 Flujo exitoso

1. El jugador ejecuta el comando.
2. Respuesta inmediata en chat: "Descargando imagen...". El jugador puede seguir jugando;
   nada se congela.
3. El plugin descarga y procesa la imagen fuera del hilo principal.
4. Al terminar, el item mapa aparece en el inventario del jugador:
   - Si tiene hueco libre, va al inventario.
   - Si el inventario esta lleno, el item se suelta a los pies del jugador y el chat lo
     avisa.
   - Si el jugador se desconecto durante la descarga, el proceso se cancela sin entregar
     nada. La imagen procesada queda en cache; volver a pedir la misma URL es instantaneo.
5. Mensaje de confirmacion en chat, indicando si es imagen estatica o GIF y, en el caso
   del GIF, cuantos frames tiene.

### 2.3 Item entregado

- Item: mapa relleno (filled map), cantidad 1.
- Nombre visible: el nombre del archivo de la URL, o el dominio si no se puede deducir.
- Descripcion (lore): dominio de origen, quien lo creo y la fecha. Sirve para que el staff
  identifique de un vistazo el origen de una imagen colgada.
- Marca interna que identifica el mapa como creado por este plugin.

## 3. Reglas de imagen

### 3.1 Escalado

- Destino fijo: 128x128 pixeles (un mapa, un marco).
- La imagen se escala manteniendo su proporcion original hasta que quepa completa dentro
  de los 128x128. El espacio sobrante se rellena con pixeles transparentes.
- Nunca se deforma ni se recorta la imagen.
- En un item frame, la parte transparente del mapa deja ver el fondo del marco.

### 3.2 Colores

- La paleta de mapas de Minecraft es limitada. El plugin convierte cada pixel al color mas
  cercano de esa paleta. Degradados suaves y fotos pierden fidelidad: es una limitacion del
  juego, no un bug.
- La transparencia total (alfa) se conserva. La transparencia parcial se resuelve como
  opaca o totalmente transparente, sin medias tintas.

### 3.3 Formatos aceptados

- PNG, JPEG, BMP (imagenes estaticas).
- GIF (estatico o animado).
- WEBP (estatico, y animado si el decodificador lo soporta - ver 3.4).
- Cualquier otro formato se rechaza con un mensaje que dice cuales estan soportados.
- El formato se determina por el contenido del archivo, no por la extension de la URL ni
  por el header `Content-Type`.

### 3.4 WEBP (decision D1, resuelta: opcion b)

Google sirve buena parte de sus imagenes en WEBP y Java no lo lee de serie. El plugin
incluye **TwelveMonkeys ImageIO** (`com.twelvemonkeys.imageio:imageio-webp`, ultima
version publicada 3.15.0, verificada en Maven Central el 2026-09-10) como unica dependencia
externa, empaquetada dentro del jar.

- Es una dependencia madura y de uso extendido, con licencia BSD de 3 clausulas.
- Aporta soporte de lectura de WEBP. No se usa para escribir.
- Los mismos limites de la seccion 5 aplican a los WEBP.

**Riesgo R1 cerrado: SÍ lee todos los frames** (verificado el 2026-09-10 en la fase F0).
La libreria `com.twelvemonkeys.imageio:imageio-webp:3.15.0` decodifica con exito la totalidad
de los frames de archivos WEBP animados reales mediante `reader.read(i)`.

Evidencia empirica (comparada contra `webpinfo` 1.4.0 oficial de Google y Python Pillow):
1. `animated_1_google.webp` (Google / Mathias Bynens): 12 de 12 frames leidos (100%).
2. `chromium_animated_large.webp` (Proyecto Chromium): 8 de 8 frames leidos (100%).
3. `mozilla_blend.webp` (Proyecto Mozilla Gecko): 2 de 2 frames leidos (100%).
4. `static_google.webp` (Google WebP Gallery): 1 de 1 frame leido (control estatico).

**Retardos entre frames**: la libreria **no expone los retardos** a traves de la API estandar
de `ImageIO` (`reader.getStreamMetadata()` devuelve `null` y `reader.getImageMetadata(i)`
solo expone el esquema estandar `javax_imageio_1.0` sin campos de animacion ni duracion).
Internamente la clase privada `AnimationFrame` almacena el valor `duration`, pero no se
expone al exterior.

**Propuesta de retardo por defecto**:
Al igual que con los GIFs que declaran retardo invalido o cero (seccion 4.2), los WEBP
animados usaran por defecto **10 fps (100 ms por frame / 2 ticks de Minecraft)**, salvo que
en F3 se decida extraer la duracion directamente leyendo los 3 bytes de duration del chunk
`ANMF` (offset 20 en cada chunk ANMF, formato little-endian de 24 bits).

## 4. GIFs animados

### 4.1 Cuando se anima

Un mapa GIF avanza de frame unicamente cuando al menos un jugador esta mirando el marco
que lo contiene:

- El marco esta dentro del rango de vision configurado (por defecto 24 bloques).
- El jugador esta apuntando al marco: un trazado de rayo desde sus ojos, en la direccion
  en la que mira, impacta ese marco antes que cualquier bloque solido.

Cuando ningun jugador cumple lo anterior, el mapa se queda congelado en el frame en el que
estaba y deja de consumir red y CPU. Al volver a mirarlo, la animacion continua desde ese
mismo frame.

Cada jugador ve su propia animacion, con su propio ritmo, independiente de los demas.

### 4.2 Velocidad

- Se respeta el retardo entre frames que declara el propio GIF.
- Tope de 10 frames por segundo. Un GIF mas rapido se reproduce a 10 fps.
- Un GIF que declara un retardo invalido o de cero usa 10 fps por defecto.
- La animacion es en bucle infinito, incluso si el GIF declara un numero limitado de
  repeticiones.
- **WEBP animado**: se reproduce a 10 fps fijos (100 ms por frame). La libreria que lo
  decodifica lee todos los frames pero no expone sus retardos (verificado en F0, ver
  seccion 3.4). Como el tope del plugin ya es 10 fps, la diferencia frente al retardo real
  es inapreciable en la mayoria de archivos. Leer la duracion a mano del chunk `ANMF` queda
  como mejora futura, no se implementa ahora.

### 4.3 Mapa en mano

Un mapa GIF sostenido en la mano tambien se anima, mientras el jugador lo tenga en la mano
principal y lo este mirando (vista de mapa activa).

## 5. Limites

Rechazo con mensaje explicativo si se supera cualquiera de estos:

| Limite | Valor |
|---|---|
| Tamano maximo de descarga | 5 MB |
| Frames maximos de un GIF | 100 |
| Frames maximos por jugador | segun sus nodos, nunca por encima del limite global (spec 003) |
| Frames por segundo maximos | 10 |
| Dimensiones maximas de origen | 4096 x 4096 pixeles |
| Timeout de conexion | 5 segundos |
| Timeout de lectura total | 15 segundos |
| Redirecciones seguidas | 3 |
| Descargas simultaneas en todo el servidor | 4 |

Todos estos valores son editables en `config.yml`. El limite de descarga se aplica
contando bytes mientras se descarga, no confiando en lo que declare el servidor remoto.

Si ya hay 4 descargas en curso, la peticion se encola y el jugador recibe aviso de que
esta en cola.

## 6. Seguridad

Aplicacion concreta del piso definido en la constitution:

- Solo se aceptan URLs `http://` y `https://`.
- Antes de conectar, se resuelve el host a IP y se rechaza si apunta a loopback,
  link-local, o rango privado (IPv4 e IPv6). La comprobacion se repite en cada redireccion.
- Una URL rechazada por esta regla devuelve al jugador un mensaje generico de "URL no
  permitida", sin revelar detalles de la red interna del servidor.
- Nunca se ejecuta ni interpreta el contenido descargado: solo se decodifica como imagen.

## 7. Cache y persistencia

### 7.1 Cache por URL

- Dos peticiones de la misma URL producen mapas que apuntan a la misma imagen almacenada.
  La segunda no descarga nada y es instantanea.
- La comparacion de URLs ignora diferencias irrelevantes (mayusculas del host, barra final).
- El cache no expira solo. Una imagen que cambie en su servidor de origen no se vuelve a
  descargar. Para forzarlo esta el comando administrativo de la seccion 8.

### 7.2 Persistencia

- Las imagenes procesadas se guardan en la carpeta de datos del plugin y sobreviven al
  reinicio del servidor.
- Al arrancar, el plugin re-vincula cada imagen guardada con su ID de mapa. Los cuadros
  colgados en el mundo vuelven a mostrarse sin intervencion manual.
- Si el archivo de una imagen se borra o se corrompe, ese mapa se queda en blanco, se
  registra el error en el log y el resto del plugin sigue funcionando.
- Desinstalar el plugin deja esos mapas en blanco. No corrompe el mundo ni ningun otro dato.

## 8. Administracion

Comandos con permiso `framcit.admin` (por defecto solo operadores):

- `/imagen recargar` - recarga `config.yml`.
- `/imagen info` - muestra, mirando a un marco, quien creo esa imagen, cuando y de que URL.
- `/imagen purgar <url|id>` - borra una imagen del cache y del disco. Los mapas que la
  usaban quedan en blanco.
- `/imagen listar [pagina]` - lista las imagenes almacenadas: id, autor, fecha, URL.

El spec 003 anade `/imagen uso` y `/imagen purgar-jugador`, y permite filtrar `listar` por
jugador.

### 8.1 Registro (log)

Cada creacion registra en el log del servidor: nombre y UUID del jugador, URL completa,
tamano, formato, y el ID de mapa resultante. Este registro es obligatorio: es la unica
forma de que el staff sepa quien colgo que.

## 9. Mensajes al jugador

Todos en espanol. Cada fallo dice que paso, no solo que fallo:

| Situacion | Mensaje |
|---|---|
| Sin URL | Uso: /imagen <url> |
| URL malformada | Esa URL no es valida. |
| Esquema no permitido | Solo se aceptan enlaces http y https. |
| Destino bloqueado | URL no permitida. |
| Host inexistente | No se pudo conectar con ese sitio. |
| HTTP 404 / 403 / 5xx | El sitio respondio con error (codigo). El enlace puede haber caducado. |
| Timeout | El sitio tardo demasiado en responder. |
| Demasiado grande | La imagen supera el limite de 5 MB. |
| No es imagen | Ese enlace no apunta a una imagen. |
| Formato no soportado | Formato no soportado. Usa PNG, JPEG, BMP, GIF o WEBP. |
| Archivo corrupto | Ese archivo esta danado y no se pudo leer. |
| GIF con demasiados frames | Ese GIF tiene demasiados frames (limite: 100). |
| Dimensiones excesivas | Esa imagen es demasiado grande (limite: 4096x4096). |
| Cola llena | Hay varias descargas en curso, espera un momento. |
| Inventario lleno | Inventario lleno: el mapa se solto al suelo. |
| Sin permiso | No tienes permiso para usar este comando. |
| Desde consola | Este comando solo lo puede usar un jugador. |

Ningun mensaje de error expone rutas del servidor, IPs internas ni trazas de excepcion.
Esos detalles van al log.

## 10. Rendimiento

- El plugin no toca el hilo principal para descargar, decodificar, escalar ni guardar en
  disco.
- Con ningun jugador mirando GIFs, el coste en marcha del plugin es practicamente cero.
- Objetivo: 20 GIFs animados visibles a la vez, con 10 jugadores mirandolos, sin caida
  medible de TPS.

## 11. Criterios de aceptacion

1. `/imagen <url de PNG>` entrega un mapa que muestra ese PNG, con proporcion correcta y
   sin deformar.
2. `/imagen <url de GIF>` entrega un mapa que se anima al mirarlo y se congela al apartar
   la vista.
3. Dos jugadores mirando el mismo GIF desde angulos distintos lo ven animarse, cada uno de
   forma independiente.
4. Reiniciar el servidor deja todos los mapas colgados exactamente como estaban.
5. Una URL apuntando a 127.0.0.1 o a una IP privada es rechazada, y el mensaje al jugador
   no revela nada de la red interna.
6. Un archivo de 500 MB servido con `Content-Length: 1000` se corta al llegar al limite
   de 5 MB.
7. Una URL que no responde nunca no bloquea el servidor: el jugador recibe un timeout y el
   TPS no se mueve.
8. La misma URL pedida dos veces no genera una segunda descarga.
9. 20 GIFs visibles con 10 jugadores mirando: sin caida medible de TPS.
10. Borrar el jar del plugin y arrancar el servidor no produce errores ni corrupcion.

## 12. Decisiones y riesgos

- **D1** - Soporte de WEBP. **Resuelta** (2026-09-10): opcion (b), se incluye
  TwelveMonkeys ImageIO. Ver seccion 3.4.
- **R1** - Riesgo abierto: soporte de WEBP animado por TwelveMonkeys, sin verificar. Ver
  seccion 3.4. Se verifica en el plan; no bloquea el cierre del spec.
