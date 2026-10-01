# Revisión de la última subida a GitHub — 30/09/2026

## Conclusión

**Todavía no está todo sincronizado ni libre de fallos.** La última rama compila y pasa sus pruebas de lógica web, pero contiene una web distinta de la publicada, un contrato web/Android incompatible en carga de expedientes y un APK de descarga anterior a los últimos cambios.

Esta revisión comprueba las versiones remotas exactas, exportadas a carpetas de prueba. No confunde las correcciones locales de la auditoría anterior con cambios ya subidos. No se modificó ni publicó código de producción durante esta verificación.

## Versiones revisadas

| Repositorio / rama | Commit confirmado con GitHub | Alcance |
|---|---|---|
| Android y web / `combinado` | `55a6aac77ac6907463bf54ad1d920743b12edb4d` | Última subida, 30/09 a las 16:57 de Lima. Incluye oficio y aviso a administradores. |
| Android / `main` | `74f8214596c986450b3678ceb7679fbe2276e285` | Compilación QA, JUnit y lint comprobados. |
| Web `moral-y-disciplina` / `main` | `f982c849d661e62b407f7d63f3cc7904edf3c065` | Versión que coincide con el JavaScript servido en la web pública. |

Referencias: [rama combinado](https://github.com/NUMBRSWORD/APLICACION-DE-MORAL-Y-DISCIPLINA-/tree/55a6aac77ac6907463bf54ad1d920743b12edb4d), [web main](https://github.com/NUMBRSWORD/moral-y-disciplina/tree/f982c849d661e62b407f7d63f3cc7904edf3c065), [publicación web correcta en GitHub Actions](https://github.com/NUMBRSWORD/moral-y-disciplina/actions/runs/36709276699).

## Hallazgos

### 1. La web combinada no satisface lo que espera la APK — prioridad alta

`app/src/main/assets/mobile_adapter.js` abre el módulo `expedientes-lote` usando `btnExpedientesLote`, `xlArchivo` y `modalExpedientesLote`. Los tres elementos faltan en el `index.html` de `combinado`. También faltan `lib/loteExpedientes.js` y `lib/remision.js`, presentes en la web `main` más reciente.

Se ejecutó el test de contrato existente apuntándolo a una copia de la propia web combinada: falla con `AssertionError: Elemento: btnExpedientesLote`. No es un fallo causado por falta de base de datos. Publicar esa web como sustituto de la actual rompería ese módulo del móvil.

La web actualmente publicada sí contiene esos controles. Por ello, este hallazgo no significa que la carga pública esté fallando hoy por esa causa: es una incompatibilidad de la rama combinada que debe resolverse antes de publicarla.

### 2. Subir `combinado` no actualiza la web usada por el móvil — prioridad alta

`WebActivity.java:72` sigue abriendo `https://numbrsword.github.io/moral-y-disciplina/`, es decir, el otro repositorio. Se compararon por HTTP `app.js` y `lib/oficioRemision.js`, normalizando solo saltos de línea: coinciden con `moral-y-disciplina/main`, no con `combinado`.

Hay dos implementaciones diferentes del oficio. Antes de integrar, hay que conservar las funciones recientes de la web y los nuevos avisos de Android, no reemplazar una versión por la otra sin resolver diferencias. El workflow de Pages incluido en `combinado` solo se dispara automáticamente al subir a `main`.

### 3. El APK incluido en GitHub está desactualizado — prioridad alta

`descargas/faltos-1.5.apk` procede del commit `5c5549d`, del 27/09. La inspección directa de sus recursos muestra «Subir expediente completo» y no contiene `aviso_documentos_por_recibir`. El código de `combinado` sí contiene «Subir expediente» y el aviso nuevo.

El APK y `descargas/version.json` siguen en versión 1.5 / código 6. Compilar el código nuevo no actualiza automáticamente esa descarga. Falta generar y verificar una distribución con la firma apropiada y publicar coordinadamente su versión y archivo.

SHA-256 del APK incluido: `966b01e3e3b922614fbedbcc69caa741c210dd7d3d7399c84ddf54e389f216da`.

### 4. La web publicada permite un oficio con firmante vacío — prioridad media

En `moral-y-disciplina/main`, `lib/oficioRemision.js:116`, `faltaParaElOficio` comprueba `superior` (quien impuso la sanción), pero no el nombre del comisario firmante. Con un sancionador válido y `comisario: {}`, la reproducción devuelve:

```json
{"firmante":"","faltantes":[]}
```

El formulario puede superar esa validación sin nombre en la firma. Las 375 pruebas de esa versión no detectan este caso. La implementación distinta de `combinado` sí valida `firma_nombre`; el defecto corresponde a la versión publicada, no a ambas.

### 5. Los avisos reales siguen pendientes de comprobación de extremo a extremo

`PENDIENTES-2026-09-30.md` afirma que se aplicaron las migraciones de recepción/entregas y se desplegó `avisos-android` versión 9. También documenta cero dispositivos registrados y ausencia de programación automática para llamar a esa función. Son datos declarados en el repositorio: esta revisión no accedió administrativamente a Supabase para confirmar su estado actual.

La lógica nueva de aviso al administrador pasa su prueba aislada. Eso no demuestra que llegue un aviso real a un teléfono. Faltan comprobar el registro del dispositivo, la programación, la entrega y la visualización con la aplicación abierta/cerrada.

### 6. `npm test` no cubre todo el repositorio combinado

Su comando solo ejecuta `lib/*.test.js`; excluye las pruebas de `tests/` de avisos, SQL y contrato Android/web. Al preparar las dependencias de prueba, los tres archivos SQL pasan y queda una incompatibilidad real de contrato con la web combinada. Conviene integrar esas comprobaciones en CI con preparación explícita de sus dependencias.

## Pruebas ejecutadas

| Comprobación | Resultado |
|---|---|
| `npm test`, web de `combinado` exacta | **282 correctas**, 0 fallidas. |
| `npm test`, web `moral-y-disciplina/main` exacta | **375 correctas**, 0 fallidas. Son otra versión; no se suman a las anteriores. |
| Lógica de avisos del servidor combinado | **8 correctas**, incluido aviso a administradores. |
| SQL aislado en PGlite | **22 comprobaciones correctas**: 6 entregas, 10 recepción/apelación, 6 seguridad. No consulta producción. |
| Contrato APK con la web de `combinado` | **Falla**, faltan controles de carga en lote. |
| Mismo contrato APK con la web publicada `main` | **Correcto**. Confirma que la incompatibilidad corresponde a la web combinada. |
| Compilación Android `combinado` | QA, APK de pruebas y compilación de release para lint correctos. |
| JUnit Android `combinado` | **8 correctas**, 0 errores. |
| Lint Android `combinado` | **No issues found**. |
| Instrumentación Android `combinado` | **57 correctas**, 0 fallidas, en emulador Android. Incluye modo oscuro, navegación, acceso, token inferior y mediciones de texto grande. |
| Android `main` exacto | Compilación QA, 8 JUnit y lint correctos. |
| Publicación web | Último workflow exitoso; archivos públicos examinados coinciden con web `main`. |

Las pruebas de pantallas Android usan datos ficticios y transporte controlado. No acreditan por sí mismas la sincronización entre dos cuentas reales, el OCR externo, cámara física, permisos efectivos en producción o entrega FCM. No se subieron expedientes reales ni se enviaron notificaciones a usuarios.

## Evidencia local y siguiente paso

Exportaciones y registros en `app/build/verify-latest-20260930/`: `combinado-web-tests.txt`, `combinado-extra-configurados.txt`, `combinado-android-build.txt`, `combinado-instrumentacion.txt` y `revisar-contrato.mjs`. Este último reproduce los controles ausentes y la validación defectuosa de la web publicada sin usar red ni credenciales.

Orden recomendado: reconciliar las dos versiones web preservando funciones → corregir y cubrir los fallos de contrato/oficio → verificar Supabase y avisos con cuentas de prueba → generar el APK actualizado con firma de distribución → publicar y probar la actualización instalada.

**No desinstalar a ciegas la aplicación real para cambiar de firma.** Primero comprobar recuperación de acceso y respaldo del token local; esta auditoría usa un paquete QA separado en un emulador temporal.
