-- Registro de dispositivos para los avisos nativos (FCM), desde
-- supabase/notificaciones-android.sql del repositorio de la aplicacion.
--
-- Nota importante: ese archivo exige public.tiene_mfa_verificada(), que vive en
-- seguridad-mfa-aal2.sql. Ese archivo NO se aplica entero a proposito, porque
-- redefine esta_aprobado() para exigir token y dejaria fuera a las cuentas que
-- todavia no lo activaron. Aqui se crea unicamente la funcion auxiliar, que solo
-- lee una marca del token de sesion y no cambia ninguna politica existente.

create or replace function public.tiene_mfa_verificada()
returns boolean
language sql
stable
set search_path to 'public'
as $$
  select coalesce(auth.jwt() ->> 'aal', 'aal1') = 'aal2';
$$;

revoke execute on function public.tiene_mfa_verificada() from public, anon;
grant execute on function public.tiene_mfa_verificada() to authenticated;

do $$ begin
  if to_regprocedure('public.esta_aprobado()') is null
     or to_regprocedure('public.tiene_mfa_verificada()') is null then
    raise exception 'Aplicar primero las migraciones de aprobación y seguridad MFA.';
  end if;
end $$;

create table if not exists public.dispositivos_android (
  token text primary key check (length(token) between 32 and 4096),
  user_id uuid not null references auth.users(id) on delete cascade,
  registrado_at timestamptz not null default now(),
  actualizado_at timestamptz not null default now()
);
create index if not exists dispositivos_android_usuario_idx
  on public.dispositivos_android(user_id);
alter table public.dispositivos_android enable row level security;
revoke all on public.dispositivos_android from public, anon, authenticated;

-- El teléfono no puede enumerar tokens ajenos ni escribir la tabla directamente.
-- Al cambiar de cuenta en el mismo dispositivo, el token se reasigna solo tras
-- verificar que la cuenta nueva está aprobada y pasó MFA.
create or replace function public.registrar_dispositivo_android(p_token text)
returns void language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is null or not coalesce(public.esta_aprobado(), false)
     or not coalesce(public.tiene_mfa_verificada(), false) then
    raise exception 'Cuenta o token digital sin verificar.' using errcode = '42501';
  end if;
  if p_token is null or length(p_token) not between 32 and 4096
     or p_token ~ '[[:space:]]' then
    raise exception 'Token de dispositivo inválido.' using errcode = '22023';
  end if;
  insert into public.dispositivos_android(token, user_id)
    values (p_token, auth.uid())
  on conflict(token) do update
    set user_id = excluded.user_id, actualizado_at = now();
end $$;
revoke all on function public.registrar_dispositivo_android(text) from public, anon;
grant execute on function public.registrar_dispositivo_android(text) to authenticated;

create or replace function public.quitar_dispositivo_android(p_token text)
returns void language plpgsql security definer set search_path = ''
as $$
begin
  if auth.uid() is null then return; end if;
  delete from public.dispositivos_android
    where token = p_token and user_id = auth.uid();
end $$;
revoke all on function public.quitar_dispositivo_android(text) from public, anon;
grant execute on function public.quitar_dispositivo_android(text) to authenticated;

notify pgrst, 'reload schema';
