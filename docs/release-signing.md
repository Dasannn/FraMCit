# Firma de releases de FraMCit

El actualizador acepta solo JARs con una firma Ed25519 válida. El verificador usa la clave
pública incluida en `plugin/src/main/resources/framcit-update-public-key.pem`; la clave privada
no pertenece al repositorio ni al JAR.

## Configurar GitHub Actions

1. Crea el environment `release-signing` en la configuración del repositorio y añade el secret
   `FRAMCIT_ED25519_PRIVATE_KEY` con la clave privada PEM que corresponde a la clave pública
   fijada en el plugin. Puede cargarse con GitHub CLI, sin mostrar el valor en pantalla:
   `gh secret set FRAMCIT_ED25519_PRIVATE_KEY --env release-signing --repo Dasannn/FraMCit < <ruta-a-la-clave-privada.pem>`.
2. Activa aprobación de revisores para ese environment y protege la rama por defecto y los tags
   de release. El workflow solo firma releases publicadas estables y comprueba que la clave
   privada corresponde a la clave pública del código protegido.
3. Publica una release con un asset llamado `FraMCit-MAJOR.MINOR.PATCH.jar`, por ejemplo
   `FraMCit-1.1.0.jar`. El workflow añade `FraMCit-1.1.0.jar.sig` a la misma release.

El actualizador verifica el SHA-256 del asset publicado por GitHub, la firma Ed25519 y el
`paper-plugin.yml` contenido en el JAR antes de prepararlo para el próximo reinicio. No requiere
un token de GitHub en el plugin; las releases públicas se consultan sin autenticación. La release
histórica `v1.0.0` no tiene firma; el actualizador la considera instalada si la versión actual ya
es `1.0.0`, pero exige una firma válida para instalar cualquier versión más nueva.

## Rotar la clave

Los clientes instalados confían únicamente en la clave pública que traen en su JAR. Por eso,
reemplazarla directamente impide que esos clientes acepten releases firmadas con la clave nueva.
Una rotación compatible requiere una release puente: primero publicar una versión que confíe en
ambas claves y que esté firmada con la clave antigua; solo después de que los clientes la hayan
instalado, cambiar el secret de Actions y firmar releases con la nueva clave. El verificador y el
workflow deben prepararse para ese conjunto de claves en la release puente. Si la clave antigua se
pierde o queda comprometida antes de publicar esa transición, los clientes existentes necesitarán
una actualización manual/reinstalación para confiar en otra clave.

No publiques ni incluyas claves privadas en commits, assets o logs de Actions.
