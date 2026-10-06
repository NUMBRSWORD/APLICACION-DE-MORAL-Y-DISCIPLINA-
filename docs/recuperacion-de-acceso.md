# Recuperación de acceso y segundo administrador

`moral-y-disciplina` — Supabase proyecto `tndjulaitywtoocqeeiy`.

Hay **dos administradores** (comprobado el 06/10/2026, ambos con token). Mantener
siempre al menos dos: si solo queda uno y se bloquea, **nadie más puede
administrar** (crear notas, generar documentos, gestionar efectivos, ver
Recepción).

## Cómo funciona el acceso

- Los usuarios se crean en **Supabase → Authentication → Users**.
- Cada usuario tiene una fila en la tabla `public.profiles` con `role` =
  `'admin'` o `'viewer'`. Solo `role='admin'` habilita todo.
- Los oficiales inician sesión con su **CIP** (el sistema le agrega
  `@moralydisciplina.local` por dentro); la clave la fija el administrador.

## Crear una cuenta para firmar documentos (Comisario u otro mando, sin ser admin)

Para firmar la política de datos o la política de IA (pestaña **Cumplimiento**)
no hace falta ser administrador — cualquier cuenta que pueda iniciar sesión
sirve. Es más corto que crear un admin: solo el paso 1 de abajo
(**Authentication → Users → Add user**, con *Auto Confirm User* marcado). La
fila en `profiles` se crea sola con `role = 'viewer'`, que ya alcanza para
entrar y firmar — no hace falta el paso 2.

## Crear un segundo administrador

1. **Supabase → Authentication → Users → Add user**
   - Email: `<CIP>@moralydisciplina.local` (ej. `12345678@moralydisciplina.local`).
   - Password: una clave temporal; entregarla a la persona para que la cambie.
   - Marcar *Auto Confirm User*.
2. **Supabase → Table Editor → `profiles`**: buscar la fila de ese usuario
   (se crea sola al registrarse) y poner `role = 'admin'`.
   - Si no aparece la fila, crearla: `id` = el UUID del usuario de Auth,
     `role` = `'admin'`.
3. Verificar: esa persona entra con su CIP y ve las pestañas de admin
   (Efectivos, Recepción, Panel, Historial, botón "+ Nueva nota").

> Recomendado: 2 admins fijos (jefe de la unidad + su suplente), y revisar la
> lista cada vez que hay cambio de destino.

## Un usuario olvidó su clave

Las cuentas son `<CIP>@moralydisciplina.local`: no tienen un correo real, así que
el *Reset password* de Supabase (que manda un enlace por correo) **no sirve**.

1. Un administrador entra a la web con su token y pulsa **Restablecer clave**
   (barra superior).
2. Escribe el CIP del usuario y pulsa **Generar clave temporal**.
3. Entrega en persona la clave que aparece (tipo `ABCD-EF23`). No se vuelve a
   mostrar.
4. El usuario entra con su CIP y esa clave; la app le exige elegir una nueva
   antes de ver nada. Si tenía token, lo sigue necesitando.

Queda registrado en **Historial** (quién y a qué CIP, nunca la clave). Se cierran
las sesiones abiertas de ese usuario. Función: `restablecer_clave_usuario`
(migración `20261006120000_restablecer_clave_por_admin`).

## Recuperar acceso de un admin bloqueado

El botón no restablece la clave de otro administrador (para que ninguno pueda
tomar la cuenta del otro). Se hace desde el **SQL Editor** de Supabase con una
clave temporal; al entrar se le exigirá cambiarla:

```sql
with u as (
  update auth.users
     set encrypted_password = extensions.crypt('CLAVE-TEMPORAL', extensions.gen_salt('bf', 10)),
         updated_at = now()
   where email = '<CIP>@moralydisciplina.local'
  returning id, encrypted_password
)
insert into public.cambios_clave_pendientes (user_id, hash_al_marcar)
select id, encrypted_password from u
on conflict (user_id) do update set hash_al_marcar = excluded.hash_al_marcar, marcado_at = now();
```

- **No hay ningún admin disponible:** entrar al **panel de Supabase** con la
  cuenta dueña del proyecto (`hanshidalgo98@gmail.com`) y:
  1. En *Authentication → Users*, resetear la clave del usuario, o crear uno
     nuevo como en la sección anterior.
  2. En *Table Editor → profiles*, poner `role='admin'` a ese usuario.
- **Se perdió también la cuenta de Supabase:** la recuperación es por el correo
  de esa cuenta de Google. Mantener ese correo con doble factor y datos de
  recuperación al día. Considerar agregar a un segundo miembro como
  *Owner/Administrator* de la organización en **Supabase → Organization →
  Team**.

  **Activar verificación en dos pasos en `hanshidalgo98@gmail.com`:**
  1. `myaccount.google.com` → **Seguridad** → **Verificación en 2 pasos** → Activar.
  2. Confirmar la clave actual, luego agregar el número de celular (código por SMS)
     o, mejor, una app autenticadora (Google Authenticator / similar).
  3. **Importante — guardar los códigos de respaldo (backup codes)** que Google
     ofrece al terminar, en un lugar aparte del celular (impresos, o en el JSON de
     respaldo manual). Si el celular se pierde o se daña sin esos códigos, el 2FA
     puede terminar bloqueando el acceso en vez de protegerlo — justo el escenario
     que se quiere evitar.

## Respaldo de datos (por si hay que reconstruir)

- **Automático:** Supabase guarda backups del proyecto.
- **Manual:** desde la app, *Ajustes → Descargar respaldo* genera un `.json` con
  todo. Guardar una copia mensual fuera de línea.
- **Documentos firmados:** copia en Google Drive de la cuenta institucional
  (pestaña Recepción → "Conectar Drive").
