(function () {
  'use strict';
  const c = window.__faltosConfig;
  if (!c || location.origin !== c.origin || !location.pathname.startsWith(c.path)) return;
  // La sesión se instala antes del módulo web, sin recargar ni mostrar el acceso.
  if (c.session && sessionStorage.getItem('faltos-native-session') !== c.nonce) {
    localStorage.setItem(c.sessionKey, c.session);
    sessionStorage.setItem('faltos-native-session', c.nonce);
  }
  if (!c.view) return;
  // Android es la fuente del tema en los módulos (su barra web está oculta).
  // No se pisa la preferencia del sitio ni se recarga un formulario al cambiar de modo.
  window.__faltosSetTheme = function (dark) {
    c.dark = !!dark;
    if (document.documentElement) {
      document.documentElement.setAttribute('data-native-theme', c.dark ? 'dark' : 'light');
      document.documentElement.setAttribute('data-theme', c.dark ? 'dark' : 'light');
    }
  };
  window.__faltosSetTheme(c.dark);
  document.addEventListener('DOMContentLoaded', () => window.__faltosSetTheme(c.dark), {once:true});
  const style = document.createElement('style');
  style.textContent = `
    :root[data-native-theme="light"] {
      color-scheme:light!important;
      --bg:#f3f6f4!important; --bg-elev:#fafcfb!important; --bg-card:#ffffff!important;
      --text:#14231c!important; --text-muted:#526159!important; --border:#899d91!important;
      --accent:#0b6b4f!important; --accent-hover:#08553e!important; --accent-soft:#ddf3ea!important;
      --danger:#a62b32!important; --danger-soft:#ffe8e8!important; --native-on-accent:#ffffff;
      --native-warning:#704c10; --native-warning-bg:#fff0cb; --native-info:#174c82; --native-info-bg:#e8f1ff;
    }
    :root[data-native-theme="dark"] {
      color-scheme:dark!important;
      --bg:#0d1511!important; --bg-elev:#1b2821!important; --bg-card:#15211b!important;
      --text:#eef7f1!important; --text-muted:#b7c7be!important; --border:#536b5e!important;
      --accent:#6ed6ad!important; --accent-hover:#87e2be!important; --accent-soft:#153c30!important;
      --danger:#ffb4ab!important; --danger-soft:#452521!important; --native-on-accent:#073627;
      --native-warning:#f1d398; --native-warning-bg:#342b1d; --native-info:#b7d7ff; --native-info-bg:#17324a;
    }
    #topbar, #view-login, .install-banner { display:none!important; }
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
