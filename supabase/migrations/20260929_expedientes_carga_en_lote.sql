-- Prepara public.expedientes para la carga en lote de expedientes firmados.
-- Aplicado el 29/09/2026 con la tabla vacia, asi que ningun dato existente se
-- vio afectado.

-- 1. El oficio y la hoja de tramite eran obligatorios, pero los expedientes
-- firmados reales no siempre los traen: el fajo que llega por mesa de partes si
-- tiene Hoja de Tramite, y el expediente suelto empieza directamente por el
-- inicio de imputacion. Obligarlos forzaba a inventar un valor.
alter table public.expedientes alter column numero_oficio drop not null;
alter table public.expedientes alter column numero_ht drop not null;

-- 2. Un caso tiene un solo expediente firmado. Sin esta restriccion, repetir la
-- carga en lote creaba un segundo expediente en cada caso sin avisar. Con ella,
-- volver a subir es reemplazar, y eso se decide a la vista.
create unique index if not exists expedientes_nota_id_unico
  on public.expedientes (nota_id);

-- 3. Faltaba la politica de UPDATE. El formulario manual insertaba la fila,
-- subia el PDF y despues lo enlazaba con un update: sin politica, RLS lo
-- descartaba en silencio y el archivo quedaba en el deposito sin pertenecer a
-- ningun expediente.
drop policy if exists "solo admin actualiza expedientes" on public.expedientes;
create policy "solo admin actualiza expedientes"
  on public.expedientes for update to authenticated
  using (public.es_admin()) with check (public.es_admin());
