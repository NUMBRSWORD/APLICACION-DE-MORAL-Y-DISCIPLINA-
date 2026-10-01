-- Eliminacion de cuenta a pedido de la persona. Google Play exige, en toda app que
-- permite crear una cuenta, una via dentro de la app y otra publica en la web para
-- pedir la eliminacion. Aqui queda el registro verificable de cada pedido.

create table if not exists public.solicitudes_eliminacion (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  email text,
  motivo text,
  estado text not null default 'pendiente'
    check (estado in ('pendiente', 'atendida', 'rechazada')),
  creado_at timestamptz not null default now(),
  atendida_at timestamptz,
  atendida_por uuid references auth.users(id)
);

create unique index if not exists solicitudes_eliminacion_una_pendiente
  on public.solicitudes_eliminacion (user_id) where estado = 'pendiente';

alter table public.solicitudes_eliminacion enable row level security;

-- Cada persona ve solo lo suyo; el administrador ve y resuelve todas.
drop policy if exists "ve su propia solicitud de eliminacion" on public.solicitudes_eliminacion;
create policy "ve su propia solicitud de eliminacion" on public.solicitudes_eliminacion
  for select to authenticated
  using (user_id = (select auth.uid()) or public.es_admin());

drop policy if exists "el administrador resuelve las solicitudes" on public.solicitudes_eliminacion;
create policy "el administrador resuelve las solicitudes" on public.solicitudes_eliminacion
  for update to authenticated
  using (public.es_admin()) with check (public.es_admin());

-- La alta se hace solo por la funcion de abajo, nunca con un insert directo.
revoke all on public.solicitudes_eliminacion from anon, authenticated;
grant select on public.solicitudes_eliminacion to authenticated;
grant update on public.solicitudes_eliminacion to authenticated;

-- Registra el pedido de quien ha iniciado sesion. Devuelve 'ok' o 'ya_pendiente'.
create or replace function public.solicitar_eliminacion_cuenta(p_motivo text default null)
returns text
language plpgsql
security definer
set search_path = public, auth
as $$
declare
  v_usuario uuid := auth.uid();
  v_email text;
begin
  if v_usuario is null then
    return 'sin_sesion';
  end if;
  if exists (select 1 from public.solicitudes_eliminacion
             where user_id = v_usuario and estado = 'pendiente') then
    return 'ya_pendiente';
  end if;
  select u.email into v_email from auth.users u where u.id = v_usuario;
  insert into public.solicitudes_eliminacion (user_id, email, motivo)
  values (v_usuario, v_email, nullif(btrim(coalesce(p_motivo, '')), ''));
  return 'ok';
end;
$$;

revoke all on function public.solicitar_eliminacion_cuenta(text) from public, anon;
grant execute on function public.solicitar_eliminacion_cuenta(text) to authenticated;

-- Permite a la app saber si ya hay un pedido en curso.
create or replace function public.tiene_eliminacion_pendiente()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (select 1 from public.solicitudes_eliminacion
                 where user_id = auth.uid() and estado = 'pendiente');
$$;

revoke all on function public.tiene_eliminacion_pendiente() from public, anon;
grant execute on function public.tiene_eliminacion_pendiente() to authenticated;
