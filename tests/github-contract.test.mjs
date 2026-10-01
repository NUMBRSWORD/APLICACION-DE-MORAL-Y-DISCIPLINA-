// Lectura estática del checkout web auditado. No usa sesiones ni APIs privadas.
// git clone --depth 1 https://github.com/NUMBRSWORD/moral-y-disciplina.git app/build/upstream-github
import {readFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const root=new URL('../app/build/upstream-github/',import.meta.url);
const html=await readFile(new URL('index.html',root),'utf8');
const js=await readFile(new URL('app.js',root),'utf8');
for(const name of ['onAuthed','loadProfile','loadNotas','showView','openNotaDetail','renderPanel','abrirCambioClave'])
  assert.match(js,new RegExp('function '+name+'\\s*\\('),`Contrato de función: ${name}`);
for(const name of ['cumplimiento','seguimiento','efectivos','panel','roles','directivas','agenda','documentos','historial']){
  assert.ok(html.includes(`data-view="${name}"`),`Pestaña: ${name}`);
  assert.ok(html.includes(`id="view-${name}"`),`Vista: ${name}`);
}
for(const id of ['view-token','modalCambiarClave','btnVolverDashboard','btnFaltasLote','flArchivo','modalFaltasLote',
  'btnReincorporacionLote','rlArchivo','modalReincorporacionLote','btnContinuanFaltosLote','cfArchivo','modalContinuanFaltosLote',
  'btnExpedientesLote','xlArchivo','modalExpedientesLote'])
  assert.ok(html.includes(`id="${id}"`),`Elemento: ${id}`);
for(const rpc of ['necesita_cambiar_clave','confirmar_cambio_clave'])assert.ok(js.includes(`"${rpc}"`),`RPC: ${rpc}`);
assert.match(js,/state\.notas\s*=\s*data\s*\|\|\s*\[\]/,'Detectar carga completa de notas');
const config=await readFile(new URL('config.js',root),'utf8');
const auth=await readFile(new URL('../app/src/main/java/com/hidalgoferrai/myapplication/ConfigSupabase.java',import.meta.url),'utf8');
const servidor=/https:\/\/[a-z0-9]+\.supabase\.co/;
assert.ok(config.match(servidor)&&auth.match(servidor),'Configuración de backend presente');
assert.equal(config.match(servidor)[0],auth.match(servidor)[0],'Web y APK deben usar el mismo backend');
for(const campo of ['registrar_notificacion_orden','archivo_orden_notificacion_path','orden_notificada_at'])
  assert.ok(js.includes(campo),`Registro canónico del expediente: ${campo}`);
console.log('Contrato de funciones, vistas, formularios y seguridad compatible con el checkout auditado.');
