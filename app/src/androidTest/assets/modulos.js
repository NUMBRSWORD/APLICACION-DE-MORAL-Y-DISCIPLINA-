const state={session:null,role:'admin'};
const session=JSON.parse(localStorage.getItem(window.__faltosConfig.sessionKey)||'null');
window.bootHasSession=!!session;
window.submitted=false;
const supabase={auth:{
  onAuthStateChange(callback){return {data:{subscription:{unsubscribe(){}}}};},
  async getSession(){return {data:{session}};}
}};
function showView(id){
  document.querySelectorAll('.view').forEach(v=>v.classList.add('hidden'));
  document.getElementById(id).classList.remove('hidden');
}
async function onAuthed(s){state.session=s;await new Promise(r=>setTimeout(r,100));showView('view-dashboard');}
async function openNotaDetail(id){window.openedNote=id;showView('view-nota-detail');}
document.querySelectorAll('.tab-btn').forEach(b=>b.onclick=()=>showView('view-'+b.dataset.view));
document.getElementById('btnFaltasLote').onclick=()=>document.getElementById('modalFaltasLote').classList.remove('hidden');
document.getElementById('flArchivo').onchange=e=>{window.chosenName=e.target.files[0]?.name;};
document.getElementById('guardar').onclick=()=>window.submitted=true;
document.getElementById('cerrar').onclick=()=>document.getElementById('modalFaltasLote').classList.add('hidden');
// Simula el segundo evento de autenticación que antes regresaba al inicio.
setTimeout(()=>showView('view-dashboard'),1200);
