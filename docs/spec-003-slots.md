# Spec 003 - Slots por jugador

Tercer spec de FraMCit. Limita **cuantas imagenes puede tener a la vez** cada jugador,
segun los nodos de permiso que le haya dado el administrador.

Depende de `docs/spec.md` (spec 001) y convive con `docs/spec-002-economia.md` (spec 002).
Requiere las enmiendas de la constitution seccion 3 y 4.2 fechadas el 2026-09-10.

## 1. Que resuelve

Hoy un jugador con `framcit.imagen` puede crear imagenes sin limite. Este spec introduce
un cupo por jugador, graduable con permisos, para que el staff pueda dar mas margen a unos
rangos que a otros sin tocar configuracion por jugador.

Lo que **no** introduce, y sigue fuera del proyecto salvo aprobacion nueva: cooldown entre
usos, lista blanca de dominios, cupos por mundo, y transferir imagenes entre jugadores.

## 2. Que ocupa un slot

Un slot lo ocupa **una imagen que el jugador posee**, no el marco donde la cuelga ni el
item que lleva encima.

Consecuencia buscada, y la que pidio el owner: el jugador puede descolgar el mapa del
marco, guardarlo en un cofre, volver a colgarlo en otro sitio, y sigue ocupando ese mismo
slot. Nunca se queda sin imagen por mover un item.

Reglas:

1. Crear una imagen nueva ocupa un slot.
2. Pedir una URL que **otro jugador** ya creo ocupa un slot igual, aunque no se descargue
   nada. El acierto de cache ahorra ancho de banda, no cupo.
3. Pedir una URL que **ya posees** no ocupa un segundo slot: recibes otra copia del mismo
   item mapa y el cupo no se mueve.
4. Una imagen deja de ocupar slot solo cuando su dueno la borra (seccion 5) o cuando un
   administrador la purga.

### 2.1 Posesion compartida

Spec 001 seccion 7.1 reutiliza el mapa por URL: dos jugadores que piden la misma URL
acaban apuntando al **mismo `mapId`** y al mismo archivo en disco. Por eso el cupo no se
puede contar mirando quien creo cada entrada del indice: hay que registrar, aparte,
**quien posee que**.

- Una imagen puede tener varios poseedores a la vez.
- Que un poseedor la borre no afecta a los demas: el mapa sigue visible para ellos.
- El archivo de disco se borra cuando se queda **sin ningun poseedor**. Ese es el unico
  momento en que los mapas colgados de esa imagen se quedan en blanco.

### 2.2 Imagenes anteriores a este spec

Las imagenes que ya existen en `index.json` tienen autor pero no tienen registro de
posesion. Al arrancar por primera vez con este spec, cada imagen existente se asigna como
posesion de su autor.

Una imagen cuyo autor sea desconocido (`authorUuid` vacio) no se asigna a nadie: no cuenta
para el cupo de nadie y **no se borra**. Seguir mostrandose es mas importante que cuadrar
un contador.

## 3. Nodos de permiso

| Nodo | Por defecto | Efecto |
|---|---|---|
| `framcit.slots.1` | `false` | El jugador puede tener 1 imagen |
| `framcit.slots.2` | `false` | 2 imagenes |
| `framcit.slots.3` | `false` | 3 imagenes |
| `framcit.slots.ilimitado` | `op` | Sin cupo |

Reglas de resolucion:

1. Si el jugador tiene `framcit.slots.ilimitado`, no hay limite y no se comprueba nada mas.
2. Si tiene varios nodos `framcit.slots.<n>`, **gana el mayor**. Tener `framcit.slots.1` y
   `framcit.slots.3` da 3, no 4 ni 1. Los nodos no se suman: son escalones.
3. Si no tiene ninguno, se aplica `slots.por-defecto` de `config.yml` (por defecto **1**).

El plugin busca nodos de `framcit.slots.1` hasta `slots.maximo-nodo` (por defecto 10). Solo
los tres primeros se declaran en `paper-plugin.yml`, para que aparezcan en las listas de
los gestores de permisos; los demas funcionan igual si el administrador los concede.

El plugin **declara** los nodos y pregunta por ellos. A quien se le conceden es decision
del administrador, como manda la constitution seccion 4.2.

### 3.1 Bajar el cupo no borra nada

Si un administrador reduce el cupo de un jugador que ya esta por encima, no se borra
ninguna imagen. El jugador simplemente no puede crear mas hasta que baje del limite por su
cuenta. El plugin nunca destruye contenido por un cambio de permisos.

## 4. Cuando se comprueba el cupo

El cupo se comprueba **dos veces**, por la misma razon por la que el spec 002 comprueba el
saldo dos veces: entre el principio y el final de una descarga puede cambiar todo.

Orden completo del flujo, integrando los tres specs:

1. Permiso `framcit.imagen` (spec 001).
2. **Cupo disponible** (este spec). Sin cupo, se corta aqui: no se comprueba saldo, no se
   descarga nada, no se cobra nada. Es la comprobacion mas barata y va primera.
3. Saldo suficiente (spec 002).
4. Descarga y procesado.
5. **Cupo disponible otra vez**, justo antes de entregar. Si el jugador lleno su cupo
   mientras se descargaba, no se entrega el mapa, **no se cobra**, y se le explica por que.
6. Cobro (spec 002).
7. Entrega del item.

La regla 2.3 (URL que ya posees) se evalua en el paso 2: si ya la posees, el cupo no
bloquea aunque estes al limite.

## 5. Borrar una imagen propia

### 5.1 `/imagen mias`

Lista las imagenes que posee quien ejecuta el comando: id, URL, fecha, y si es estatica o
animada. Muestra tambien el cupo: `Usados 2 de 3`.

Permiso: `framcit.imagen`. Un jugador solo ve lo suyo.

### 5.2 `/imagen borrar <id>`

Libera el slot de esa imagen.

- Solo funciona sobre imagenes que posee quien ejecuta el comando. Sobre las de otro
  responde que no es suya, sin decir de quien es.
- Es destructivo e irreversible: requiere confirmacion explicita repitiendo el comando con
  `confirmar` al final. Sin esa palabra solo se muestra que va a pasar.
- Si nadie mas posee esa imagen, su archivo se borra del disco y los mapas colgados de ella
  se quedan en blanco. Si queda algun otro poseedor, no se borra nada del disco y para los
  demas no cambia nada.
- El aviso de confirmacion dice cual de los dos casos aplica.

## 6. Comandos de administracion

Amplian la seccion 8 del spec 001. Todos con permiso `framcit.admin`.

### 6.1 `/imagen listar [jugador] [pagina]`

Sin jugador, lista todas las imagenes del servidor (comportamiento actual del spec 001).
Con un jugador, lista solo las que ese jugador posee, con su cupo usado y su limite.

### 6.2 `/imagen uso [jugador]`

Consumo real de recursos. Sin jugador, el total del servidor; con jugador, lo suyo.

Informa de:

- Numero de imagenes y numero total de frames.
- Espacio en disco ocupado por los `.fmc`, medido, no estimado.
- Memoria ocupada **ahora mismo** por el cache LRU, y cuantas de esas imagenes estan
  cargadas. Una imagen que no esta en el cache no ocupa memoria: el informe lo dice asi en
  lugar de sumar lo que ocuparia si se cargara.
- Cuantas imagenes estan animandose en este momento, que es lo unico que consume tiempo de
  tick.

Ningun numero de este comando puede ser una proyeccion presentada como medida.

### 6.3 `/imagen purgar-jugador <jugador> confirmar`

Borra **todas** las imagenes que posee ese jugador y le deja el cupo a cero.

- Sin la palabra `confirmar`, no borra: muestra cuantas imagenes son y cuanto espacio
  liberaria.
- Se aplican las reglas de posesion compartida de la seccion 2.1: solo desaparece del disco
  lo que no posea nadie mas.
- Queda registrado en el log: que administrador lo ejecuto, sobre quien, y cuantas imagenes
  borro.

## 7. Configuracion

```yaml
slots:
  # Cupo de quien no tiene ningun nodo framcit.slots.<n>.
  # 0 deja el comando inutilizable para quien no tenga nodo.
  por-defecto: 1
  # Hasta que numero se buscan nodos framcit.slots.<n>.
  maximo-nodo: 10
```

Un valor negativo o invalido en `por-defecto` se trata como 1, con aviso en el log. El
plugin nunca deja a un jugador sin poder usar el comando por un error de configuracion.

## 8. Relacion con el spec 002 (economia)

- El cupo se comprueba antes que el saldo. Un jugador sin cupo nunca llega a que se le mire
  el dinero.
- Quedarse sin cupo a mitad de una descarga **no cobra**, igual que quedarse sin saldo.
- Borrar una imagen **no devuelve dinero**. El spec 002 no tiene reembolsos y este spec no
  los introduce. Liberar un slot y recuperar lo pagado son cosas distintas.
- Pedir una URL que ya posees no gasta cupo pero **si se cobra**, porque el spec 002 ya
  establece que un acierto de cache cuesta el precio completo. Es deliberado: el cupo mide
  ocupacion, el precio mide uso.

## 9. Mensajes al jugador

Todos en espanol, siguiendo la seccion 9 del spec 001.

| Situacion | Mensaje |
|---|---|
| Cupo lleno al pedir | Ya tienes 3 de 3 imagenes. Borra una con /imagen borrar <id> o mira las tuyas con /imagen mias. |
| Cupo lleno al entregar | Llenaste tu cupo mientras se descargaba. No se creo la imagen y no se te cobro nada. |
| Cupo por defecto a cero | No tienes ningun espacio de imagenes asignado. Pideselo a un administrador. |
| Lista vacia | No tienes ninguna imagen. |
| Borrar, confirmacion | Vas a borrar la imagen <id>. Los mapas que la usen se quedaran en blanco. Repite el comando con "confirmar" al final. |
| Borrar, confirmacion compartida | Vas a soltar la imagen <id>. Otros jugadores la tienen, asi que sus mapas seguiran funcionando. Repite el comando con "confirmar" al final. |
| Borrada | Imagen borrada. Te queda 1 de 3 en uso. |
| Id que no existe | No existe ninguna imagen con ese id. |
| Id de otro jugador | Esa imagen no es tuya. |

Ningun mensaje revela quien posee una imagen ajena. Para eso estan los comandos de
administracion.

## 10. Criterios de aceptacion

1. Un jugador con `framcit.slots.1` crea una imagen. Al pedir una segunda, se le rechaza
   sin descargar nada y sin cobrarle.
2. Ese mismo jugador descuelga el mapa, lo mete en un cofre y lo vuelve a colgar. Sigue
   teniendo 1 de 1: mover el item no gasta ni libera cupo.
3. Borra su imagen con `/imagen borrar <id> confirmar` y ya puede crear otra.
4. Un jugador con `framcit.slots.1` y `framcit.slots.3` tiene 3, no 4.
5. Un jugador sin ningun nodo tiene el cupo de `slots.por-defecto`.
6. Un jugador pide una URL que otro ya creo: no se descarga nada, pero le ocupa un slot.
7. Un jugador pide una URL que ya posee: recibe otra copia del item y su cupo no cambia.
8. Dos jugadores poseen la misma imagen. Uno la borra: al otro le sigue funcionando el
   mapa y el archivo sigue en disco.
9. El ultimo poseedor la borra: el archivo desaparece del disco y los mapas quedan en
   blanco.
10. Un administrador baja el cupo de un jugador que tiene 3 imagenes a 1. No se borra
    ninguna; el jugador no puede crear mas.
11. `/imagen uso` sin argumentos da numeros de disco que coinciden con el tamano real de la
    carpeta, y separa lo cargado en memoria de lo que solo esta en disco.
12. `/imagen uso <jugador>` da solo lo de ese jugador.
13. `/imagen purgar-jugador <jugador>` sin `confirmar` no borra nada y dice cuanto borraria.
14. Con `confirmar`, borra lo suyo, respeta lo que posean otros, y lo registra en el log.
15. Un jugador con `framcit.slots.ilimitado` crea imagenes sin que ningun cupo le pare.
16. Al arrancar con imagenes creadas antes de este spec, cada una queda asignada a su autor
    y los cupos cuadran sin intervencion manual.
17. Con el cobro del spec 002 activo y el cupo lleno, el jugador no pierde dinero.
