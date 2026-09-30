# Estado al 30/09/2026 y qué hacer en la otra computadora

Rama: `combinado` (une la app Android con el repositorio web `moral-y-disciplina`).

## Ya hecho

**En el código (esta rama)**
- Oficio de remisión en la web: `lib/oficioRemision.js`, plantilla `plantillas/plantilla_oficio_remision.docx`,
  tarjeta «Oficio de remisión» en el detalle del caso (aparece tras subir el expediente).
  Jefe de la DIVOPUS y Comisario se confirman en pantalla («continúan / cambiaron») y se guardan
  en el navegador. **No hay nombres ni CIP reales en el código** (el repositorio es público).
- Android: el botón del inicio dice «Subir expediente» (antes «Subir expediente completo»).
- Aviso Android nuevo para administradores: `documentos_por_recibir`
  («Tiene documentos por recibir. Ingrese a Recepcionar documentos.»).

**En Supabase (proyecto «MORAL Y DISCIPLINA», aplicado el 30/09/2026)**
- Migración `avisos_android_entregas`: tabla `entregas_android` + `reservar_entrega_android`.
- Migración `recepcion_fisica_y_apelacion`: `recepciones_fisicas`, `apelaciones_expediente`,
  `confirmar_recepcion_fisica`, `presentar_apelacion_expediente`, bucket `apelaciones-expediente`.
- Función `avisos-android` versión 9 (la de este repositorio), con `verify_jwt` apagado
  (se protege con `AVISOS_CRON_SECRET`).
- Comprobado después: 3 tablas, 3 funciones y el bucket existen.

## Qué hacer ahora (en la computadora que tiene Android Studio y `google-services.json`)

1. `git fetch` y `git checkout combinado` (o clonar el repositorio y cambiar a esa rama).
2. Copiar `google-services.json` a `app/` (está ignorado por git a propósito; sin él la app no
   muestra la tarjeta de avisos).
3. Compilar: Android Studio → Build → Generate App Bundles or APKs → Generate APKs
   (o `gradlew assembleDebug`). El APK queda en `app/build/outputs/apk/debug/`.
4. Desinstalar la app anterior del teléfono (la firma de depuración es distinta) e instalar el APK.
5. Entrar con cuenta y token; en el inicio pulsar **Activar** en la tarjeta de avisos y aceptar
   el permiso de notificaciones.
6. Comprobar que el teléfono quedó registrado: en Supabase, tabla `dispositivos_android` debe
   tener una fila (hoy tiene 0, por eso nadie recibe avisos todavía).

## Pendientes que requieren decisión

- **Programación diaria de los avisos:** no hay ninguna tarea programada que llame a `avisos-android`.
  Hay que crearla (con el secreto `AVISOS_CRON_SECRET`, que no está en el repositorio).
  Sin ella los avisos no salen solos.
- **Publicar la web:** la página que usan los usuarios es `numbrsword.github.io/moral-y-disciplina`
  (otro repositorio). El oficio de remisión aparecerá ahí solo cuando esta rama se integre a ese
  repositorio. Esta rama vive en `APLICACION-DE-MORAL-Y-DISCIPLINA-`.
- **Pull request** de `combinado` a `main` en `APLICACION-DE-MORAL-Y-DISCIPLINA-`, si se quiere
  dejarla como principal.
- Cuatro pruebas de `tests/` (`*-db.test.mjs` y el contrato de GitHub) fallan en una computadora
  sin la base de datos de prueba local; ya fallaban antes de estos cambios.

## Datos que NO están en el repositorio (por ser público)

Nombre, grado, CIP y OA del Jefe de la DIVOPUS y del Comisario: se escriben una vez en la tarjeta
del oficio y quedan en el navegador. La copia local de los datos está en `oficio/datos.json`
(ignorado por git).
