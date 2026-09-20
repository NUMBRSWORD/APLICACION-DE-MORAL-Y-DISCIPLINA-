// Datos y API exclusivamente ficticios; este archivo se incluye solo en el APK de pruebas.
window.casesQa={receipts:[],appeals:[],calls:[],uploads:[],failTable:null,failRpc:false,notes:[
  {id:'nota-subida',grado:'S1 PNP',apellidos:'DEMOSTRACIÓN',nombres:'ANA',numero_nota_falta:'001',fecha_falta:'2026-09-18',codigo_infraccion:'L21',orden_sancion_generada_at:'2026-09-18T12:00:00Z',orden_notificada_at:'2026-09-18T12:00:00Z',archivo_orden_notificacion_path:'nota-subida/legajo.pdf',archivo_orden_notificacion_nombre:'legajo-demo.pdf'},
  {id:'nota-pendiente',grado:'S2 PNP',apellidos:'PRUEBA',nombres:'LUIS',numero_nota_falta:'002',fecha_falta:'2026-09-17',codigo_infraccion:'L21',orden_sancion_generada_at:'2026-09-17T12:00:00Z'},
  {id:'nota-archivo',apellidos:'ARCHIVADO',nombres:'DEMO',numero_nota_falta:'003',fecha_falta:'2026-09-16',codigo_infraccion:'L21',archivo_leve_generada_at:'2026-09-16T12:00:00Z'}
]};
supabase.from=function(table){
  const filter=[];let start=0,end=999;
  return {select(){return this;},order(){return this;},in(key,values){filter.push(r=>values.includes(r[key]));return this;},not(key){filter.push(r=>r[key]!=null);return this;},range(a,b){start=a;end=b;return this;},then(resolve,reject){
    const fixture=window.casesQa;
    if(fixture.failTable===table)return Promise.resolve({data:null,error:{code:'42P01'}}).then(resolve,reject);
    const rows=table==='notas_informativas'?fixture.notes:table==='recepciones_fisicas'?fixture.receipts:fixture.appeals;
    return Promise.resolve({data:rows.filter(r=>filter.every(f=>f(r))).slice(start,end+1),error:null}).then(resolve,reject);
  }};
};
supabase.rpc=async function(name,args){
  const f=window.casesQa; f.calls.push({name,args});
  await new Promise(resolve=>setTimeout(resolve,120));
  if(f.failRpc)return {error:{code:'42501'}};
  if(name==='confirmar_recepcion_fisica'){
    if(state.role!=='admin'||!args.p_conforme)return {error:{code:'42501'}};
    let r=f.receipts.find(r=>r.nota_id===args.p_nota_id);
    if(!r){r={nota_id:args.p_nota_id,recibido_at:'2026-09-20T01:30:00Z',conformidad_verificada:true};f.receipts.push(r);}
    return {data:r};
  }
  let r=f.appeals.find(r=>r.nota_id===args.p_nota_id);
  if(!r){r={nota_id:args.p_nota_id,presentada_at:'2026-09-20T01:30:00Z',fecha_notificacion:f.notes.find(n=>n.id===args.p_nota_id)?.orden_notificada_at?.slice(0,10)||args.p_fecha_notificacion,archivo_path:args.p_archivo_path,archivo_nombre:args.p_archivo_nombre};f.appeals.push(r);}
  return {data:r};
};
supabase.storage={from(bucket){return {async upload(path,file){window.casesQa.uploads.push({bucket,path,name:file.name});return {data:{path}};},async createSignedUrl(path){return {data:{signedUrl:'https://example.invalid/qa-document.pdf'}};}};}};
async function renderNotaDetail(n){
  document.getElementById('view-nota-detail').innerHTML=`<h2>Expediente de demostración</h2><div class="detail-card"><h3>Cargo del expediente firmado</h3><p>Registrado el 18/09/2026</p><span>Notificación final</span></div><button id="btnVolverDashboard">Volver</button>`;
}
openNotaDetail=async function(id){window.openedNote=id;await renderNotaDetail(window.casesQa.notes.find(n=>n.id===id));showView('view-nota-detail');};
window.openQaNote=id=>openNotaDetail(id);
window.setQaRole=role=>{state.role=role;};
