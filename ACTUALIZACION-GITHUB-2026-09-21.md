# Integración Android 1.2 con el repositorio web

## Fuente y alcance

Se revisó `NUMBRSWORD/moral-y-disciplina`, rama `main`, en el commit
[`2a68969`](https://github.com/NUMBRSWORD/moral-y-disciplina/commit/2a689697637504587e45ba23a6ec00bd629926c3).
La copia Android partía del commit local `ff31acc` (versión 1.1), sin remoto
configurado. No son el mismo proyecto: no se ha sustituido el código Android
por el sitio ni se ha configurado un remoto incorrecto. Se conserva la limpieza
del prototipo que ya estaba realizada.

Los cambios de esta entrega son locales; no se ha hecho push, publicado la web,
ejecutado SQL en producción ni aceptado políticas o modificado expedientes reales.
La app usa el sitio publicado y añade su integración móvil; el commit anterior
identifica la versión auditada, no fija las futuras actualizaciones del sitio.

## Novedades integradas

- Historial por efectivo separado de Seguimiento (pendientes). Ya no se cambia
  el título del nuevo seguimiento web a «Expedientes concluidos».
- Panel mensual administrativo, esperando la carga inicial de expedientes para
  evitar un falso panel vacío. Conserva los cálculos del repositorio web; no se
  han inventado remuneraciones ni modificado reglas de sanción.
- Más herramientas: reincorporación grupal, continúan faltos, roles de servicio,
  directivas, agenda, documentos generados e historial de actividad.
  Seleccionar una herramienta no envía formularios ni registra documentos.
- Se conservan carga por escáner/PDF, recepción física con conformidad expresa,
  consulta del cargo y apelación opcional de la entrega anterior.
- Compatibilidad con cambio obligatorio de clave: el servidor determina el
  bloqueo, se muestra el formulario protegido y se vuelve al flujo de políticas
  antes del token nativo. Una consulta de seguridad fallida no permite avanzar.
- Los módulos recuperan el destino después de completar clave o token, sin
  temporizador mientras la persona escribe. Las pantallas de seguridad impiden
  capturas. La renovación de sesión no devuelve al inicio.
- Las cuentas Google consultan su CIP registrado mediante `cip_actual`; no se
  utiliza el texto del correo como CIP. Un error no se convierte en otro usuario.
- Avances nativos con fechas en America/Lima, distinguiendo fechas civiles de
  timestamps. El formato no depende de la zona configurada en el teléfono.
- Tarjetas de herramientas con colores claro/oscuro; los gráficos del Panel se
  vuelven a dibujar al cambiar el tema. Token Digital permanece abajo en Inicio.
- Aviso de privacidad sincronizado con el texto en revisión del repositorio.
  La versión de aceptación combinada pasa a 2 por el nuevo aviso; los términos
  de uso existentes conservan su texto y versión 1. Las firmas se describen como
  constancias internas, no como firmas digitales certificadas.

## Backend: comprobar antes de distribuir

La app necesita las RPC `necesita_cambiar_clave`, `confirmar_cambio_clave` y
`cip_actual` del servidor actualizado. Debe verificarse el despliegue de la
migración web `20260921180000_cambio_clave_pendiente_en_servidor.sql`.
Si una función no existe o falla, la comprobación no se omite silenciosamente.

Esa migración web redefine `esta_aprobado`, `es_admin` y `cip_actual`. Aplicar
después la versión actualizada de `supabase/seguridad-mfa-aal2.sql` conserva
simultáneamente el bloqueo de clave y el requisito AAL2 para datos operativos.
También conserva la lectura/firma propia de políticas antes del token. No aplicar
después un SQL antiguo que vuelva a sobrescribir esas funciones.

La recepción y apelación necesitan su migración aditiva independiente:
`supabase/recepcion-fisica-y-apelacion.sql`. Ver `RECEPCION-Y-APELACION.md`.
El estado de producción no se da por validado con las pruebas locales. Las
Edge Functions con service_role deben verificar sus permisos explícitamente.

El aviso de privacidad sigue marcado como texto institucional en revisión;
sus campos pendientes y sus afirmaciones sobre conservación/proveedores deben
ser revisados por el responsable. Sin firma del usuario no se da por aceptado.

## Comprobaciones reproducibles

Resultado final: 32 pruebas instrumentadas, 7 unitarias (4 fechas y 3 TOTP),
6 pruebas PostgreSQL de compatibilidad de seguridad y 10 de recepción/apelación
aprobadas. Compilación Debug/QA correcta. Lint: 0 errores y 58 advertencias,
principalmente recursos sin usar; no se presenta como una compilación sin avisos.
El contrato estático de la integración con el checkout web también pasó.
La versión 1.2 (versionCode 3) quedó instalada con actualización conservando datos
y abierta en el emulador. Se retiraron únicamente los paquetes QA y sus datos
ficticios. El APK de desarrollo queda en `app/build/outputs/apk/debug/app-debug.apk`.

```powershell
.\gradlew.bat assembleDebug assembleQa assembleQaAndroidTest testQaUnitTest lintDebug
node tests/github-contract.test.mjs
npm install --prefix app/build/sql-qa --no-save --package-lock=false --ignore-scripts @electric-sql/pglite@0.5.8
node tests/seguridad-db.test.mjs
node tests/recepcion-db.test.mjs
```

El contrato necesita el checkout web auditado en `app/build/upstream-github`.
Las pruebas instrumentadas se ejecutan únicamente sobre el paquete `.qa`, con
sesiones, transportes y documentos ficticios. No sustituyen la prueba final con
una cuenta autorizada, las RPC desplegadas y un escáner físico.

Se probaron las funciones de fecha y TOTP, las pantallas independientes, los
controles de clave/token, el CIP de Google, la carga previa del Panel, los fallos
de red y el contraste oscuro. PostgreSQL en memoria valida MFA y cambio de clave
juntos, así como los permisos de recepción, apelación y sus archivos privados.

La suite web original dio 248/249 pruebas correctas y 31 comprobaciones de
sintaxis correctas. El fallo restante está en el dato de prueba
`lib/informeAdministrativo.test.js`: `fechaHace()` toma el día UTC, mientras el
código actualizado usa Lima; después de las 19:00 de Lima queda un día de
diferencia. No se ha alterado el cómputo correcto de la aplicación para hacer
pasar ese test. El parche `upstream-patches/fecha-lima-prueba.patch` corrige solo
ese dato de prueba; aplicado a la copia local auditada, pasan las 249 pruebas
web y las 31 comprobaciones de sintaxis. No se ha publicado en el repositorio
remoto.
