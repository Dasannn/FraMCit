# FraMCit

**Convierte cualquier URL de imagen o GIF en un mapa de Minecraft que puedes colgar en un
marco.** Los GIFs animados solo se animan para quien está mirando el marco.

[![Paper](https://img.shields.io/badge/Paper-26.2-blue)](https://papermc.io/)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net/)
[![Licencia](https://img.shields.io/badge/Licencia-MIT-green)](LICENSE)

🇬🇧 **[Read this in English](README.md)**

---

## Qué hace

Un jugador ejecuta `/imagen <url>` y recibe en la mano un mapa con esa imagen. Lo cuelga en
un marco y ahí se queda — sobrevive a los reinicios, indefinidamente.

Si la imagen es un GIF o un WEBP animado, se anima **solo mientras alguien está mirando
directamente al marco**. Un GIF que nadie mira no consume ni ancho de banda ni CPU. Cada
jugador ve su propia animación, a su propio ritmo.

Sin NMS. Sin reflexión contra clases internas del servidor. Solo API de Paper.

---

## Descarga

**[⬇ Descargar FraMCit-1.0.0.jar](https://github.com/Dasannn/FraMCit/releases/latest/download/FraMCit-1.0.0.jar)**

Todas las versiones: [github.com/Dasannn/FraMCit/releases](https://github.com/Dasannn/FraMCit/releases)

### Requisitos

| | |
|---|---|
| Servidor | **Paper 26.2** (no Spigot, no Bukkit) |
| Java | **25** (lo exige Paper 26.1+) |
| Dependencias | **Ninguna.** Vault y LuckPerms son opcionales |

FraMCit funciona en un servidor pelado sin ningún otro plugin. Es una regla de diseño, no
una casualidad — mira [Funcionar sin Vault](#funcionar-sin-vault).

### Instalación

1. Para el servidor.
2. Copia `FraMCit-1.0.0.jar` a `plugins/`.
3. Arranca el servidor.
4. `/imagen https://ejemplo.com/gato.gif`

> ⚠️ **Nunca sustituyas el jar con el servidor encendido.** Paper carga las clases según las
> necesita, así que sobrescribir un jar en uso hace que las clases que aún no se habían
> cargado desaparezcan a media sesión y salte `NoClassDefFoundError`. Para primero, copia
> después.

De fábrica el comando es **solo para operadores**. Mira [Permisos](#permisos).

---

## Comandos

| Comando | Permiso | Qué hace |
|---|---|---|
| `/imagen <url>` | `framcit.imagen` | Descarga la imagen y te da el mapa |
| `/imagen mias` | `framcit.imagen` | Lista tus imágenes y tu cupo en uso |
| `/imagen borrar <id> confirmar` | `framcit.imagen` | Borra una imagen tuya y libera el slot |
| `/imagen recargar` | `framcit.admin` | Recarga `config.yml` |
| `/imagen info` | `framcit.admin` | Mirando a un marco: quién la creó, cuándo y de qué URL |
| `/imagen listar [jugador] [pág]` | `framcit.admin` | Lista las imágenes, filtrable por jugador |
| `/imagen uso [jugador]` | `framcit.admin` | Consumo medido: disco, RAM y animaciones activas |
| `/imagen purgar <url\|id>` | `framcit.admin` | Borra una imagen del caché y del disco |
| `/imagen purgar-jugador <jugador> confirmar` | `framcit.admin` | Borra todo lo de un jugador |

Alias: `/img`, `/image`. Todos los mensajes al jugador están en español y viven en
`messages.yml`, así que puedes cambiarlos sin recompilar.

Los dos comandos destructivos exigen la palabra literal `confirmar`. Sin ella solo te
enseñan lo que *pasaría*.

---

## Permisos

FraMCit declara sus nodos y pregunta por ellos. **A quién se le conceden es cosa de tu
gestor de permisos, nunca del plugin.**

| Nodo | Por defecto | Da |
|---|---|---|
| `framcit.imagen` | `op` | Usar `/imagen`, `mias`, `borrar` |
| `framcit.admin` | `op` | Todos los subcomandos de administración |
| `framcit.gratis` | `false` | Exento de pago; ni se le menciona el precio |
| `framcit.slots.1` | `false` | Poseer hasta 1 imagen a la vez |
| `framcit.slots.2` | `false` | Hasta 2 imágenes |
| `framcit.slots.3` | `false` | Hasta 3 imágenes |
| `framcit.slots.ilimitado` | `op` | Sin límite de imágenes |
| `framcit.frames.10` | `false` | Imágenes de hasta 10 frames |
| `framcit.frames.25` | `false` | Hasta 25 frames |
| `framcit.frames.50` | `false` | Hasta 50 frames |
| `framcit.frames.100` | `false` | Hasta 100 frames |

### Los dos cupos

Miden cosas distintas y son independientes:

- Los **slots** limitan *cuántas imágenes* acumula un jugador.
- Los **frames** limitan *cuánta memoria cuesta cada una*. Cada frame de mapa ocupa
  exactamente 16384 bytes, así que limitar frames limita RAM y disco de forma directa y
  predecible: 100 frames = 1,6 MB, 20 frames = 320 KB.

Un jugador puede tener 3 slots y solo 10 frames por imagen.

### Tres reglas que sorprenden

**1. Los escalones no se suman: gana el mayor.** Un jugador con `framcit.slots.1` *y*
`framcit.slots.3` tiene **3**, no 4. Son peldaños de una escalera, no puntos que sumar. Así
quitarle un rango a alguien nunca produce un número intermedio raro.

**2. Ningún nodo puede levantar el techo del servidor.** El cupo efectivo de frames es
`min(su nodo, imagen.frames-maximos del config.yml)`. Conceder `framcit.frames.500` **no**
le deja subir 500 frames: el techo del servidor sigue mandando. Ese límite es defensa
contra bombas de descompresión, no una preferencia, y por eso no existe a propósito ningún
nodo de "frames ilimitados".

**3. El slot lo ocupa una imagen que *posees*, no un marco ni un item.** Descuelga el mapa,
mételo en un cofre, cuélgalo en otro sitio: mismo slot, no cambia nada. Un slot se libera
con `/imagen borrar`, y solo así.

### Poseer no es lo mismo que crear

Dos jugadores que piden la misma URL comparten una imagen guardada y un mismo id de mapa —
eso es el caché haciendo su trabajo. Pero son **dos poseedores**, y le cuesta un slot a cada
uno. Pedir una URL que otro ya descargó no descarga nada, pero gasta cupo igual.

Cuando un poseedor la borra, a los demás no les pasa nada: sus mapas siguen funcionando. El
archivo solo desaparece del disco cuando la suelta el **último** poseedor.

---

## Configurar LuckPerms

LuckPerms es opcional. Sin él tienes los `default: op` de la tabla de arriba, que para un
servidor pequeño puede bastar.

LuckPerms viene con `apply-bukkit-default-permissions: true`, así que **respeta los valores
por defecto de FraMCit**: tus operadores conservan `framcit.imagen`, `framcit.admin` y
`framcit.slots.ilimitado` sin que configures nada.

### Un ejemplo completo

```
lp creategroup vip
lp creategroup mvp

lp group vip parent add default
lp group mvp parent add vip

lp group default permission set framcit.imagen true
lp group default permission set framcit.slots.1 true
lp group default permission set framcit.frames.10 true

lp group vip permission set framcit.slots.2 true
lp group vip permission set framcit.frames.25 true

lp group mvp permission set framcit.slots.3 true
lp group mvp permission set framcit.frames.50 true
lp group mvp permission set framcit.gratis true

lp group vip setweight 10
lp group mvp setweight 20
```

Dar un rango:

```
lp user Steve parent add vip
```

Dar un permiso suelto a una persona, sin tocar grupos:

```
lp user Steve permission set framcit.slots.3 true
```

Comprobar qué se aplica de verdad:

```
lp user Steve info
lp user Steve permission check framcit.slots.2
lp editor
```

### Nodos contra valores por defecto del config

**Sin ningún nodo manda `config.yml`**: `slots.por-defecto` (1) y `frames.por-defecto`
(100). Por eso el ejemplo le da `framcit.frames.10` al grupo `default`: si no, un jugador
sin rango puede subir GIFs de 100 frames, porque el valor de fábrica no restringe nada.

Tienes dos caminos para lo mismo. Elige uno:

- **Apretar el `config.yml`** (`frames.por-defecto: 10`) y usar los nodos solo para *subir*
  el cupo a quien pague. Menos trabajo, y falla hacia el lado seguro: quien no tiene rango
  queda restringido por omisión, no porque te acordaras de darle un nodo.
- **Dejar el `config.yml` como está** y dar nodos explícitos a todos los grupos, incluido
  `default`.

### Probar los cupos siendo operador

`framcit.slots.ilimitado` es `default: op`, así que **siendo operador nunca vas a ver el
límite de slots**. Para probarlo, niégate el nodo a ti mismo:

```
lp user Steve permission set framcit.slots.ilimitado false
```

Una negación explícita gana sobre el `default: op`. Se deshace con `permission unset`.

---

## Economía (Vault)

FraMCit puede cobrar dinero del juego por imagen. Viene **desactivado** y es totalmente
opcional.

```yaml
economia:
  precio-por-imagen: 0.0   # 0 = gratis
```

Pon un precio, ejecuta `/imagen recargar` y empieza a cobrar. Sin reiniciar, sin recompilar.

### Cómo funciona el cobro

- El saldo se comprueba **antes** de descargar. Sin dinero, no se descarga nada.
- El cobro ocurre **al entregar**, nunca antes. Si algo falla, no se te cobra.
- **No hay reembolsos**, porque no hay nada que reembolsar: el dinero solo se mueve cuando
  el mapa ya está en tu mano. Borrar la imagen después no te lo devuelve.
- Un acierto de caché cuesta el precio completo. El cupo mide ocupación; el precio mide uso.
- `framcit.gratis` exime a un jugador, y a ese ni se le menciona el precio.

### Funcionar sin Vault

Esto es una regla de diseño dura: **ninguna dependencia externa puede ser obligatoria.**

FraMCit arranca y funciona en un servidor sin Vault, sin plugin de economía y sin gestor de
permisos. Vault se declara con `required: false`, se compila contra él pero nunca se
empaqueta, y todo acceso a sus tipos está detrás de tres comprobaciones — que el plugin
exista, que esté habilitado, y un `Class.forName` — aisladas en una sola clase
package-private.

Tres situaciones significan lo mismo: **las imágenes son gratis y el plugin no se queja.**

1. Vault no está instalado.
2. Vault está instalado pero ningún plugin de economía se registró en él.
3. El precio es 0.

Al arrancar sale una línea informativa diciéndote en cuál de los tres estás:

```
[FraMCit] Cobro de imágenes desactivado: Vault no está instalado en el servidor. Las imágenes son gratis.
```

Es información, no un aviso. No tener economía es una forma perfectamente normal de usar
este plugin.

---

## Configuración

Referencia completa en [`plugin/src/main/resources/config.yml`](plugin/src/main/resources/config.yml),
comentado línea a línea.

```yaml
descarga:
  tiempo-conexion-segundos: 5     # timeout de conexión
  tiempo-lectura-segundos: 15     # timeout de lectura total
  tamano-maximo-mb: 5             # corte duro por bytes, contando durante la descarga
  max-redirecciones: 3
  hilos: 4                        # tamaño del pool          [requiere reiniciar]
  capacidad-cola: 20              # capacidad de la cola     [requiere reiniciar]

imagen:
  dimension-maxima: 4096          # píxeles máximos por lado
  frames-maximos: 100             # TECHO DEL SERVIDOR, ningún nodo lo supera
  retardo-defecto-ms: 100
  retardo-minimo-ms: 100          # 100 ms = tope de 10 fps

animacion:
  distancia-maxima-vision: 24.0   # bloques
  periodo-rastreo-ticks: 5        # periodo del raytrace     [requiere reiniciar]
  pausa-por-mirada: 5             # minutos; 0 la desactiva

cache:
  tamano-maximo-mb: 128           # techo del caché LRU

economia:
  precio-por-imagen: 0.0

slots:
  por-defecto: 1
  maximo-nodo: 10

frames:
  por-defecto: 100
  maximo-nodo: 100
```

Casi todo se aplica al vuelo con `/imagen recargar`. Los tres marcados
`[requiere reiniciar]` no, porque cambiar un pool de hilos o un temporizador vivo a mitad de
vuelo podría descartar peticiones.

Cualquier valor que falte o sea inválido cae a su valor por defecto **con un aviso en
consola**. El plugin nunca se queda inutilizable por una errata.

### Pausa por mirada prolongada

Si un jugador lleva más tiempo del configurado en `pausa-por-mirada` mirando el mismo marco
sin apartar la vista, su animación de ese mapa se detiene.

- El contador es de **mirada seguida**. Apartar la vista lo reinicia a cero, así que quien
  mira, se va y vuelve nunca lo activa.
- Es **por jugador y por marco**. A nadie más le afecta.
- Se congela en el frame actual: no reinicia al frame 0 ni se queda en blanco.
- **Apartar la vista y volver a mirar es lo único que lo reanuda.**

Quien está jugando normalmente no llega nunca a cinco minutos de mirada fija. Quien llega
casi siempre está AFK delante de un cuadro, donde la animación solo está gastando red.

Ojo: pausar **no libera memoria**. La imagen sigue en caché y vuelve al instante. Lo único
que se deja de hacer es enviar paquetes a ese jugador. Si lo que quieres es bajar la RAM, la
palanca es el cupo de frames, no esto.

---

## Almacenamiento

```
plugins/FraMCit/
  config.yml
  messages.yml
  index.json          <- id de mapa, url, autor, poseedores, fecha, formato, frames, retardos
  images/
    <id>.fmc          <- frames ya convertidos a bytes de paleta de mapa
```

Las imágenes se guardan **ya convertidas** a bytes de paleta, no como el archivo original.
Arrancar el servidor es leer bytes y ya: ni decodificar ni convertir. Además garantiza que
un cuadro colgado se vea siempre igual, aunque el algoritmo de conversión cambie en el
futuro.

`index.json` es texto legible que puedes inspeccionar. `.fmc` es una cabecera corta (magia,
versión de formato, versión de paleta, número de frames) seguida de los frames en crudo de
16384 bytes, escrito de forma atómica con un archivo temporal y `ATOMIC_MOVE`.

**Desinstalar es seguro.** Quitas el jar y los mapas se quedan en blanco. No se toca ni se
corrompe ningún dato del mundo.

---

## Cómo funciona la animación

Es la decisión central del diseño, y no necesita NMS.

La API de Bukkit ofrece justo lo que hace falta:

- `MapRenderer.render(MapView, MapCanvas, Player)` recibe al jugador, así que un renderer
  marcado como **contextual** puede pintar algo distinto para cada jugador sobre el mismo
  mapa.
- `Player.sendMap(MapView)` renderiza y envía ese mapa **a un solo jugador**.

Juntando ambas: el plugin lleva un índice de frame por jugador, un raytrace periódico decide
quién mira qué marco, y el planificador avanza y envía frames solo a esos jugadores. Si
nadie mira, no se envía nada.

El coste escala entonces con el **número de espectadores**, no con el número de GIFs
colgados.

### El crafteo está bloqueado

Los mapas de FraMCit son inertes: no se pueden usar en mesa de crafteo, mesa de cartografía
ni en un bloque Crafter. La casilla de resultado sale vacía y tus ingredientes nunca se
consumen.

Lo que de verdad importa es la ampliación. Ampliar un mapa produce un **mapa nuevo con otro
id** del que el plugin no sabe nada, así que el jugador acabaría con un mapa en blanco
convencido de que el plugin está roto. Copiar no rompe nada técnicamente, pero reparte
cuadros sin que el staff pueda seguirles la pista, así que va al mismo saco.

Se bloquea la categoría entera en vez de una lista de recetas, para que una receta futura de
Minecraft que consuma mapas no pueda abrir un agujero en silencio.

---

## Seguridad

Un comando que hace que tu servidor descargue URLs arbitrarias es un riesgo de SSRF. Estas
protecciones existen con independencia de quién tenga permiso para usarlo, y **ninguna es
configurable**: no se puede desactivar por archivo lo que está para proteger el servidor.

- **Solo `http` y `https`.** Nada de `file:`, `ftp:`, `jar:` ni otros.
- **Destinos internos bloqueados**: loopback, link-local y rangos privados (10/8,
  172.16/12, 192.168/16, CGNAT 100.64/10, ULA de IPv6 y equivalentes). Se valida la *IP
  resuelta*, no solo el texto del host.
- **Cada salto de redirección se re-valida.** Las redirecciones se siguen a mano, nunca
  automáticamente, así que una redirección a `127.0.0.1` se caza.
- **Corte duro de bytes aplicado durante el stream**, ignorando lo que declare
  `Content-Length`.
- **Timeouts de conexión y de lectura**, y límite de redirecciones.
- **Límites de dimensiones y de frames comprobados en la cabecera antes de decodificar**,
  así una bomba de descompresión se rechaza antes de poder reservar memoria.

Cada creación de imagen se registra con nombre y UUID del jugador, URL completa, tamaño,
formato e id de mapa resultante. Ese registro es un requisito, no una funcionalidad: es la
única forma de que el staff sepa quién colgó qué.

FraMCit **no** modera el contenido. La responsabilidad de lo que publican los jugadores es
de ellos y de tu staff.

### Limitación conocida: reenlace de DNS

Mejor decirlo claro que esconderlo.

`HttpClient` resuelve el nombre **dos veces**: una cuando validamos la IP y otra al
conectar. Un atacante que controle el DNS de su propio dominio puede cambiar la respuesta
entre ambas y hacer que la conexión acabe en una IP interna que la validación nunca vio. Es
el bypass clásico de esta clase de protección.

La caché de DNS de 30 segundos de la JVM hace que en la práctica las dos resoluciones
coincidan, lo que **estrecha** la ventana y dificulta el ataque, pero no lo elimina: cada
expiración del TTL abre una nueva. Arrancar el servidor con `-Dsun.net.inetaddr.ttl=0` la
reabre por completo, y FraMCit avisa con énfasis al arrancar si detecta eso.

Las tres soluciones técnicas se investigaron y se descartaron por ser peores que la
enfermedad: reescribir la URI a la IP rompe la verificación de certificados TLS (un agujero
MITM crítico), `InetAddressResolverProvider` es global a toda la JVM y afectaría a Paper y
a los demás plugins, y hacer un cliente HTTP artesanal sobre `SSLSocket` significa escribir
un parser HTTP/1.1 manual en la ruta más sensible del plugin.

Así que el riesgo residual se **acepta por escrito**, documentado en
[`docs/constitution.md`](docs/constitution.md) §6.1.

---

## Límites

| Límite | Valor |
|---|---|
| Tamaño máximo de descarga | 5 MB |
| Frames máximos de un GIF | 100 |
| Frames por segundo máximos | 10 |
| Dimensiones máximas de origen | 4096 × 4096 |
| Timeout de conexión | 5 s |
| Timeout de lectura total | 15 s |
| Redirecciones seguidas | 3 |
| Descargas simultáneas (todo el servidor) | 4 |

Formatos aceptados: PNG, JPEG, BMP, GIF, WEBP.

**El WEBP animado se reproduce a 10 fps fijos.** La librería que lo decodifica lee todos los
frames pero no expone sus retardos. Como el plugin ya topa a 10 fps, la diferencia es
inapreciable en la mayoría de archivos.

Una imagen es un mapa: 128×128. Los mosaicos de varios marcos están fuera del alcance.

---

## Compilar desde el código

```bash
git clone https://github.com/Dasannn/FraMCit.git
cd FraMCit/plugin
./gradlew shadowJar
```

Sale en `plugin/build/libs/FraMCit-1.0.0.jar`. Necesitas JDK 25; Gradle se descarga la
toolchain si no lo tienes en el `PATH`.

Los tests se lanzan con `./gradlew test` — hay 230, concentrados donde un fallo sería
silencioso: el guardia de URLs, el corte por bytes, la conversión de paleta, la resolución
de cupos, la posesión y el contador de mirada.

---

## Cómo se construyó este proyecto

Este plugin se desarrolló escribiendo primero la especificación. Cada regla del código sale
de una decisión escrita, y esos documentos están en [`docs/`](docs/):

| Documento | Qué contiene |
|---|---|
| [`constitution.md`](docs/constitution.md) | Principios no negociables y el piso de seguridad |
| [`spec.md`](docs/spec.md) | Spec 001 — imágenes, GIFs, animación, el item mapa |
| [`spec-002-economia.md`](docs/spec-002-economia.md) | Spec 002 — cobro opcional vía Vault |
| [`spec-003-slots.md`](docs/spec-003-slots.md) | Spec 003 — cupos por jugador |
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Componentes, hilos, almacenamiento, riesgos |
| [`plan.md`](docs/plan.md) / [`tasks.md`](docs/tasks.md) | Fases y lista de tareas |

Los informes de fase incluyen correcciones donde un informe de implementación exageró lo que
estaba realmente verificado. Se dejaron en vez de limpiarlos, porque dejar constancia de qué
está *medido* y qué está *proyectado* vale más que un historial ordenado.

---

## Contribuir

Los issues y los pull requests son bienvenidos. Dos cosas que conviene saber antes:

1. **Lee [`docs/constitution.md`](docs/constitution.md).** Hay cosas deliberadamente fuera
   del alcance: búsqueda de imágenes por palabras clave, mosaicos de varios marcos,
   moderación de contenido, NMS, soporte de Folia y soporte multi-versión. Un PR que añada
   una de esas se rechazará por buena que sea su calidad.
2. **El piso de seguridad no se negocia.** Un PR que haga configurables las protecciones
   SSRF, o que añada un nodo de permiso capaz de levantar un techo de seguridad, no se
   mergeará.

Si encuentras un fallo de seguridad, abre un aviso privado en vez de un issue público.

---

## Licencia

[MIT](LICENSE) © 2026 Dasannn

Puedes usarlo, modificarlo, ponerlo en un servidor de pago o meterlo en algo cerrado.
Mantén el aviso de copyright y listo.
