
// Adaptador nativo añadido únicamente al módulo público de este sitio.
// Registrar documentos y aceptar políticas siguen siendo acciones explícitas.
;(() => {
  'use strict';
  const c = window.__faltosConfig;
  if (!c || !window.AndroidNavegacion) return;
  const post = (type, extra = {}) => AndroidNavegacion.postMessage(JSON.stringify({type, nonce:c.nonce, ...extra}));
  supabase.auth.onAuthStateChange((event, session) => {
    if (session) post('session', {session});
    else if (event === 'SIGNED_OUT') post('signed-out');
  });
  if (!c.view) return;
  let opened = false;
  const originalShow = showView;
  showView = function (id) {
    // Renovar la sesión no debe devolver al dashboard de escritorio.
    if (opened && id === 'view-dashboard') return;
    originalShow(id);
  };
  const back = document.getElementById('btnVolverDashboard');
  if (back) back.addEventListener('click', event => {
    event.stopImmediatePropagation();
    if (c.view === 'detalle') post('back');
    else if (c.view === 'consulta' || c.view === 'recepcion-fisica') window.__faltosCasework.open(c.view);
    else originalShow('view-' + c.view);
  }, true);
  let startup;
  const originalAuthed = onAuthed;
  onAuthed = function (session) {
    state.session = session;
    if (!startup) startup = originalAuthed(session).then(openModule);
    return startup;
  };
  async function openModule() {
    if (opened) return;
    if (!state.role) throw new Error('profile');
    if (c.view === 'consulta' || c.view === 'recepcion-fisica') {
      await window.__faltosCasework.open(c.view);
    } else if (c.view === 'detalle') {
      if (!c.note) throw new Error('missing-note');
      await openNotaDetail(c.note);
      if (document.getElementById('view-nota-detail')?.classList.contains('hidden')) throw new Error('detail');
    } else if (c.view === 'registro') {
      const button = document.getElementById('btnFaltasLote');
      if (!button || button.classList.contains('hidden') || button.disabled || state.role !== 'admin') throw new Error('permission');
      button.click();
      const input = document.getElementById('flArchivo');
      if (!input || !c.document) throw new Error('document');
      const response = await fetch(c.document);
      if (!response.ok) throw new Error('document');
      const data = new DataTransfer();
      data.items.add(new File([await response.blob()], c.filename || 'documento.pdf', {type:'application/pdf'}));
      input.files = data.files;
      input.dispatchEvent(new Event('change', {bubbles:true}));
      const modal = document.getElementById('modalFaltasLote');
      new MutationObserver(() => {
        if (opened && modal.classList.contains('hidden')) post('back');
      }).observe(modal, {attributes:true, attributeFilter:['class']});
    } else {
      const button = document.querySelector('.tab-btn[data-view="' + c.view + '"]');
      const panel = document.getElementById('view-' + c.view);
      if (!button || !panel || button.classList.contains('hidden') || button.disabled) throw new Error('permission');
      button.click();
      if (panel.classList.contains('hidden')) throw new Error('view');
    }
    opened = true;
    if (c.view === 'seguimiento') {
      const title = document.querySelector('#view-seguimiento h2, #view-seguimiento h1');
      if (title) title.textContent = 'Expedientes concluidos';
    }
    post('ready', {view:c.view});
  }
  supabase.auth.getSession().then(({data, error}) => {
    if (error || !data.session) throw new Error('session');
    post('session', {session:data.session});
    return onAuthed(data.session);
  }).catch(() => post('error'));
})();
