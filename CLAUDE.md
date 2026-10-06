# Faltos — guía para trabajar en este repositorio

Herramienta independiente para expedientes disciplinarios (infracciones Leves,
Ley N.° 30714). Este repositorio reúne la **web** (raíz), la **app Android**
(`app/`) y el **backend Supabase** (`supabase/`). Se habla y se escribe en
español; el responsable no es programador: explicar en lenguaje sencillo.

## Reglas que no se rompen

- **El repositorio es público.** Nunca subir nombres, CIP, DNI, teléfonos de
  personal real, claves, secretos, `google-services.json` ni datos de
  `oficio/datos.json`. En pruebas y ejemplos, solo personas inventadas.
- **Fechas en hora de Lima** (`lib/fechas.js`, `hoyLima()`), nunca `current_date`
  ni el día UTC: entre las 19:00 y las 24:00 de Lima difieren.
- **Supabase real:** leer está bien; cualquier cambio de estructura o borrado se
  confirma antes con el responsable y va como migración en `supabase/migrations/`
  con su script de reversión. El conector MCP no puede aplicar DDL ni borrados
  (se queda esperando una confirmación): se le da el SQL para el SQL Editor.
- **La web se usa también dentro de la app Android** (WebView). Lo que sea solo
  para el navegador se protege con `EN_APP_ANDROID` (`window.__faltosConfig`).
  El contrato con Android está en `app/src/main/assets/mobile_*.js`.
- No desactivar pruebas para que pasen. No reemplazar la clave de firma del APK.

## Comandos

```bash
npm install --ignore-scripts   # base PostgreSQL aislada (PGlite) para pruebas
npm test                       # sintaxis de todo el JS + todas las pruebas
node --test lib/archivo.test.js            # una prueba de lógica
node tests/archivo-db.test.mjs             # una prueba de base de datos
npm run sync:web -- app/build/upstream-github   # copiar la web al repo publicado
gradlew assembleDebug assembleQa testQaUnitTest lintDebug connectedQaAndroidTest  # Android (requiere SDK)
```

La web publicada vive en el repositorio `NUMBRSWORD/moral-y-disciplina`
(GitHub Pages: `numbrsword.github.io/moral-y-disciplina`); se actualiza con
`sync:web` y un push allí. Al cambiar `app.js`/`index.html`/`styles.css`, subir la
versión de caché en `sw.js`.

## Estructura

- `app.js`, `index.html`, `styles.css`: la web (una sola página).
- `lib/*.js`: lógica pura y probada (plazos, documentos, remisión, archivo…).
  Lo nuevo va aquí con su `*.test.js`, no directo en `app.js`.
- `plantillas/*.docx`: plantillas de documentos (docxtemplater, `{campo}`).
- `tests/*-db.test.mjs`: migraciones probadas en PGlite (roles, RLS, permisos).
- `app/`: Android (Java). Variante `qa` para pruebas (`com.hidalgoferrai.faltos.qa`).
- `supabase/`: SQL, migraciones y Edge Functions.
- `descargas/`: APK publicado y `version.json` (la app avisa de versiones nuevas).

## Ciclo de un caso

Nota informativa → imputación → descargo (o acta de no descargo) → orden de
sanción → notificación → **subir expediente firmado** → **oficio de remisión**
(su número se guarda en el caso) → **Hoja de Trámite** en Recepción →
**Archivo** (folder manila rotulado con el N.º de oficio, legajo PDF único).
Archivados sin sanción usan su N.º de Resolución.

## Seguridad y acceso

- Acceso: cuenta aprobada por un administrador + políticas firmadas + Token
  Digital (TOTP en una app de códigos, también en la web con QR).
- `es_admin()` / `esta_aprobado()` aún **no** exigen AAL2: se aplicará
  `supabase/seguridad-mfa-aal2.sql` cuando todas las cuentas tengan token.
- Avisos Android: Edge Function `avisos-android` + cron 08:05 (clave en Vault).
