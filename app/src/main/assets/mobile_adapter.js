// Integración Android con el contrato público de la web. No confirma formularios.
;(() => {
  'use strict';
  const c=window.__faltosConfig;
  if(!c||!window.AndroidNavegacion) return;
  const post=(type,extra={})=>AndroidNavegacion.postMessage(JSON.stringify({type,nonce:c.nonce,...extra}));
  const visible=id=>{const el=document.getElementById(id);return el&&!el.classList.contains('hidden');};
  supabase.auth.onAuthStateChange((event,session)=>{
    if(session) post('session',{session});
    else if(event==='SIGNED_OUT') post('signed-out');
  });
  if(!c.view) return;
  let opened=false,startup=null,notesPromise=null;
  const originalShow=showView;
  showView=function(id){if(opened&&id==='view-dashboard')return;originalShow(id);};
  // El panel web conserva sus datos; aquí solo se afina la presentación de
  // Chart.js para que los tres gráficos respondan al tema nativo.
  if(typeof renderPanel==='function'){
    const originalRenderPanel=renderPanel;
    renderPanel=async function(...args){
      await originalRenderPanel.apply(this,args);
      if(typeof chartsPanel==='undefined'||!chartsPanel)return;
      const css=getComputedStyle(document.documentElement);
      const color=name=>css.getPropertyValue(name).trim();
      const dark=c.dark;
      const ink=color('--text'),muted=color('--text-muted'),accent=color('--accent');
      const grid=dark?'rgba(191,222,201,.15)':'rgba(33,84,56,.13)';
      const gold=dark?'#e9c77b':'#a36a18';
      const palette=dark
        ? ['#82d9ab',gold,'#85b9ef','#b9a3ee','#f29a91','#8caea0']
        : ['#087e56',gold,'#3c78af','#7458aa','#b85755','#668073'];
      const reduce=window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
      const common=chart=>{
        chart.options.animation=reduce?false:{duration:500,easing:'easeOutQuart'};
        chart.options.plugins.tooltip={
          backgroundColor:dark?'#20382b':'#183a2b',titleColor:'#fff',bodyColor:'#fff',
          borderColor:gold,borderWidth:1,padding:12,cornerRadius:10,
          displayColors:true,boxPadding:5
        };
      };
      const estado=chartsPanel.estado;
      if(estado){
        common(estado);
        estado.data.datasets[0].backgroundColor=palette;
        estado.data.datasets[0].borderColor=color('--bg-card');
        estado.data.datasets[0].borderWidth=3;
        estado.data.datasets[0].hoverOffset=9;
        estado.data.datasets[0].borderRadius=5;
        estado.options.cutout='67%';
        estado.options.plugins.legend={position:'bottom',labels:{color:ink,usePointStyle:true,
          pointStyle:'circle',boxWidth:8,padding:14,font:{size:12,weight:'600'}}};
        estado.update();
      }
      const codigo=chartsPanel.codigo;
      if(codigo){
        common(codigo);
        codigo.data.datasets[0].backgroundColor=context=>{
          const area=context.chart.chartArea;
          if(!area)return accent;
          const gradient=context.chart.ctx.createLinearGradient(area.left,0,area.right,0);
          gradient.addColorStop(0,dark?'#2d9a70':'#0b7450');
          gradient.addColorStop(1,dark?'#93d9ac':'#47aa73');
          return gradient;
        };
        codigo.data.datasets[0].borderRadius=8;
        codigo.data.datasets[0].borderSkipped=false;
        codigo.data.datasets[0].maxBarThickness=24;
        codigo.options.scales.x.grid.color=grid;
        codigo.options.scales.x.border={display:false};
        codigo.options.scales.y.border={display:false};
        codigo.options.scales.y.ticks.color=ink;
        codigo.options.scales.y.ticks.font={weight:'600',size:11};
        codigo.update();
      }
      const tendencia=chartsPanel.tendencia;
      if(tendencia){
        common(tendencia);
        const serie=tendencia.data.datasets[0];
        serie.borderColor=accent;
        serie.backgroundColor=context=>{
          const area=context.chart.chartArea;
          if(!area)return color('--accent-soft');
          const gradient=context.chart.ctx.createLinearGradient(0,area.top,0,area.bottom);
          gradient.addColorStop(0,dark?'rgba(121,221,177,.34)':'rgba(9,107,78,.24)');
          gradient.addColorStop(1,'rgba(9,107,78,0)');
          return gradient;
        };
        serie.borderWidth=3;
        serie.pointRadius=4;
        serie.pointHoverRadius=7;
        serie.pointBackgroundColor=gold;
        serie.pointBorderColor=color('--bg-card');
        serie.pointBorderWidth=2;
        serie.tension=.38;
        tendencia.options.scales.y.grid.color=grid;
        tendencia.options.scales.y.border={display:false};
        tendencia.options.scales.x.border={display:false};
        tendencia.options.scales.x.ticks.color=muted;
        tendencia.update();
      }
    };
  }
  // Una cuenta Google usa el CIP registrado, no el nombre anterior a @.
  if(typeof loadProfile==='function'){
    const originalProfile=loadProfile;
    loadProfile=async function(id){
      await originalProfile(id);state.cip=null;
      const {data,error}=await supabase.rpc('cip_actual');
      if(error)throw new Error('profile');
      if(typeof data==='string'&&data.trim())state.cip=data.trim();
    };
  }
  // Panel y Agenda necesitan los datos que el arranque carga en segundo plano.
  if(typeof loadNotas==='function'){
    const originalNotes=loadNotas;
    loadNotas=function(...args){
      const previous=state.notas;
      notesPromise=Promise.resolve(originalNotes(...args)).then(()=>{
        // La web conserva el array anterior cuando falla la consulta. No mostrar
        // un panel vacío o datos antiguos como si la carga hubiera terminado.
        if(state.notas===previous)throw new Error('notes');
      });
      notesPromise.catch(()=>post('error'));return notesPromise;
    };
  }
  window.addEventListener('faltos-theme-change',()=>{
    if(opened&&visible('view-panel')&&typeof renderPanel==='function')
      Promise.resolve().then(()=>renderPanel()).catch(()=>post('error'));
  });
  function authStep(){
    const step=visible('modalCambiarClave')?'password':visible('view-token')?'token':null;
    if(step){document.documentElement.setAttribute('data-native-gate',step);post('auth-step',{step});}
    else document.documentElement.removeAttribute('data-native-gate');
    return step;
  }
  const back=document.getElementById('btnVolverDashboard');
  if(back)back.addEventListener('click',event=>{
    event.stopImmediatePropagation();
    if(c.view==='detalle')post('back');
    else if(c.view==='consulta'||c.view==='recepcion-fisica')window.__faltosCasework.open(c.view);
    else originalShow('view-'+c.view);
  },true);
  const originalAuthed=onAuthed;
  onAuthed=function(session){
    state.session=session;
    if(startup)return startup;
    if(opened)return Promise.resolve();
    startup=(async()=>{
      if(c.view==='seguridad'){
        // No cargar expedientes ni saltar las políticas nativas en este paso.
        const {data,error}=await supabase.rpc('necesita_cambiar_clave');
        if(error||typeof data!=='boolean')throw new Error('security');
        if(data){
          if(typeof abrirCambioClave!=='function')throw new Error('security');
          abrirCambioClave({obligatorio:true});authStep();return;
        }
        const {data:current,error:sessionError}=await supabase.auth.getSession();
        if(sessionError||!current.session)throw new Error('session');
        post('security-complete',{session:current.session});return;
      }
      await originalAuthed(session);
      // Una clave/token pendientes no son un fallo. No caduca la pantalla
      // mientras la persona completa ese paso; al terminar se reanuda el destino.
      if(authStep())return;
      await openModule();
    })().catch(()=>post('error')).finally(()=>{startup=null;});
    return startup;
  };
  const batches={
    registro:{button:'btnFaltasLote',input:'flArchivo',modal:'modalFaltasLote'},
    reincorporacion:{button:'btnReincorporacionLote',input:'rlArchivo',modal:'modalReincorporacionLote'},
    continuan:{button:'btnContinuanFaltosLote',input:'cfArchivo',modal:'modalContinuanFaltosLote'},
    'expedientes-lote':{button:'btnExpedientesLote',input:'xlArchivo',modal:'modalExpedientesLote'}
  };
  function toolsMenu(){
    if(state.role!=='admin')throw new Error('permission');
    const panel=document.createElement('section');panel.id='view-herramientas';panel.className='view hidden';
    panel.innerHTML='<h2>Herramientas de gestión</h2><p class="muted">Cada opción abre su módulo. Los documentos siempre se revisan antes de registrarse.</p><div class="native-tools-grid"></div>';
    document.getElementById(panel.id)?.remove();document.querySelector('main').appendChild(panel);
    const options=[
      ['reincorporacion','Reincorporación grupal','PDF con uno o varios efectivos.'],
      ['continuan','Continúan faltos','Agregar constancias sin crear otra falta.'],
      ['roles','Roles de servicio','Puestos y programación del personal.'],
      ['directivas','Directivas','Consultar y gestionar documentos normativos.'],
      ['agenda','Agenda','Pendientes y vencimientos.'],
      ['documentos','Documentos generados','Versiones de imputaciones, órdenes e informes.'],
      ['historial','Historial de actividad','Consultar los movimientos registrados.']
    ];
    for(const [view,title,description]of options){
      const trigger=batches[view]?document.getElementById(batches[view].button):document.querySelector(`.tab-btn[data-view="${view}"]`);
      if(!trigger||trigger.disabled||trigger.classList.contains('hidden'))continue;
      const b=document.createElement('button');b.type='button';b.className='native-tool';b.dataset.module=view;
      const h=document.createElement('strong'),p=document.createElement('span');h.textContent=title;p.textContent=description;
      b.append(h,p);b.onclick=()=>post('open-module',{view});panel.querySelector('.native-tools-grid').appendChild(b);
    }
    originalShow(panel.id);
  }
  async function openModule(){
    if(opened)return;
    if(!state.role)throw new Error('profile');
    if(['panel','agenda','seguimiento','reincorporacion','continuan'].includes(c.view)){
      if(notesPromise)await notesPromise;else if(typeof loadNotas==='function')await loadNotas();
    }
    if(c.view==='herramientas')toolsMenu();
    else if(c.view==='consulta'||c.view==='recepcion-fisica')await window.__faltosCasework.open(c.view);
    else if(c.view==='detalle'){
      if(!c.note)throw new Error('missing-note');
      await openNotaDetail(c.note);
      if(!visible('view-nota-detail'))throw new Error('detail');
    }else if(batches[c.view]){
      const batch=batches[c.view],button=document.getElementById(batch.button),modal=document.getElementById(batch.modal);
      if(!button||!modal||button.classList.contains('hidden')||button.disabled||state.role!=='admin')throw new Error('permission');
      button.click();const input=document.getElementById(batch.input);
      if(!input||!visible(batch.modal))throw new Error('document');
      if(c.document){
        const response=await fetch(c.document);if(!response.ok)throw new Error('document');
        const data=new DataTransfer();data.items.add(new File([await response.blob()],c.filename||'documento.pdf',{type:'application/pdf'}));
        input.files=data.files;input.dispatchEvent(new Event('change',{bubbles:true}));
      }else if(c.view==='registro')throw new Error('document');
      document.querySelectorAll('.view').forEach(v=>v.classList.add('hidden'));
      new MutationObserver(()=>{if(opened&&modal.classList.contains('hidden'))post('back');})
        .observe(modal,{attributes:true,attributeFilter:['class']});
    }else{
      const button=document.querySelector(`.tab-btn[data-view="${c.view}"]`),panel=document.getElementById('view-'+c.view);
      if(!button||!panel||button.classList.contains('hidden')||button.disabled)throw new Error('permission');
      button.click();if(panel.classList.contains('hidden'))throw new Error('view');
    }
    opened=true;post('ready',{view:c.view});
  }
  supabase.auth.getSession().then(({data,error})=>{
    if(error||!data.session)throw new Error('session');post('session',{session:data.session});return onAuthed(data.session);
  }).catch(()=>post('error'));
})();
