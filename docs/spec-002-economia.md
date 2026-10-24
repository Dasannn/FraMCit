# Spec 002 - Cobro por imagen (Vault)

Cobrar al jugador por crear una imagen, cuando el servidor tenga economia.

Rige sobre este documento: `docs/constitution.md`, en particular la seccion 3.1.
Complementa a `docs/spec.md` (spec 001); no lo sustituye.

## 1. Resumen

Crear una imagen con `/imagen` puede costar dinero del juego. El precio lo pone el
administrador en `config.yml`. Si el servidor no tiene economia instalada, o el precio es
cero, las imagenes son gratis y el jugador no ve nada distinto a lo de hoy.

## 2. El plugin funciona sin economia

Es el requisito que manda sobre todos los demas de este spec. El servidor al que va
destinado FraMCit **no tiene economia todavia**.

- Vault se declara como dependencia **blanda** (`soft-depend`), nunca obligatoria.
- Sin Vault instalado, o con Vault pero sin ningun plugin de economia registrado, o con
  el precio a cero: **las imagenes son gratis**. El plugin arranca con normalidad.
- En ese caso no se menciona el dinero en ningun mensaje al jugador. No existe el cobro,
  asi que no se habla de el.
- Al arrancar se registra **una** linea en el log diciendo si el cobro esta activo y por
  que. Una sola, informativa, no un aviso alarmante: no tener economia es un estado
  normal y esperado, no un problema.
- Activar el cobro mas adelante es poner un precio en `config.yml` y recargar. Sin
  recompilar, sin tocar codigo, sin reinstalar.

## 3. Precio

- **Un precio fijo por imagen**, el mismo para una imagen estatica que para un GIF.
- Se configura en `config.yml` bajo `economia.precio-por-imagen`. Por defecto **0**, que
  significa gratis.
- Un precio negativo o invalido se trata como cero, con aviso en el log. El plugin nunca
  da dinero al jugador.
- El precio se muestra al jugador con el formato del propio plugin de economia (que es
  quien sabe como se llama la moneda del servidor), no inventado por FraMCit.

## 4. Cuando se cobra

El orden importa, porque determina que pasa cuando algo falla:

1. **Antes de descargar**: se comprueba que el jugador tiene saldo suficiente. Si no lo
   tiene, se le dice cuanto cuesta y cuanto le falta, y ahi termina: no se descarga nada.
   Un jugador sin saldo no puede hacer que el servidor gaste ancho de banda.
2. **Al entregar**: se cobra en el momento en que el mapa entra en su inventario o cae a
   sus pies, no antes.

De ahi se siguen estas garantias:

- **Si algo falla, el jugador no paga.** URL rota, timeout, formato no soportado,
  desconexion a mitad: no hay cobro, porque el cobro ocurre despues.
- **No existen los reembolsos.** No se devuelve dinero porque nunca se cobro por
  adelantado. Un reembolso es una operacion que tambien puede fallar, y cuando falla el
  jugador pierde dinero y el staff recibe una reclamacion. Este diseno elimina ese caso
  en vez de gestionarlo.

### Reinicio durante la entrega

Antes de intentar el cobro, FraMCit persiste el destinatario como una entrega pendiente. Si el
servidor se apaga durante el cobro o antes de que el mapa llegue al inventario/suelo, al volver
a conectarse el jugador recibe el mapa sin un segundo cobro. Esta política prioriza no perder
una compra; como Vault no ofrece una transacción idempotente que abarque dinero e inventario, un
apagón justo antes de completar el cobro puede terminar en una entrega gratuita. La marca se
borra del índice una vez completada la entrega.

Entre la comprobacion y el cobro pasa tiempo (la descarga). El saldo puede haber cambiado
en ese intervalo: el jugador pudo gastarlo en otra cosa. Si al cobrar ya no alcanza, **no
se entrega el mapa** y se le explica por que. El mapa creado se descarta.

## 5. Imagenes ya cargadas (acierto de cache)

Pedir una URL que ya esta almacenada **cuesta lo mismo**. El jugador paga por tener la
imagen, no por el trabajo que le costo al servidor.

Es deliberado: el precio es predecible y no depende de si otro jugador se adelanto. Se
comprueba el saldo igual, aunque no haya descarga.

## 6. Exencion

- Permiso **`framcit.gratis`**, por defecto `false`: quien lo tenga no paga nunca.
- No se le comprueba el saldo ni se le menciona el precio: para el, el comando funciona
  como si el cobro no existiera.
- Se gestiona desde el gestor de permisos del servidor, como los demas nodos.
- Los operadores **no** estan exentos automaticamente. Si un administrador quiere no
  pagar, se concede `framcit.gratis` de forma explicita. Que algo sea gratis para alguien
  tiene que poder leerse en la configuracion de permisos, no deducirse de ser operador.

## 7. Permisos, estado completo

Con la enmienda de la constitution 4.2 y este spec:

| Permiso | Por defecto | Que permite |
|---|---|---|
| `framcit.imagen` | `false` | Usar `/imagen <url>`, `mias`, `obtener`, `borrar` |
| `framcit.admin` | `false` | `recargar`, `info`, `purgar`, `listar` y otros comandos de administración |
| `framcit.gratis` | `false` | Crear imagenes sin pagar |

`framcit.imagen` y `framcit.admin` requieren concesión explícita (`default: false`), también
para operadores. LuckPerms puede limitar los nodos a mundos concretos mediante su contexto
`world=<nombre-real-del-mundo>`.

## 8. Mensajes

Se anaden a la tabla del spec 001 seccion 9. Todos en espanol, en `messages.yml`:

| Situacion | Mensaje |
|---|---|
| Saldo insuficiente | No tienes suficiente dinero. Cuesta {precio} y tienes {saldo}. |
| Cobro correcto | Se te han cobrado {precio} por la imagen. |
| Saldo perdido durante la descarga | Ya no tienes suficiente dinero: la imagen no se entrego. |
| Fallo del plugin de economia | No se pudo procesar el pago. Avisa al staff. |

Cuando el cobro esta desactivado, ninguno de estos mensajes aparece jamas.

Si el plugin de economia falla al cobrar (devuelve error o lanza una excepcion), **no se
entrega el mapa**, se registra el fallo completo en el log y el jugador recibe el ultimo
mensaje de la tabla. Nunca se entrega una imagen cuyo cobro fallo.

## 9. Registro

El log obligatorio del spec 001 seccion 8.1 incorpora el importe cobrado, o `gratis` si no
hubo cobro. Con acceso al comando ya restringido, esto permite al staff saber quien creo
cada imagen y cuanto pago.

## 10. Criterios de aceptacion

1. Sin Vault instalado: el plugin arranca, `/imagen` funciona, las imagenes son gratis y
   ningun mensaje habla de dinero.
2. Con Vault pero sin plugin de economia: igual que el anterior.
3. Con economia y precio 0: gratis, sin mencionar dinero.
4. Con economia y precio configurado: se cobra al entregar y el saldo baja exactamente esa
   cantidad, ni mas ni menos.
5. Sin saldo suficiente: no se descarga nada y el mensaje dice cuanto cuesta y cuanto tiene.
6. URL rota con precio configurado: el jugador **no** paga.
7. Acierto de cache: se cobra el precio completo.
8. Con `framcit.gratis`: no paga y no se le comprueba el saldo.
9. Gastar el dinero durante la descarga: no se entrega el mapa y se explica por que.
10. Poner un precio y recargar con `/imagen recargar`: el cobro se activa sin reiniciar.
11. Un jugador sin `framcit.imagen` no puede usar el comando en una instalacion nueva.
