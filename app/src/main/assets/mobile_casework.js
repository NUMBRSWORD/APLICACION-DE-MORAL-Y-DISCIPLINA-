// Extensión del módulo web: envío digital, recepción física y apelación son hechos distintos.
// Los permisos y la fecha de recepción se validan en Supabase, nunca en el reloj del móvil.
;(() => {
  'use strict';
  if (!window.__faltosConfig?.view || !window.AndroidNavegacion) return;
  const $id = id => document.getElementById(id);
  const esc = value => String(value ?? '').replace(/[&<>"']/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
  const schemaError = error => ['42P01', '42703', 'PGRST200', 'PGRST202', 'PGRST205'].includes(error?.code);
  const failure = error => schemaError(error)
    ? 'Esta función requiere activar la actualización de recepción y apelación en Supabase. No se ha confirmado ninguna operación.'
    : 'No se pudo verificar la operación. Compruebe su conexión y actualice antes de reintentar.';
  const date = value => {
    if (!value) return 'sin fecha registrada';
    const parsed = new Date(/^\d{4}-\d{2}-\d{2}$/.test(value) ? value + 'T12:00:00Z' : value);
    return isNaN(parsed.getTime()) ? 'fecha por verificar' : new Intl.DateTimeFormat('es-PE', {
      timeZone:'America/Lima', day:'2-digit', month:'long', year:'numeric'
    }).format(parsed);
  };
  const dateISO = value => {
    if (!value) return '';
    if (/^\d{4}-\d{2}-\d{2}$/.test(value)) return value;
    const parsed = new Date(value);
    if (isNaN(parsed.getTime())) return '';
    const parts = new Intl.DateTimeFormat('en-CA', {timeZone:'America/Lima', year:'numeric', month:'2-digit', day:'2-digit'}).formatToParts(parsed);
    const part = type => parts.find(p => p.type === type).value;
    return `${part('year')}-${part('month')}-${part('day')}`;
  };
  const today = () => dateISO(new Date().toISOString());
  async function request(query) {
    let timer;
    try {
      const result = await Promise.race([query, new Promise((_, reject) => {
        timer = setTimeout(() => reject(new Error('timeout')), 20000);
      })]);
      if (result.error) throw result.error;
      return result.data;
    } finally { clearTimeout(timer); }
  }
  async function metadata(table, ids) {
    if (!ids.length) return {rows:[], error:null};
    try {
      return {rows:await request(supabase.from(table).select('*').in('nota_id', ids)), error:null};
    } catch (error) { return {rows:[], error}; }
  }
  const uploaded = n => !!n.orden_notificada_at && !!n.archivo_orden_notificacion_path;
  const sanctioned = n => /^L/i.test((n.codigo_infraccion || '').trim()) && !!n.orden_sancion_generada_at && !n.archivo_leve_generada_at;
  const name = n => [n.grado, n.apellidos, n.nombres].filter(Boolean).join(' ') || 'Expediente';
  function receiptHtml(n, receipt, error) {
    if (error) return `<div class="native-notice" role="status"><strong>Recepción por verificar</strong><p>${esc(failure(error))}</p></div>`;
    if (receipt?.recibido_at) return `<div class="native-notice native-success" role="status"><strong>Recibido físicamente</strong><p>Su documento fue recibido el ${esc(date(receipt.recibido_at))}.</p><span class="muted small">Conformidad registrada por Mesa de Partes después de verificar el expediente físico.</span></div>`;
    if (uploaded(n)) return '<div class="native-notice" role="status"><strong>Expediente subido · pendiente de recepción física</strong><p>El envío digital no confirma la recepción. Mesa de Partes debe recibir el físico, verificar que esté conforme y confirmar su recepción.</p></div>';
    if (n.archivo_leve_generada_at) return '<div class="native-notice"><strong>Procedimiento archivado sin sanción</strong><p>No corresponde presentar apelación de una orden de sanción en este registro.</p></div>';
    return '<div class="native-notice"><strong>Pendiente de subir expediente</strong><p>Complete la documentación y adjunte el expediente firmado.</p></div>';
  }
  async function viewFile(bucket, path, filename, output) {
    try {
      const data = await request(supabase.storage.from(bucket).createSignedUrl(path, 300));
      const url = new URL(data.signedUrl);
      if (url.protocol !== 'https:') throw new Error('invalid-url');
      const a = document.createElement('a'); a.href = url.href;
      a.textContent = filename || 'Abrir documento'; a.rel = 'noopener'; a.target = '_blank';
      output.replaceChildren(a); a.click();
    } catch (error) { output.textContent = failure(error); }
  }
  function bindReceipt(box, n, receipt, error, onReceived) {
    box.innerHTML = receiptHtml(n, receipt, error);
    if (state.role !== 'admin' || receipt?.recibido_at || error || !uploaded(n)) return;
    const verification = document.createElement('details');
    verification.className = 'native-verification';
    verification.innerHTML = `<summary>Verificar y recepcionar</summary>
      <p>Confirme únicamente después de recibir y revisar el expediente físico. La fecha se guardará automáticamente.</p>
      <label class="native-check"><input type="checkbox"> He recibido el físico y verificado que toda la documentación está conforme.</label>
      <button type="button" class="btn-primary" disabled>Confirmar recepción física</button>
      <p class="native-feedback" role="status" aria-live="polite"></p>`;
    box.appendChild(verification);
    const check = verification.querySelector('input'), button = verification.querySelector('button'), feedback = verification.querySelector('.native-feedback');
    check.onchange = () => { button.disabled = !check.checked; };
    button.onclick = async () => {
      if (!check.checked || button.disabled) return;
      check.disabled = true; button.disabled = true; feedback.textContent = 'Confirmando recepción…';
      try {
        const saved = await request(supabase.rpc('confirmar_recepcion_fisica', {p_nota_id:n.id, p_conforme:true}));
        if (!saved?.recibido_at) throw new Error('no-receipt');
        bindReceipt(box, n, saved, null, onReceived); onReceived?.(saved);
      } catch (err) {
        feedback.textContent = failure(err); check.disabled = false; check.checked = false;
        // Un reintento es idempotente: el servidor conserva el primer cargo y su fecha.
      }
    };
  }
  function appeal(box, n, row, error) {
    if (!sanctioned(n)) { box.remove(); return; }
    box.innerHTML = `<h3>Apelación <span class="native-badge">Opcional</span></h3>
      <p>Solo si el sancionado no está conforme con su sanción. No es un paso obligatorio ni impide subir o recepcionar el expediente.</p>
      <div class="native-notice"><strong>Plazo: 3 días hábiles</strong><p>Contados desde el día siguiente a la notificación de la sanción. No desde la carga del PDF ni desde la recepción física.</p>
      <p class="muted small">El cómputo debe considerar feriados y días inhábiles aplicables. Esta pantalla no determina la admisibilidad del recurso.</p>
      <a href="https://www.congreso.gob.pe/Docs/comisiones2023/Constitucion/files/dl-1583-2023-of.pdf" target="_blank" rel="noopener">Base normativa: artículo 62, modificado por D. Leg. 1583</a></div>`;
    if (error) {
      const warning = document.createElement('p'); warning.className='native-feedback'; warning.setAttribute('role','status');
      warning.textContent = 'No se pudo consultar la apelación. ' + failure(error); box.appendChild(warning); return;
    }
    if (row) {
      const saved = document.createElement('div'); saved.className = 'native-notice native-success';
      saved.innerHTML = `<strong>Apelación registrada</strong><p>Registrada el ${esc(date(row.presentada_at))}. Pendiente de evaluación por la autoridad competente.</p><p class="muted small">Notificación de la sanción: ${esc(date(row.fecha_notificacion))}.</p><button type="button" class="btn-secondary">Ver apelación adjunta</button><p class="native-feedback" role="status"></p>`;
      saved.querySelector('button').onclick = () => viewFile('apelaciones-expediente', row.archivo_path, row.archivo_nombre, saved.querySelector('.native-feedback'));
      box.appendChild(saved); return;
    }
    const panel = document.createElement('details'); panel.className = 'native-verification';
    panel.innerHTML = `<summary>Presentar apelación (opcional)</summary><form>
      <label>Fecha de notificación de la sanción<input name="fecha" type="date" required max="${esc(today())}" value="${esc(dateISO(n.orden_notificada_at))}" ${n.orden_notificada_at ? 'readonly' : ''}></label>
      <p class="muted small">${n.orden_notificada_at ? 'Fecha ya registrada en la orden.' : 'Ingrese la fecha que consta en el cargo de notificación; no la fecha de elaboración de la orden.'}</p>
      <label>Recurso de apelación firmado (PDF, hasta 20 MB)<input name="archivo" type="file" accept="application/pdf" required></label>
      <label class="native-check"><input name="conforme" type="checkbox" required> Confirmo que el sancionado desea apelar y que adjunto su recurso firmado.</label>
      <button class="btn-primary" type="submit">Registrar apelación</button><p class="native-feedback" role="status" aria-live="polite"></p>
      </form>`;
    box.appendChild(panel);
    const form = panel.querySelector('form'); let busy = false;
    form.onsubmit = async event => {
      event.preventDefault(); if (busy || !form.reportValidity()) return;
      const file = form.elements.archivo.files[0], button = form.querySelector('button'), output = form.querySelector('.native-feedback');
      busy=true; button.disabled=true; output.textContent='Registrando apelación…';
      try {
        if (!file || file.size > 20*1024*1024 || new TextDecoder().decode(await file.slice(0,5).arrayBuffer()) !== '%PDF-') {
          output.textContent = 'Seleccione un PDF válido de hasta 20 MB.'; return;
        }
        // UUID por intento; nunca sobrescribir un recurso previamente presentado.
        const path = `${state.session.user.id}/${n.id}/${crypto.randomUUID()}.pdf`;
        await request(supabase.storage.from('apelaciones-expediente').upload(path,file,{upsert:false,contentType:'application/pdf'}));
        const saved = await request(supabase.rpc('presentar_apelacion_expediente', {
          p_nota_id:n.id, p_fecha_notificacion:form.elements.fecha.value, p_archivo_path:path, p_archivo_nombre:file.name
        }));
        if (!saved?.presentada_at) throw new Error('no-appeal');
        appeal(box,n,saved,null);
      } catch (err) { output.textContent=failure(err); }
      finally { busy=false; button.disabled=false; }
    };
  }
  let revision=0;
  async function enhance(n) {
    const parent = $id('view-nota-detail'); if (!parent) return;
    const generation = ++revision;
    $id('native-case-detail')?.remove();
    let uploadCard=null;
    for (const title of parent.querySelectorAll('h3')) {
      if (title.textContent.trim() === 'Cargo del expediente firmado') {
        title.textContent = 'Subir expediente';
        if(n.orden_notificada_at && title.nextElementSibling?.tagName==='P')
          title.nextElementSibling.textContent=`Fecha de notificación de la sanción: ${date(n.orden_notificada_at)}. El PDF está subido; la recepción física se confirma por separado.`;
      }
      if(title.textContent.trim()==='Subir expediente') uploadCard=title.closest('.detail-card');
    }
    // Cambia solo etiquetas, sin borrar controles, fechas ni manejadores originales.
    const walker=document.createTreeWalker(parent,NodeFilter.SHOW_TEXT);
    while(walker.nextNode()) {
      const node=walker.currentNode;
      if (['Notificación final','Cierre (notificación / expediente)'].includes(node.textContent.trim())) node.textContent='Subir expediente';
    }
    const input=$id('fOrdenNotifArchivo');
    if(input) {
      input.accept='application/pdf';
      const label=input.closest('label');
      if(label?.firstChild?.nodeType === Node.TEXT_NODE) label.firstChild.textContent='Subir expediente completo firmado (PDF) ';
      const button=$id('ordenNotifForm')?.querySelector('button[type=submit]');
      if(button) button.textContent='Subir expediente';
    }
    const legacy=$id('btnAbrirRecepcionDesdeNota');
    if(legacy) {const button=legacy.cloneNode(true); legacy.replaceWith(button); button.textContent='Ver recepción física'; button.onclick=()=>open('recepcion-fisica');}
    const area=document.createElement('div'); area.id='native-case-detail';
    area.innerHTML='<section class="detail-card native-case"><h3>Estado de recepción</h3><div class="native-receipt"><p role="status">Consultando recepción…</p></div><button class="btn-secondary native-refresh" type="button">Actualizar estado</button></section><section class="detail-card native-case native-appeal"><p role="status">Consultando apelación…</p></section>';
    // Mantener visibles primero la navegación y los puntos de avance existentes.
    if(uploadCard) uploadCard.after(area); else ($id('notaDetailContent')||parent).appendChild(area);
    area.querySelector('.native-refresh').onclick=()=>enhance(n);
    const [receipts, appeals]=await Promise.all([metadata('recepciones_fisicas',[n.id]),metadata('apelaciones_expediente',[n.id])]);
    if(generation!==revision || !area.isConnected) return;
    bindReceipt(area.querySelector('.native-receipt'),n,receipts.rows?.[0],receipts.error);
    appeal(area.querySelector('.native-appeal'),n,appeals.rows?.[0],appeals.error);
  }
  if (typeof renderNotaDetail === 'function') {
    const original=renderNotaDetail;
    renderNotaDetail=async function(n) { await original(n); await enhance(n); };
  }
  async function open(view) {
    if(view === 'recepcion-fisica' && state.role !== 'admin') throw new Error('permission');
    let root=$id('view-native-cases');
    if(!root) {root=document.createElement('section');root.id='view-native-cases';root.className='view hidden';document.querySelector('main').appendChild(root);}
    root.innerHTML=`<div class="native-case-heading"><span class="native-badge">${view==='consulta'?'MI EXPEDIENTE':'MESA DE PARTES'}</span>
      <h2>${view==='consulta'?'Consulta de expediente':'Recepción física'}</h2>
      <p class="muted">${view==='consulta'?'Consulte el avance, la recepción física y la apelación opcional de sus expedientes.':'Primero reciba el físico y verifique su conformidad. Solo entonces confirme la recepción.'}</p></div>
      <label>Buscar entre los expedientes cargados<input id="native-case-search" type="search" placeholder="Nombre o número de nota"></label>
      ${view==='recepcion-fisica'?'<label class="native-check"><input id="native-only-pending" type="checkbox" checked> Solo pendientes de recepción física</label>':''}
      <p id="native-case-info" role="status" aria-live="polite"></p><div id="native-case-list"></div>
      <div class="native-case-actions"><button type="button" class="btn-secondary" id="native-case-refresh">Actualizar</button><button type="button" class="btn-secondary" id="native-case-more" hidden>Cargar más</button></div>`;
    showView(root.id);
    let rows=[], offset=0, busy=false, ended=false, records=new Map(), errors=new Map();
    const list=root.querySelector('#native-case-list'), info=root.querySelector('#native-case-info'), more=root.querySelector('#native-case-more'), refresh=root.querySelector('#native-case-refresh');
    function paint() {
      const q=root.querySelector('#native-case-search').value.trim().toLocaleLowerCase('es');
      const only=root.querySelector('#native-only-pending')?.checked;
      list.replaceChildren();
      const visible=rows.filter(n=>(!q||`${name(n)} ${n.numero_nota_falta||''}`.toLocaleLowerCase('es').includes(q))&&(!only||!records.get(n.id)?.recibido_at));
      for(const n of visible) {
        const card=document.createElement('article');card.className='detail-card native-case';card.dataset.note=n.id;
        card.innerHTML=`<h3>${esc(name(n))}</h3><p class="muted small">Nota ${esc(n.numero_nota_falta||'sin número')} · Falta: ${esc(date(n.fecha_falta))}</p><div class="native-receipt"></div>
          <div class="native-case-actions"><button type="button" class="btn-secondary native-detail">Consultar expediente</button>${uploaded(n)?'<button type="button" class="btn-secondary native-pdf">Ver expediente subido</button>':''}</div><p class="native-feedback" role="status"></p>`;
        bindReceipt(card.querySelector('.native-receipt'),n,records.get(n.id),errors.get(n.id),saved=>{
          records.set(n.id,saved); // Conservar el aviso visible tras confirmar; el filtro se reaplica al actualizar.
        });
        card.querySelector('.native-detail').onclick=async event=>{
          const b=event.currentTarget;b.disabled=true;
          try { await openNotaDetail(n.id); } catch(err) {card.querySelector('.native-feedback').textContent=failure(err);}
          finally {b.disabled=false;}
        };
        card.querySelector('.native-pdf')?.addEventListener('click',()=>viewFile('notas',n.archivo_orden_notificacion_path,n.archivo_orden_notificacion_nombre,card.querySelector('.native-feedback')));
        list.appendChild(card);
      }
      if(!visible.length) {const empty=document.createElement('p');empty.textContent=errors.size?'No se pudo verificar el estado de recepción. Actualice para reintentar.':rows.length?'No hay coincidencias con los filtros actuales.':'No hay expedientes disponibles para su perfil.';list.appendChild(empty);}
    }
    async function load(reset) {
      if(busy) return;busy=true;refresh.disabled=true;more.disabled=true;info.textContent='Consultando expedientes…';
      try {
        let query=supabase.from('notas_informativas').select('id,grado,apellidos,nombres,numero_nota_falta,fecha_falta,codigo_infraccion,orden_sancion_generada_at,orden_notificada_at,archivo_orden_notificacion_path,archivo_orden_notificacion_nombre,archivo_leve_generada_at').order('fecha_falta',{ascending:false}).order('id',{ascending:true});
        if(view==='recepcion-fisica') query=query.not('orden_notificada_at','is',null).not('archivo_orden_notificacion_path','is',null);
        const start=reset?0:offset;
        const page=await request(query.range(start,start+99));
        const next=reset?page:[...rows,...page.filter(n=>!rows.some(old=>old.id===n.id))];
        // Solo 100 identificadores por petición; conservar estados de páginas previas.
        const result=await metadata('recepciones_fisicas',page.map(n=>n.id));
        if(!root.isConnected || root.querySelector('#native-case-list')!==list) return;
        if(reset) {records=new Map();errors=new Map();}
        for(const record of result.rows||[])records.set(record.nota_id,record);
        for(const n of page) {if(result.error)errors.set(n.id,result.error);else errors.delete(n.id);}
        rows=next;offset=start+page.length;ended=page.length<100;
        info.textContent=errors.size?failure(errors.values().next().value):`${rows.length} expedientes cargados. Los estados se consultan en línea.`;
        paint(); more.hidden=ended;
      } catch(error) {info.textContent=failure(error);}
      finally {busy=false;refresh.disabled=false;more.disabled=false;}
    }
    root.querySelector('#native-case-search').oninput=paint;
    root.querySelector('#native-only-pending')?.addEventListener('change',paint);
    refresh.onclick=()=>load(true);more.onclick=()=>load(false);
    await load(true);
  }
  window.__faltosCasework={open};
})();
