// Contrato de los módulos añadidos en GitHub. Solo datos y credenciales ficticios.
window.updatesQa={pending:false,token:false,notesReady:false,profileCalls:0,notesCalls:0,authedCalls:0,failProfile:false};
const uq=window.updatesQa;
const qMain=document.querySelector('main'),qNav=document.querySelector('nav');
for(const name of ['panel','roles','directivas','agenda','documentos','historial']){
  const button=document.createElement('button');button.className='tab-btn';button.dataset.view=name;
  button.textContent=name;qNav.appendChild(button);
  const view=document.createElement('section');view.id='view-'+name;view.className='view hidden card';
  view.innerHTML='<h2>'+name+'</h2><p class="muted">Datos de demostración</p>';qMain.appendChild(view);
  button.onclick=()=>{uq.panelSawNotes=uq.notesReady;showView(view.id);};
}
for(const [buttonId,inputId,modalId] of [['btnReincorporacionLote','rlArchivo','modalReincorporacionLote'],['btnContinuanFaltosLote','cfArchivo','modalContinuanFaltosLote'],['btnExpedientesLote','xlArchivo','modalExpedientesLote']]){
  const b=document.createElement('button');b.id=buttonId;document.getElementById('view-dashboard').appendChild(b);
  const m=document.createElement('div');m.id=modalId;m.className='modal hidden';
  m.innerHTML='<h2>Revisar documentos</h2><input type="file" id="'+inputId+'" accept="application/pdf" multiple>';
  qMain.appendChild(m);b.onclick=()=>{uq.batchSawNotes=uq.notesReady;m.classList.remove('hidden');};
  m.querySelector('input').onchange=e=>{uq.batchFile=e.target.files[0]?.name;uq.batchReadNotes=uq.notesReady;};
}
for(const id of ['modalCambiarClave','view-token']){
  const el=document.createElement('div');el.id=id;el.className='modal hidden';
  el.innerHTML='<h2>Paso de seguridad de demostración</h2><input type="password" autocomplete="off">';qMain.appendChild(el);
}
supabase.rpc=async name=>{
  if(name==='necesita_cambiar_clave')return {data:uq.pending,error:null};
  if(name==='cip_actual')return uq.failProfile?{data:null,error:{message:'simulado'}}:{data:'123456',error:null};
  throw new Error('RPC no prevista');
};
async function loadProfile(){uq.profileCalls++;state.role='admin';state.cip='correo.google';}
async function loadNotas(){uq.notesCalls++;await new Promise(r=>setTimeout(r,250));if(uq.failNotes)return;uq.notesReady=true;state.notas=[{id:'demo'}];}
function renderPanel(){uq.chartRenders=(uq.chartRenders||0)+1;}
function abrirCambioClave(){document.getElementById('modalCambiarClave').classList.remove('hidden');}
onAuthed=async function(s){
  state.session=s;uq.authedCalls++;
  if(uq.token){showView('view-token');return;}
  if(uq.pending){abrirCambioClave();return;}
  await loadProfile(s.user.id);showView('view-dashboard');loadNotas();
};
uq.currentCip=()=>state.cip;
uq.reload=()=>loadNotas();
uq.resume=()=>{uq.pending=false;uq.token=false;document.getElementById('modalCambiarClave').classList.add('hidden');document.getElementById('view-token').classList.add('hidden');return onAuthed(session);};
