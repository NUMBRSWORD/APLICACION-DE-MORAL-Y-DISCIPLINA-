package com.hidalgoferrai.myapplication;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Panel de consulta de la sesión actual. Solo conserva filas en memoria. */
final class PanelOficina {
    private final InicioActivity pantalla;
    private final LinearLayout menu, cuerpo;
    private final ExecutorService trabajo=Executors.newSingleThreadExecutor();
    private final String usuario;
    private PanelDatos datos;
    private String pestana, periodo, oficial, estadoCarga="Consultando expedientes…";
    private boolean ocupado;
    private int mostrar=30;
    private static final TimeZone LIMA=TimeZone.getTimeZone("America/Lima");

    PanelOficina(InicioActivity pantalla, Bundle estado) {
        this.pantalla=pantalla;
        usuario=Perfil.usuarioId(pantalla);
        menu=pantalla.findViewById(R.id.panelMenu);
        cuerpo=pantalla.findViewById(R.id.panelContenido);
        pestana=estado==null?"panel":estado.getString("tabPanel","panel");
        periodo=estado==null?hoy().substring(0,7):estado.getString("periodoPanel",hoy().substring(0,7));
        oficial=estado==null?null:estado.getString("oficialPanel");
        dibujar();
    }
    void conservar(Bundle estado) {
        estado.putString("tabPanel",pestana); estado.putString("periodoPanel",periodo); estado.putString("oficialPanel",oficial);
    }
    void cerrar(){trabajo.shutdownNow();}
    private boolean vigente(){return !pantalla.isFinishing() && !pantalla.isDestroyed() && Objects.equals(usuario,Perfil.usuarioId(pantalla));}
    private static String hoy(){
        SimpleDateFormat f=new SimpleDateFormat("yyyy-MM-dd",Locale.ROOT); f.setTimeZone(LIMA); return f.format(new Date());
    }
    private boolean vigenteFecha(JSONObject n){return PanelDatos.valor(n,"fecha_falta").startsWith(periodo);}
    private static String fecha(JSONObject n,String campo){return Fechas.lima(PanelDatos.valor(n,campo));}
    private static TextView rotulo(LinearLayout zona,String texto,boolean titulo){
        TextView v=new TextView(zona.getContext()); v.setText(texto); v.setTextSize(titulo?18:15);
        v.setTextColor(zona.getContext().getColor(titulo?R.color.texto_principal:R.color.texto_secundario));
        if(titulo)v.setTypeface(null,android.graphics.Typeface.BOLD);
        int pad=Math.round(6*zona.getResources().getDisplayMetrics().density);
        v.setPadding(pad,pad,pad,pad); zona.addView(v); return v;
    }
    private MaterialButton boton(LinearLayout zona,String texto,Runnable accion){
        MaterialButton b=new MaterialButton(pantalla,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(texto); b.setAllCaps(false); b.setOnClickListener(v->accion.run()); zona.addView(b); return b;
    }
    private void ir(String destino){pestana=destino;mostrar=30;dibujar();}
    private void modulo(String vista,String id){
        Intent i=new Intent(pantalla,WebActivity.class).putExtra(WebActivity.EXTRA_VISTA,vista);
        if(id!=null)i.putExtra(WebActivity.EXTRA_NOTA_ID,id); pantalla.startActivity(i);
    }
    void actualizar(){
        if(ocupado || usuario==null)return;
        ocupado=true; datos=null; estadoCarga="Consultando expedientes…"; dibujar();
        trabajo.execute(()->{
            try{
                String token=SesionActual.token(pantalla,usuario);
                PanelDatos resultado=PanelDatos.cargar(token,usuario);
                pantalla.runOnUiThread(()->{if(vigente()){datos=resultado;ocupado=false;dibujar();}});
            }catch(Exception ex){pantalla.runOnUiThread(()->{if(vigente()){
                ocupado=false;estadoCarga="No se pudo verificar el panel. Compruebe la conexión, la sesión y el CIP de la cuenta.";dibujar();
            }});}
        });
    }
    private void dibujar(){
        menu.removeAllViews();cuerpo.removeAllViews();
        boolean cuenta="cuenta".equals(pestana), entregas="entregas".equals(pestana);
        for(String[] item:new String[][]{{"panel","Panel"},{"entregas","Entregas"},{"cuenta","Cuenta"}}){
            MaterialButton b=boton(menu,item[1],()->ir(item[0]));
            b.setSelected(item[0].equals(pestana));b.setEnabled(!b.isSelected());
            b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(2,0,2,0);b.setLayoutParams(new LinearLayout.LayoutParams(0,-2,1));
        }
        pantalla.findViewById(R.id.panelCuenta).setVisibility(cuenta?View.VISIBLE:View.GONE);
        ((TextView)pantalla.findViewById(R.id.tvPanelTitulo)).setText(cuenta?"Mi cuenta":entregas?"Entregas":
            (datos!=null?datos.administrador:Perfil.esAdministrador(pantalla))?"Panel del administrador":"Mi panel");
        if(cuenta){boton(cuerpo,"Consultar políticas y firmas",this::politicas);return;}
        if(datos==null){rotulo(cuerpo,estadoCarga,false);if(!ocupado)boton(cuerpo,"Reintentar",this::actualizar);return;}
        if(datos.administrador){
            Map<String,String> oficiales=new TreeMap<>();
            for(JSONObject n:datos.notas){
                String cip=PanelDatos.grupo(n),nombre=PanelDatos.valor(n,"oficial_constato");
                oficiales.put(cip,cip.isEmpty()?"Sin CIP asignado":nombre.isEmpty()?cip:nombre+" · "+cip);
            }
            boton(cuerpo,oficial==null?"Todos los oficiales":oficiales.getOrDefault(oficial,"Elegir oficial"),()->{
                List<String> ids=new ArrayList<>(oficiales.keySet()),opciones=new ArrayList<>(oficiales.values()); opciones.add(0,"Todos los oficiales");
                new AlertDialog.Builder(pantalla).setTitle("Filtrar por oficial").setItems(opciones.toArray(new String[0]),(d,i)->{
                    oficial=i==0?null:ids.get(i-1);mostrar=30;dibujar();
                }).show();
            });
        }
        List<JSONObject> asignadas=new ArrayList<>();
        for(JSONObject n:datos.notas)if(!datos.administrador||oficial==null||oficial.equals(PanelDatos.grupo(n)))asignadas.add(n);
        if(!datos.recepcionDisponible)rotulo(cuerpo,"Recepción física pendiente de verificar en la base.",false);
        if(!datos.controlDisponible)rotulo(cuerpo,"La programación y el reporte de entregas necesitan activar el control de entregas en la base.",false);
        if(entregas) entregas(asignadas); else resumen(asignadas);
        boton(cuerpo,"Actualizar panel",this::actualizar);
    }
    private void resumen(List<JSONObject> filas){
        if (oficial == null) {
            boton(cuerpo,"Preparar mi jornada",()->modulo("agente-jornada",null));
            if (datos.administrador) boton(cuerpo,"Parte mensual",()->modulo("agente-parte",null));
        }
        boton(cuerpo,"Periodo · "+periodo,()->new AlertDialog.Builder(pantalla).setTitle("Faltas por fecha de la falta")
                .setItems(new String[]{"Hoy","Este mes","Elegir mes"},(d,i)->{
                    if(i==2){elegirFecha((fecha)->{periodo=fecha.substring(0,7);mostrar=30;dibujar();});return;}
                    periodo=i==0?hoy():hoy().substring(0,7);mostrar=30;dibujar();
                }).show());
        List<JSONObject> casos=new ArrayList<>();Set<String> efectivos=new HashSet<>();int sinCip=0,porEntregar=0;
        for(JSONObject n:filas){
            if(PanelDatos.documentoPendiente(n))porEntregar++;
            if(!vigenteFecha(n))continue; casos.add(n);
            String cip=PanelDatos.valor(n,"investigado_cip");if(cip.isEmpty())sinCip++;else efectivos.add(cip);
        }
        rotulo(cuerpo,casos.size()+" casos de falta · "+efectivos.size()+" efectivos con CIP",true);
        if(sinCip>0)rotulo(cuerpo,sinCip+" casos sin CIP del investigado, pendientes de identificación.",false);
        boton(cuerpo,datos.recepcionDisponible?"Ver entregas abiertas ("+porEntregar+")":"Ver estado de entregas",()->ir("entregas"));
        if(datos.administrador && oficial==null){
            Map<String,int[]> conteos=new LinkedHashMap<>();Map<String,String> nombres=new HashMap<>();
            for(JSONObject n:filas){
                String cip=PanelDatos.grupo(n);
                int[] c=conteos.computeIfAbsent(cip,k->new int[4]);
                if(vigenteFecha(n))c[0]++;
                if(PanelDatos.documentoPendiente(n)){
                    c[1]++;String limite=PanelDatos.valor(n,"fecha_limite");
                    if(!limite.isEmpty()&&limite.compareTo(hoy())<0&&PanelDatos.valor(n,"reportada_at").isEmpty())c[2]++;
                }
                String recibido=PanelDatos.valor(n,"recibido_at");
                if(!recibido.isEmpty()&&vigenteFecha(n))c[3]++;
                nombres.put(cip,PanelDatos.valor(n,"oficial_constato"));
            }
            for(Map.Entry<String,int[]> e:conteos.entrySet()){
                int[] c=e.getValue();if(c[0]+c[1]+c[3]==0)continue;
                String cip=e.getKey(),nombre=nombres.get(cip);
                String rotulo=cip.isEmpty()?"Sin CIP asignado":nombre.isEmpty()?cip:nombre+" · "+cip;
                boton(cuerpo,rotulo+" · "+c[0]+" casos"+(datos.recepcionDisponible?" · "+c[1]+" abiertos · "+c[3]+" recibidos":"")+
                    (datos.controlDisponible&&datos.recepcionDisponible?" · "+c[2]+" vencidos":""),()->{oficial=cip;mostrar=30;dibujar();});
            }
        }
        rotulo(cuerpo,"Casos del periodo",true);lista(casos,false);
    }
    private void entregas(List<JSONObject> filas){
        rotulo(cuerpo,"Pendientes de todos los periodos",true);
        List<JSONObject> abiertas=new ArrayList<>(),recibidas=new ArrayList<>();
        for(JSONObject n:filas){
            if(PanelDatos.documentoPendiente(n))abiertas.add(n);
            else if(!PanelDatos.valor(n,"recibido_at").isEmpty())recibidas.add(n);
        }
        abiertas.sort(Comparator.comparing((JSONObject n)->{
            String f=PanelDatos.valor(n,"fecha_limite");return f.isEmpty()?"9999":f;
        }).thenComparing(n->PanelDatos.valor(n,"fecha_falta")));
        lista(abiertas,true);
        if(datos.recepcionDisponible){
            recibidas.sort((a,b)->PanelDatos.valor(b,"recibido_at").compareTo(PanelDatos.valor(a,"recibido_at")));
            rotulo(cuerpo,"Recepción física confirmada",true);lista(recibidas,true);
            if(datos.administrador)boton(cuerpo,"Abrir recepción de Mesa de Partes",()->modulo("recepcion-fisica",null));
        }
    }
    private void lista(List<JSONObject> filas,boolean entrega){
        if(filas.isEmpty()){rotulo(cuerpo,"Sin registros para esta selección.",false);return;}
        for(JSONObject n:filas.subList(0,Math.min(mostrar,filas.size()))){
            String id=PanelDatos.valor(n,"id");
            rotulo(cuerpo,ExpedientePendiente.nombre(n),true);
            rotulo(cuerpo,"Nota "+PanelDatos.valor(n,"numero_nota_falta")+" · Falta "+fecha(n,"fecha_falta"),false);
            if(entrega){
                rotulo(cuerpo,datos.recepcionDisponible?PanelDatos.documento(n):"Recepción física por verificar",true);
                if(!PanelDatos.valor(n,"recibido_at").isEmpty())rotulo(cuerpo,"Recibido el "+fecha(n,"recibido_at"),false);
                else {
                    String limite=PanelDatos.valor(n,"fecha_limite"),reportada=PanelDatos.valor(n,"reportada_at");
                    if(!reportada.isEmpty())rotulo(cuerpo,"Reportada el "+fecha(n,"reportada_at"),false);
                    rotulo(cuerpo,limite.isEmpty()?"Sin fecha límite registrada":"Fecha límite "+fecha(n,"fecha_limite")+
                        (limite.compareTo(hoy())<0 && reportada.isEmpty()?" · Vencida":""),false);
                    if(datos.controlDisponible && datos.recepcionDisponible && reportada.isEmpty()){
                        if(datos.administrador)boton(cuerpo,"Programar entrega",()->elegirFecha(f->guardar(id,f)));
                        else if(PanelDatos.firmado(n))boton(cuerpo,"Reportar entrega física",()->new AlertDialog.Builder(pantalla)
                            .setMessage("¿Ya entregó el expediente físico en Mesa de Partes? La oficina confirmará su recepción.")
                            .setNegativeButton("Volver",null).setPositiveButton("Reportar",(d,w)->guardar(id,null)).show());
                    }
                }
            }
            boton(cuerpo,"Revisar expediente",()->modulo("agente-expediente",id));
            boton(cuerpo,"Ver expediente",()->modulo("detalle",id));
        }
        if(filas.size()>mostrar)boton(cuerpo,"Ver más",()->{mostrar+=30;dibujar();});
    }
    private interface FechaElegida {void usar(String iso);}
    private void elegirFecha(FechaElegida destino){
        Calendar c=Calendar.getInstance(LIMA);
        new DatePickerDialog(pantalla,(v,a,m,d)->destino.usar(String.format(Locale.ROOT,"%04d-%02d-%02d",a,m+1,d)),
                c.get(Calendar.YEAR),c.get(Calendar.MONTH),c.get(Calendar.DAY_OF_MONTH)).show();
    }
    private void guardar(String id,String fecha){
        if(ocupado)return;ocupado=true;
        trabajo.execute(()->{
            try{
                String token=SesionActual.token(pantalla,usuario);
                JSONObject p=new JSONObject().put("p_nota",id);
                if(fecha!=null)p.put("p_fecha",fecha);
                SupabaseAuth.pedir("POST","/rest/v1/rpc/"+(fecha==null?"reportar_entrega_panel":"programar_entrega_panel"),token,p.toString(),null);
                pantalla.runOnUiThread(()->{if(vigente()){ocupado=false;actualizar();}});
            }catch(Exception e){pantalla.runOnUiThread(()->{if(vigente()){ocupado=false;
                new AlertDialog.Builder(pantalla).setMessage("No se pudo confirmar el registro. Actualice el panel para revisar su estado.")
                    .setPositiveButton("Actualizar",(d,w)->actualizar()).show();
            }});}
        });
    }
    private void politicas(){
        trabajo.execute(()->{
            try{
                String token=SesionActual.token(pantalla,usuario);
                JSONArray docs=SupabaseApi.documentosInstitucionales(token);
                Set<String> firmas=SupabaseApi.misFirmas(token,usuario);
                StringBuilder texto=new StringBuilder();
                for(int i=0;i<docs.length();i++){
                    JSONObject d=docs.getJSONObject(i);String v=d.optString("version");
                    texto.append(d.optString("titulo")).append(" · v").append(v).append(firmas.contains(d.optString("id")+":"+v)?" · Firmada":" · Pendiente")
                            .append("\n").append(d.optString("contenido")).append("\n\n");
                }
                mensaje("Mis políticas",texto.length()==0?"No hay políticas registradas.":texto.toString());
            }catch(Exception e){mensaje("Mis políticas","No se pudieron consultar las políticas.");}
        });
    }
    private void mensaje(String titulo,String texto){
        pantalla.runOnUiThread(()->{if(vigente())new AlertDialog.Builder(pantalla).setTitle(titulo).setMessage(texto).setPositiveButton("Cerrar",null).show();});
    }
}
