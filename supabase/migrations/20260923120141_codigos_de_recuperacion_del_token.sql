-- Recuperacion del Token Digital cuando se pierde el telefono.
-- Antes solo un administrador podia borrar el factor (desactivar_token), asi que quien
-- perdia el equipo quedaba bloqueado, y un administrador que perdiera el suyo no tenia
-- salida dentro de la aplicacion. Estos codigos de un solo uso, entregados al activar el
-- token, permiten a la persona recuperarlo por si misma.

create table if not exists public.codigos_recuperacion_token (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references auth.users(id) on delete cascade,
  hash text not null,
  creado_at timestamptz not null default now(),
  usado_at timestamptz
);
create index if not exists codigos_recuperacion_token_usuario
  on public.codigos_recuperacion_token (user_id) where usado_at is null;

create table if not exists public.intentos_recuperacion_token (
  id bigserial primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  creado_at timestamptz not null default now()
);
create index if not exists intentos_recuperacion_token_usuario
  on public.intentos_recuperacion_token (user_id, creado_at desc);

alter table public.codigos_recuperacion_token enable row level security;
alter table public.intentos_recuperacion_token enable row level security;

-- Nadie lee estas tablas con la clave publica: solo las funciones de abajo, que
-- se ejecutan con privilegios propios. Sin politicas, la RLS lo niega todo.
revoke all on public.codigos_recuperacion_token from anon, authenticated;
revoke all on public.intentos_recuperacion_token from anon, authenticated;

-- Entrega 8 codigos nuevos y anula los anteriores. El texto solo se devuelve aqui:
-- en la base queda unicamente el hash.
create or replace function public.generar_codigos_recuperacion()
returns text[]
language plpgsql
security definer
set search_path = public, extensions
as $$
declare
  v_usuario uuid := auth.uid();
  v_alfabeto text := 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'; -- sin I, O, 0 ni 1
  v_codigos text[] := array[]::text[];
  v_codigo text;
  v_bytes bytea;
  i int;
  j int;
begin
  if v_usuario is null then
    raise exception 'Debe iniciar sesion';
  end if;
  delete from public.codigos_recuperacion_token where user_id = v_usuario and usado_at is null;
  for i in 1..8 loop
    v_codigo := '';
    v_bytes := extensions.gen_random_bytes(10);
    for j in 0..9 loop
      v_codigo := v_codigo || substr(v_alfabeto, 1 + (get_byte(v_bytes, j) % 32), 1);
    end loop;
    insert into public.codigos_recuperacion_token (user_id, hash)
    values (v_usuario, extensions.crypt(v_codigo, extensions.gen_salt('bf')));
    v_codigos := array_append(v_codigos, substr(v_codigo, 1, 5) || '-' || substr(v_codigo, 6, 5));
  end loop;
  return v_codigos;
end;
$$;

-- Devuelve 'ok' (y deja el token borrado para volver a activarlo), 'invalido',
-- 'bloqueado' tras 10 intentos fallidos en una hora, o 'sin_sesion'.
create or replace function public.usar_codigo_recuperacion(p_codigo text)
returns text
language plpgsql
security definer
set search_path = public, extensions, auth
as $$
declare
  v_usuario uuid := auth.uid();
  v_limpio text;
  v_id uuid;
begin
  if v_usuario is null then
    return 'sin_sesion';
  end if;
  if (select count(*) from public.intentos_recuperacion_token
        where user_id = v_usuario and creado_at > now() - interval '1 hour') >= 10 then
    return 'bloqueado';
  end if;
  v_limpio := upper(regexp_replace(coalesce(p_codigo, ''), '[^A-Za-z0-9]', '', 'g'));
  select c.id into v_id
    from public.codigos_recuperacion_token c
   where c.user_id = v_usuario and c.usado_at is null
     and c.hash = extensions.crypt(v_limpio, c.hash)
   limit 1;
  if v_id is null then
    insert into public.intentos_recuperacion_token (user_id) values (v_usuario);
    return 'invalido';
  end if;
  update public.codigos_recuperacion_token set usado_at = now() where id = v_id;
  delete from auth.mfa_factors where user_id = v_usuario;
  delete from public.intentos_recuperacion_token where user_id = v_usuario;
  return 'ok';
end;
$$;

create or replace function public.codigos_recuperacion_disponibles()
returns integer
language sql
security definer
set search_path = public
stable
as $$
  select count(*)::int from public.codigos_recuperacion_token
   where user_id = auth.uid() and usado_at is null;
$$;

revoke all on function public.generar_codigos_recuperacion() from public, anon;
revoke all on function public.usar_codigo_recuperacion(text) from public, anon;
revoke all on function public.codigos_recuperacion_disponibles() from public, anon;
grant execute on function public.generar_codigos_recuperacion() to authenticated;
grant execute on function public.usar_codigo_recuperacion(text) to authenticated;
grant execute on function public.codigos_recuperacion_disponibles() to authenticated;
