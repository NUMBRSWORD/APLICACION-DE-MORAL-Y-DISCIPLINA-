# Auditoría 2026-10-01 (solo lectura sobre Supabase; no se modificó nada en producción)

## Verificado y correcto
- Migraciones `avisos_android_entregas` y `recepcion_fisica_y_apelacion` aplicadas; función `avisos-android` desplegada (v9) con cron diario 08:05 Lima activo.
- RLS activo en las 25 tablas de `public`; políticas por rol/CIP coherentes. Tablas de recepción/apelación y su almacenamiento sí exigen aprobado + MFA.
- Secretos de FCM/servicio solo en variables de entorno; `avisos-android` exige cabecera secreta. No hay claves privadas en el repositorio (la clave `anon` es pública por diseño).
- App: WebView limita la navegación a `numbrsword.github.io/moral-y-disciplina/`, sin acceso a archivos ni contenido mixto; token y recuperación con `FLAG_SECURE`; sin `Log`; `allowBackup=false`; actividades internas no exportadas.
- Pruebas locales: avisos 7/7, entregas 6, MFA 6, recepción 10 (tras corregir la prueba de fecha UTC vs Lima).

## Hallazgos
1. **ALTO – MFA no se exige en el servidor.** En producción `es_admin()` y `esta_aprobado()` no comprueban `aal2` (`seguridad-mfa-aal2.sql` no está aplicada). Solo 1 de 13 cuentas (2 admin, 11 viewer) tiene token verificado: aplicarla hoy bloquearía a las demás. Plan: que cada cuenta active su token y luego aplicar el script.
2. **MEDIO – Permisos excesivos en tablas.** `anon` y `authenticated` tienen todos los privilegios (incl. TRUNCATE) sobre ~16 tablas; solo RLS protege. Recomendado `revoke` de lo que no se usa, empezando por `anon`.
3. **MEDIO – Protección de contraseñas filtradas desactivada** en Supabase Auth.
4. **BAJO** – `pg_net` en esquema `public`; `imputacion_pnp.set_updated_at` sin `search_path`; `imputacion_pnp.es_admin()`/`handle_new_user()` ejecutables por `anon`; comparación del secreto cron con `!==` (no constante); 9 tablas con RLS sin políticas (intencional, solo funciones SECURITY DEFINER).
5. **INFO** – `dispositivos_android` y `entregas_android` tienen 0 filas: ningún teléfono ha registrado avisos aún. Falta `app/google-services.json` en builds locales.

## No verificado
- Compilación/lint/pruebas Android (sin SDK; descarga bloqueada). Cámara, escáner, avisos en teléfono real. Cumplimiento legal de retención de datos.

## Actualización (misma fecha, sobre `main` con Faltos 1.6)
- `npm test`: **405/405** pruebas correctas (web, lógica, PostgreSQL aislado, avisos, contrato).
- Primer teléfono registrado en `dispositivos_android` (1). Tokens verificados: 2 de 13 cuentas.
- Cron `avisos-android-0805` activo; la clave del cron fue rotada (commit `b9b10fb`).
- Web: los datos dinámicos que se insertan con `innerHTML` pasan por `escapeHtml`; no se encontró inyección. No hay Content-Security-Policy.
- Rendimiento: sin problemas al volumen actual (175 notas); solo avisos informativos de índices.
- Sigue pendiente el hallazgo ALTO (MFA en servidor): aplicarlo cuando las 13 cuentas tengan token.
