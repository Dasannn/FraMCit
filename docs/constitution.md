# Constitution

Principios no negociables del proyecto. Cualquier decision de spec, arquitectura o
implementacion que contradiga este documento se rechaza, o se cambia este documento
primero con aprobacion explicita del owner.

## 1. Identidad del proyecto

**FraMCit** (paquete `com.dasannn.framcit`): plugin de Paper que permite a un jugador
convertir una URL de imagen o GIF en un mapa de Minecraft colocable en un item frame. GIFs animados solo se animan para el jugador
que esta mirando el marco.

Owner y unica autoridad de producto: el propietario del repositorio.

## 2. Plataforma objetivo (fija)

- Servidor: **Paper 26.2** (verificado 2026-09-10 contra la API de PaperMC: canal
  STABLE, build 123).
- Runtime: **Java 25** (requisito oficial de Paper 26.1+).
- API: Paper API. No Spigot-only, no Bukkit legacy.
- No se usa NMS ni reflexion contra clases internas del servidor. Si una funcionalidad
  solo es posible con NMS, se documenta y se pide aprobacion antes de implementarla.
- No hay soporte multi-version. Una sola version objetivo.

## 3. Alcance

Tres specs:

- **Spec 001** (`docs/spec.md`): imagenes y GIFs en marcos.
- **Spec 002** (`docs/spec-002-economia.md`): cobro opcional por imagen mediante Vault.
- **Spec 003** (`docs/spec-003-slots.md`): cupos por jugador segun sus nodos de permiso,
  tanto de imagenes acumuladas como de frames por imagen.

Dentro del alcance:
- Comando que entrega al jugador un item mapa con la imagen de una URL.
- Soporte de imagenes estaticas y de GIF animado.
- Animacion de GIF unicamente para jugadores que miran el marco.
- Persistencia: las imagenes sobreviven al reinicio del servidor.
- Cobro opcional por imagen, desactivado mientras no se configure.
- Cupo de imagenes por jugador y de frames por imagen, graduables con nodos de permiso.

Fuera del alcance (requiere aprobacion nueva para entrar):
- Busqueda de imagenes por palabras clave / integracion con APIs de Google u otras.
- Mosaicos multi-marco (imagenes de mas de un mapa).
- Audio, musica o cualquier funcionalidad de sonido.
- Interfaz web, panel de administracion, base de datos externa.
- Cualquier trato con dinero que no sea cobrar por crear una imagen: reembolsos manuales,
  transferencias entre jugadores, precios por rango, ofertas o descuentos temporales.

### 3.1 El plugin funciona solo

Ninguna dependencia externa puede ser obligatoria. FraMCit tiene que arrancar y funcionar
en un servidor que no tenga Vault, ni plugin de economia, ni gestor de permisos.

El destino de este plugin es un servidor que **todavia no tiene economia**, asi que esto
no es una hipotesis comoda: es el caso real. Sin economia disponible, las imagenes son
gratis y el plugin no se queja. El dia que ese servidor monte la suya, el cobro se activa
poniendo un precio en `config.yml`, sin recompilar nada.

Una dependencia que impida arrancar es un fallo de diseno, no una funcionalidad.

## 4. Principios de producto

1. **Un comando, un resultado.** El jugador pega un link y recibe un mapa. Sin menus,
   sin GUIs, sin pasos intermedios.
2. **Acceso controlado por el servidor, no por el plugin.** El plugin decide *como* se
   pide permiso, nunca *a quien* se le concede. Declara sus nodos y pregunta por ellos;
   quien reparte es el administrador con su gestor de permisos.

   El comando viene **cerrado por defecto** (`framcit.imagen` con `default: op`): un
   servidor recien instalado no deja que cualquiera haga que el servidor descargue de
   internet. Abrirlo es una decision consciente del administrador, no el punto de partida.

   Lo que sigue sin existir, y no se anade sin aprobacion nueva: cooldown y lista blanca
   de dominios. Quien puede usar el comando se decide fuera del plugin; con que frecuencia
   puede usarlo, no se limita.

   > **Enmienda del 2026-09-10 (segunda).** El *limite de mapas por jugador* sale de esta
   > lista de prohibiciones y pasa a existir, con dos formas graduables por nodos de
   > permiso (`docs/spec-003-slots.md`): un cupo de imagenes poseidas y un cupo de frames
   > por imagen. Sigue cumpliendo el principio: el plugin declara los nodos, el
   > administrador decide quien recibe cuantos. El cupo de frames ademas nunca puede
   > superar el limite global de la seccion 6.6, que es piso de seguridad y no se negocia
   > con permisos. El cooldown y la lista blanca siguen prohibidos.

   > **Enmienda del 2026-09-10.** Hasta esta fecha el principio era *acceso abierto*
   > (`default: true`, cualquier jugador). El owner decidio cerrarlo por defecto tras
   > comprobar el plugin en un servidor real. Lo demas de la decision original —sin
   > cooldown, sin lista blanca, sin limite de mapas— sigue vigente.
3. **Lo que cuelgas sigue colgado.** Una imagen colocada en un marco funciona tras
   reiniciar el servidor, indefinidamente, sin intervencion manual.
4. **Fallar en voz alta.** Si una URL no sirve, el jugador recibe un mensaje claro en
   chat que dice por que. Nunca un mapa en blanco sin explicacion.

## 5. Principios tecnicos

1. **El servidor nunca se bloquea.** Toda operacion de red y de decodificacion de
   imagen ocurre fuera del hilo principal. Ninguna descarga, ningun decode, ningun I/O
   de disco en el main thread. Un TPS caido por este plugin es un bug de severidad alta.
2. **Ancho de banda proporcional a la atencion.** Solo se envian frames de mapa a los
   jugadores que realmente estan mirando el marco. Un GIF que nadie mira no consume red.
3. **Minimo indispensable.** Sin abstracciones especulativas, sin capas de servicios sin
   uso, sin dependencias nuevas para lo que resuelven la stdlib de Java o la Paper API.
4. **Sin estado invisible.** El estado persistido vive en la carpeta de datos del plugin,
   en formato legible o inspeccionable, y su formato se documenta.
5. **El plugin se puede desinstalar.** Quitar el jar no corrompe el mundo. Los mapas
   quedan en blanco, nada mas.

## 6. Piso de seguridad (no negociable)

Estas protecciones existen con independencia de quien tenga permiso para usar el comando.
Cerrarlo por defecto (seccion 4.2) reduce quien puede disparar una descarga, pero no
cambia nada de lo de abajo: un jugador con permiso legitimo sigue pudiendo pegar una URL
hostil, y sin estas protecciones el servidor se convierte en un arma. No limitan a nadie
que use el plugin para lo que es:

1. Solo esquemas `http` y `https`. Nada de `file:`, `ftp:`, `jar:`, ni otros.
2. Bloqueo de destinos internos: loopback, link-local, y rangos privados
   (10/8, 172.16/12, 192.168/16, y equivalentes IPv6). Se valida la IP resuelta, no
   solo el texto del host, y se re-valida en cada redireccion.
3. Limite duro de bytes descargados, aplicado durante el stream, no confiando en el
   header `Content-Length`.
4. Timeout de conexion y de lectura.
5. Limite de redirecciones seguidas.
6. Limite de dimensiones y de numero de frames de GIF antes de decodificar, para evitar
   agotar memoria con una imagen manipulada (decompression bomb).

Los valores concretos de cada limite se definen en spec.md. Que existan, no.

### 6.1 Limitacion conocida: reenlace de DNS (R6)

El punto 2 exige validar la IP resuelta. La implementacion de F2 lo cumple, pero queda una
grieta inherente a como funciona `HttpClient`: el DNS se resuelve **dos veces**, una al
validar y otra al conectar. Entre ambas, un atacante que controle el DNS de su propio
dominio puede cambiar la respuesta y hacer que la conexion acabe en una IP interna que la
validacion nunca vio. Es el bypass clasico de esta clase de proteccion.

Hoy lo tapa la cache de DNS de la JVM (30 segundos por defecto para respuestas positivas),
que hace que las dos resoluciones devuelvan lo mismo. Eso es una casualidad afortunada del
entorno, **no una garantia del diseno**: arrancar el servidor con
`-Dsun.net.inetaddr.ttl=0` reabre la ventana por completo.

Obligaciones y resolucion formal (F8.9):

1. **Aviso en log al arrancar**: Cumplido en F7.10 (`DnsCacheChecker`). El plugin comprueba el
   TTL de cache de DNS efectivo de la JVM y avisa con severidad en el log si la cache esta
   desactivada (`0`).
2. **Aceptacion explicita del riesgo residual (cerrado en F8.9)**: Tras una investigacion
   exhaustiva en Java 25, se determino que las vias tecnicas para forzar la IP validada en
   `HttpClient` acarrean riesgos tecnicos y de seguridad superiores al beneficio:
   - *Reescribir URI a la IP*: Rompe la verificacion de certificados TLS en HTTPS salvo que se
     desactive la comprobacion del nombre de host, lo que abriria un agujero MITM critico.
   - *InetAddressResolverProvider (SPI)*: Es global para toda la JVM, afectando a Paper, la
     autenticacion de Mojang y a todos los demas plugins del servidor de forma invasiva.
   - *Cliente HTTP artesanal sobre SSLSocket*: Implicaria reimplementar un parser HTTP/1.1
     manual en la ruta mas sensible del plugin, aumentando drásticamente la superficie de fallos.
   Por tanto, se acepta formalmente por escrito el riesgo residual: la cache de 30 s por defecto
   de la JVM mitiga significativamente el vector al estrechar la ventana temporal y dificultar
   el ataque en la practica, aunque no lo elimina por completo ya que cada expiracion abre una
   nueva ventana para reintentos del atacante. Este riesgo residual se asume debido a que las
   alternativas tecnicas (desactivar la verificacion de hostnames en TLS o registrar un SPI global)
   introducen vulnerabilidades criticas (MITM) o inestabilidad global en el servidor. FraMCit
   alerta activamente al arrancar si dicha cache protectora se encuentra deshabilitada.

## 7. Contenido de terceros

El plugin no modera el contenido de las URLs. La responsabilidad del contenido publicado
es del jugador que ejecuta el comando y del staff del servidor. El plugin registra en log
quien cargo que URL y cuando, para que el staff pueda actuar. Ese log es un requisito, no
una funcionalidad opcional.

## 8. Proceso de desarrollo (Agent Flow; enmienda 2026-10-23)

La planificacion por fases y la secuencia obligatoria de documentos fueron
utiles para construir la primera version, pero **no son requisitos para cada
bug, mejora o cambio posterior**. Las reglas operativas vigentes son las de
`AGENTS.md`.

- Esta constitucion sigue definiendo el producto, sus limites y las
  protecciones tecnicas y de seguridad. Ninguna preferencia de rapidez
  permite rebajarlas.
- Consultar la especificacion relevante y `ARCHITECTURE.md` cuando haga
  falta entender contratos, comportamiento o dependencias. `docs/plan.md`
  y `docs/tasks.md` conservan el historial de las fases originales: no
  imponen nuevas fases o aprobaciones para corregir bugs.
- Localizar el codigo responsable, sus llamadores y pruebas antes de editar.
  Resolver directamente los cambios de alcance claro; investigar o delegar
  cuando haya incertidumbre; pedir revision independiente cuando el riesgo
  lo justifique.
- No crear specs, planes o informes por rutina. Actualizar la documentacion
  pertinente cuando cambien requisitos, contratos o decisiones duraderas.
- Los cambios de alcance del producto, requisitos de seguridad o decisiones
  arquitectonicas significativas requieren aprobacion del owner.
- Trabajar en una rama separada; no fusionar a `main` sin autorizacion.
