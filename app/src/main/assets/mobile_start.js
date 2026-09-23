(function () {
  'use strict';
  const c = window.__faltosConfig;
  if (!c || location.origin !== c.origin || !location.pathname.startsWith(c.path)) return;
  // La sesión se instala antes del módulo web, sin recargar ni mostrar el acceso.
  if (c.session && sessionStorage.getItem('faltos-native-session') !== c.nonce) {
    localStorage.setItem(c.sessionKey, c.session);
    sessionStorage.setItem('faltos-native-session', c.nonce);
  }
  // Android es la única fuente del tema, también durante el arranque de cada
  // módulo. La página lee esta preferencia en <head> antes de pintar.
  window.__faltosSetTheme = function (dark) {
    c.dark = !!dark;
    localStorage.setItem('tema', c.dark ? 'dark' : 'light');
    if (document.documentElement) {
      const theme = c.dark ? 'dark' : 'light';
      document.documentElement.setAttribute('data-native-theme', theme);
      document.documentElement.setAttribute('data-theme', theme);
      window.dispatchEvent(new Event('faltos-theme-change'));
    }
  };
  window.__faltosSetTheme(c.dark);
  document.addEventListener('DOMContentLoaded', () => window.__faltosSetTheme(c.dark), {once:true});
  // La web puede volver a escribir data-theme después de cargar. En la app
  // nativa se restaura la preferencia elegida sin recargar ni perder formularios.
  const observarTema = () => {
    if (!document.documentElement) return;
    new MutationObserver(() => {
      const theme = c.dark ? 'dark' : 'light';
      if (document.documentElement.getAttribute('data-theme') !== theme)
        document.documentElement.setAttribute('data-theme', theme);
    }).observe(document.documentElement, {attributes:true, attributeFilter:['data-theme']});
  };
  if (document.documentElement) observarTema();
  else document.addEventListener('DOMContentLoaded', observarTema, {once:true});
  // Acceso web dentro de la app (entrar con correo o CIP): se oculta el botón de Google
  // de la página. Google rechaza iniciar sesión dentro de una vista web incrustada y la
  // vuelta terminaría en el navegador, con la app todavía sin sesión.
  if (!c.view) {
    const ocultarGoogleWeb = () => {
      const bloque = document.getElementById('accesoGoogle');
      if (!bloque || bloque.dataset.faltosOculto) return;
      bloque.dataset.faltosOculto = '1';
      bloque.style.display = 'none';
      const aviso = document.createElement('p');
      aviso.className = 'muted small';
      aviso.style.cssText = 'margin:14px 0 0; text-align:center';
      aviso.textContent = 'Para entrar con Google, vuelva atrás y use «Continuar con Google» en la aplicación.';
      bloque.parentNode.insertBefore(aviso, bloque);
    };
    if (document.readyState === 'loading')
      document.addEventListener('DOMContentLoaded', ocultarGoogleWeb, {once: true});
    else ocultarGoogleWeb();
    return;
  }
  const style = document.createElement('style');
  style.textContent = `
    :root[data-native-theme="light"] {
      color-scheme:light!important;
      --bg:#f4f6f2!important; --bg-elev:#f8faf5!important; --bg-card:#fffefb!important;
      --text:#14231c!important; --text-muted:#526159!important; --border:#849a8d!important;
      --accent:#096b4e!important; --accent-hover:#07543e!important; --accent-soft:#ddf3ea!important;
      --danger:#a62b32!important; --danger-soft:#ffe8e8!important; --native-on-accent:#ffffff;
      --on-accent:#ffffff!important; --shadow-color:39,73,54!important;
      --native-gold:#a36a18; --native-gold-soft:#fff1d5;
      --native-warning:#704c10; --native-warning-bg:#fff0cb; --native-info:#174c82; --native-info-bg:#e8f1ff;
    }
    :root[data-native-theme="dark"] {
      color-scheme:dark!important;
      --bg:#0c1510!important; --bg-elev:#1d3025!important; --bg-card:#17251d!important;
      --text:#eef7f1!important; --text-muted:#c1d0c5!important; --border:#456452!important;
      --accent:#79ddb1!important; --accent-hover:#a0e9c8!important; --accent-soft:#153c30!important;
      --danger:#ffb4ab!important; --danger-soft:#452521!important; --native-on-accent:#073627;
      --on-accent:#073627!important; --shadow-color:0,7,4!important;
      --native-gold:#e9c77b; --native-gold-soft:#3a311e;
      --native-warning:#f1d398; --native-warning-bg:#342b1d; --native-info:#b7d7ff; --native-info-bg:#17324a;
    }
    #topbar, #view-login, #btnTemaToggle, .install-banner { display:none!important; }
    [data-native-gate] #view-token { padding:20px; }
    [data-native-gate=password] .modal-overlay:not(.hidden) { z-index:10000; }
    .native-tools-grid { display:grid; gap:12px; grid-template-columns:repeat(auto-fit,minmax(240px,1fr)); }
    .native-tool { text-align:left; display:grid; gap:8px; padding:20px; border:1px solid var(--border); border-radius:18px; background:var(--bg-card); color:var(--text); }
    .native-tool strong { color:var(--accent); font-size:1.05rem; }
    .native-tool span { color:var(--text-muted); line-height:1.5; }
    html, body { background:var(--bg)!important; color:var(--text)!important; }
    body { margin:0!important; }
    main, #main { width:100%!important; max-width:100%!important; padding:16px!important; box-sizing:border-box; }
    .card { background:var(--bg-card)!important; color:var(--text)!important; border-radius:20px!important; border:1px solid var(--border)!important; box-shadow:0 4px 18px #00000018!important; }
    button, input, select, textarea { font:inherit; }
    button, input:not([type=checkbox]):not([type=radio]), select, textarea { min-height:44px; }
    input, select, textarea { color:var(--text)!important; background:var(--bg-elev)!important; border-color:var(--border)!important; }
    input::placeholder, textarea::placeholder { color:var(--text-muted)!important; opacity:1; }
    input[type=checkbox], input[type=radio] { accent-color:var(--accent); }
    .btn-primary, .case-progress-steps .is-done { color:var(--native-on-accent)!important; }
    .btn-danger:hover { color:var(--bg)!important; }
    .pill-no, .pill-warning { color:var(--native-warning)!important; background:var(--native-warning-bg)!important; }
    .pill-info { color:var(--native-info)!important; background:var(--native-info-bg)!important; }
    .btn { border-radius:12px!important; }
    .modal { width:calc(100% - 24px)!important; max-height:92dvh!important; border-radius:22px!important; }
    .table-wrap, .table-wrapper { overflow-x:auto; }
    h1, h2, h3 { letter-spacing:-.025em; }
    .native-case { padding:20px; margin:16px 0; border:1px solid var(--border); border-radius:20px; background:var(--bg-card); color:var(--text); }
    .native-case h3 { margin:0 0 12px; font-size:1.15rem; }
    .native-case p, .native-case-heading p { line-height:1.55; }
    .native-notice { padding:16px; margin:12px 0; border-radius:14px; background:var(--bg-elev); border:1px solid var(--border); }
    .native-notice p:last-child { margin-bottom:0; }
    .native-success { background:var(--accent-soft); border-color:var(--accent); }
    .native-success strong { color:var(--accent); }
    .native-badge { display:inline-block; color:var(--accent); background:var(--accent-soft); border-radius:8px; padding:5px 9px; font-size:.75rem; letter-spacing:.06em; }
    .native-case-heading { margin-bottom:20px; }
    .native-case-actions { display:flex; flex-wrap:wrap; gap:10px; margin-top:14px; }
    .native-case .btn-secondary, .native-case-actions .btn-secondary { background:var(--bg-elev); color:var(--text); border:1px solid var(--border); border-radius:12px; padding:10px 14px; }
    .native-verification { margin-top:16px; border-top:1px solid var(--border); padding-top:16px; }
    .native-verification summary { cursor:pointer; font-weight:700; padding:8px 0; color:var(--accent); }
    .native-verification form { display:grid; gap:16px; margin-top:16px; }
    .native-check { display:flex!important; gap:12px; align-items:flex-start; line-height:1.5; margin:16px 0; }
    .native-check input { flex:0 0 22px; width:22px!important; height:22px; margin:2px 0!important; }
    .native-feedback { overflow-wrap:anywhere; color:var(--text-muted); }
    .native-case a { color:var(--accent); overflow-wrap:anywhere; }
    .native-case button:disabled { opacity:.55; cursor:default; }
    #view-native-cases input:not([type=checkbox]), .native-verification input:not([type=checkbox]) { display:block; box-sizing:border-box; width:100%; margin-top:8px; padding:12px; border-radius:10px; border:1px solid var(--border); }
    /* Una sola identidad visual para panel, personal, expedientes y herramientas. */
    .view-header { margin:2px 0 20px; padding:0 0 14px; border-bottom:1px solid var(--border); }
    .view-header h2, .native-case-heading h2 { margin:0; color:var(--text); font-size:clamp(1.35rem,5vw,1.8rem); line-height:1.15; letter-spacing:-.035em; }
    .view-header h2::before, .native-case-heading h2::before { content:''; display:inline-block; width:5px; height:1em; margin-right:10px; border-radius:6px; vertical-align:-.12em; background:var(--native-gold); }
    .detail-card, .search-panel, .table-wrap, .stat-tile, .native-tool, .native-case { border-color:var(--border)!important; border-radius:18px!important; background:var(--bg-card)!important; color:var(--text)!important; box-shadow:0 10px 26px rgba(var(--shadow-color),.13)!important; }
    .detail-card, .native-case { padding:18px!important; }
    .detail-card-header { gap:12px; }
    .detail-card h3, .native-case h3 { color:var(--text); letter-spacing:-.02em; }
    .muted, .small, .stat-label, .detail-field .label { color:var(--text-muted); }
    .stat-tile { position:relative; overflow:hidden; padding:17px!important; }
    .stat-tile::before { content:''; position:absolute; inset:0 auto 0 0; width:4px; background:var(--accent); }
    .stat-tile:nth-child(3n+2)::before { background:var(--native-gold); }
    .stat-tile:nth-child(3n)::before { background:var(--native-info); }
    .stat-tile .stat-value { color:var(--text); font-size:2rem; font-weight:800; line-height:1.05; letter-spacing:-.045em; }
    .stat-tile .stat-label { margin-top:8px; line-height:1.35; }
    .stat-tile-btn.activo { background:var(--accent-soft)!important; border-color:var(--accent)!important; }
    .panel-chart-card { min-width:0; padding:18px!important; }
    .panel-chart-card h3 { font-size:1rem; line-height:1.35; }
    .panel-chart-wrap { height:260px; min-width:0; }
    .panel-chart-wide .panel-chart-wrap { height:290px; }
    .table-wrap { overflow-x:auto; }
    th { color:var(--text-muted)!important; background:var(--bg-elev)!important; }
    td { color:var(--text); }
    th, td { border-bottom-color:var(--border)!important; }
    tbody tr:hover { background:var(--bg-elev)!important; }
    .modal, .palette, .toast, .reception-item, .agenda-item, .document-item, .directiva-card { background:var(--bg-card)!important; color:var(--text)!important; border-color:var(--border)!important; }
    .btn-primary { background:var(--accent)!important; color:var(--on-accent)!important; border-radius:12px!important; font-weight:700; }
    .btn-secondary, .btn-ghost { color:var(--text)!important; border-radius:12px!important; }
    .eyebrow { color:var(--native-gold)!important; }
    .native-case { margin:12px 0; border-left:3px solid var(--accent)!important; }
    .native-case-heading { padding:8px 2px 4px; }
    .native-badge { color:var(--native-gold); background:var(--native-gold-soft); font-weight:800; }
    @media (max-width:640px) {
      main, #main { padding:18px 14px 32px!important; }
      .view-actions { gap:10px; }
      .panel-stats { grid-template-columns:repeat(2,minmax(0,1fr)); gap:10px; }
      #panelStats .stat-tile:first-child { grid-column:1/-1; }
      .panel-charts { gap:12px; }
      .table-wrap { margin:0!important; border:1px solid var(--border)!important; border-radius:16px!important; }
      #view-efectivos .table-wrap { background:transparent!important; border:0!important; box-shadow:none!important; overflow:visible; }
      #view-efectivos table, #view-efectivos tbody { display:block; width:100%; }
      #view-efectivos thead { display:none; }
      #view-efectivos tbody { display:grid; gap:10px; }
      #view-efectivos tbody tr { display:grid; grid-template-columns:1fr 1fr; gap:7px 12px; padding:15px; border:1px solid var(--border); border-radius:16px; background:var(--bg-card); box-shadow:0 8px 20px rgba(var(--shadow-color),.12); }
      #view-efectivos tbody td { display:block; min-width:0; padding:0; border:0!important; overflow-wrap:anywhere; font-variant-numeric:tabular-nums; }
      #view-efectivos tbody td:nth-child(2) { grid-column:1/-1; grid-row:1; font-weight:800; font-size:1rem; }
      #view-efectivos tbody td:nth-child(1) { grid-column:1/-1; color:var(--native-gold); font-size:.8rem; font-weight:700; }
      #view-efectivos tbody td:nth-child(3)::before { content:'CIP'; }
      #view-efectivos tbody td:nth-child(4)::before { content:'DNI'; }
      #view-efectivos tbody td:nth-child(3)::before, #view-efectivos tbody td:nth-child(4)::before { display:block; margin:4px 0; color:var(--text-muted); font-size:.7rem; font-weight:700; letter-spacing:.08em; }
    }
  `;
  const attach = () => {
    window.__faltosSetTheme(c.dark);
    if (document.head && !style.isConnected) document.head.appendChild(style);
  };
  attach();
  if (!style.isConnected) {
    const observer = new MutationObserver(() => { attach(); if (style.isConnected) observer.disconnect(); });
    observer.observe(document, { childList:true, subtree:true });
  }
})();
