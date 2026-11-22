package com.hidalgoferrai.myapplication;

import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.card.MaterialCardView;
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
    private LinearLayout filtros;
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
    private int dp(int valor){return Math.round(valor*pantalla.getResources().getDisplayMetrics().density);}
    private int color(int id){return pantalla.getColor(id);}
    private static TextView rotulo(LinearLayout zona,String texto,boolean titulo){
        TextView v=new TextView(zona.getContext()); v.setText(texto); v.setTextSize(titulo?16:14);
        v.setTextColor(zona.getContext().getColor(titulo?R.color.texto_principal:R.color.texto_secundario));
        if(titulo)v.setTypeface(null,Typeface.BOLD);
        int pad=Math.round(3*zona.getResources().getDisplayMetrics().density);
        v.setPadding(0,pad,0,pad); zona.addView(v); return v;
    }
    /** Recuadro igual a las tarjetas del inicio: cada grupo de datos va en el suyo. */
    private LinearLayout tarjeta(LinearLayout zona){
        MaterialCardView c=new MaterialCardView(pantalla);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.setMargins(dp(6),dp(6),dp(6),dp(6));
        c.setLayoutParams(lp); c.setRadius(dp(20)); c.setCardElevation(0);
        c.setCardBackgroundColor(color(R.color.superficie_tarjeta));
        c.setStrokeColor(color(R.color.borde_suave)); c.setStrokeWidth(dp(1));
        LinearLayout dentro=new LinearLayout(pantalla); dentro.setOrientation(LinearLayout.VERTICAL);
        dentro.setPadding(dp(16),dp(14),dp(16),dp(14)); c.addView(dentro); zona.addView(c); return dentro;
    }
    private void seccion(String texto){
        TextView v=new TextView(pantalla); v.setText(texto); v.setTextSize(13); v.setAllCaps(true); v.setLetterSpacing(0.06f);
        v.setTypeface(null,Typeface.BOLD); v.setTextColor(color(R.color.dorado)); v.setPadding(dp(10),dp(18),dp(10),dp(4));
        cuerpo.addView(v);
    }
    private LinearLayout fila(LinearLayout zona){
        LinearLayout f=new LinearLayout(pantalla); f.setOrientation(LinearLayout.HORIZONTAL); f.setGravity(Gravity.CENTER_VERTICAL);
        zona.addView(f,new LinearLayout.LayoutParams(-1,-2)); return f;
    }
    private MaterialButton boton(LinearLayout zona,String texto,Runnable accion){
        MaterialButton b=new MaterialButton(pantalla,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
        b.setText(texto); b.setAllCaps(false); b.setCornerRadius(dp(16)); b.setMinHeight(dp(48));
        b.setStrokeColor(ColorStateList.valueOf(color(R.color.borde_suave)));
        b.setOnClickListener(v->accion.run()); zona.addView(b); return b;
    }
    private MaterialButton principal(LinearLayout zona,String texto,Runnable accion){
        MaterialButton b=new MaterialButton(pantalla); b.setText(texto); b.setAllCaps(false); b.setCornerRadius(dp(16));
        b.setOnClickListener(v->accion.run()); zona.addView(b); return b;
    }
    private MaterialButton enlace(LinearLayout zona,String texto,Runnable accion){
        MaterialButton b=new MaterialButton(pantalla,null,androidx.appcompat.R.attr.borderlessButtonStyle);
        b.setText(texto); b.setAllCaps(false); b.setOnClickListener(v->accion.run()); zona.addView(b); return b;
    }
    /** Dos botones que se reparten el ancho de la fila. */
    private static void repartir(MaterialButton... botones){
        for(int i=0;i<botones.length;i++){
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
            lp.setMarginStart(i==0?0:Math.round(8*botones[i].getResources().getDisplayMetrics().density));
            botones[i].setLayoutParams(lp);
        }
    }
    /** Fila tocable con su carpeta: icono, título y detalle, como un acceso del inicio. */
    private void carpeta(LinearLayout zona,String titulo,String detalle,Runnable accion){
        LinearLayout f=fila(zona); f.setPadding(0,dp(10),0,dp(10));
        android.util.TypedValue fondo=new android.util.TypedValue();
        pantalla.getTheme().resolveAttribute(android.R.attr.selectableItemBackground,fondo,true);
        f.setBackgroundResource(fondo.resourceId); f.setOnClickListener(v->accion.run());
        FrameLayout marco=new FrameLayout(pantalla); marco.setBackgroundResource(R.drawable.bg_icon_tile);
        ImageView icono=new ImageView(pantalla); icono.setImageResource(R.drawable.ic_expediente); icono.setContentDescription(null);
        marco.addView(icono,new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER));
        f.addView(marco,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout textos=new LinearLayout(pantalla); textos.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1); lp.setMarginStart(dp(12));
        f.addView(textos,lp);
        rotulo(textos,titulo,true); rotulo(textos,detalle,false);
        ImageView flecha=new ImageView(pantalla); flecha.setImageResource(R.drawable.ic_arrow_forward);
        flecha.setImageTintList(ColorStateList.valueOf(color(R.color.dorado))); flecha.setContentDescription(null);
        f.addView(flecha,new LinearLayout.LayoutParams(dp(18),dp(18)));
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
        MaterialButtonToggleGroup pestanas=new MaterialButtonToggleGroup(pantalla);
        pestanas.setSingleSelection(true); pestanas.setSelectionRequired(true);
        LinearLayout.LayoutParams lpPestanas=new LinearLayout.LayoutParams(-1,-2); lpPestanas.setMargins(dp(6),dp(12),dp(6),dp(6));
        menu.addView(pestanas,lpPestanas);
        for(String[] item:new String[][]{{"panel","Panel"},{"entregas","Entregas"},{"cuenta","Cuenta"}}){
            MaterialButton b=new MaterialButton(pantalla,null,com.google.android.material.R.attr.materialButtonOutlinedStyle);
            b.setId(View.generateViewId()); b.setText(item[1]); b.setAllCaps(false); b.setCheckable(true);
            b.setOnClickListener(v->ir(item[0]));
            pestanas.addView(b,new LinearLayout.LayoutParams(0,-2,1));
            if(item[0].equals(pestana))pestanas.check(b.getId());
        }
        pantalla.findViewById(R.id.panelCuenta).setVisibility(cuenta?View.VISIBLE:View.GONE);
        // Los accesos con su carpeta (subir, recepcionar, seguimiento…) encabezan el panel.
        pantalla.findViewById(R.id.grid).setVisibility(!cuenta && !entregas?View.VISIBLE:View.GONE);
        ((TextView)pantalla.findViewById(R.id.tvPanelTitulo)).setText(cuenta?"Mi cuenta":entregas?"Entregas":
            (datos!=null?datos.administrador:Perfil.esAdministrador(pantalla))?"Panel del administrador":"Mi panel");
        if(cuenta){
            LinearLayout t=tarjeta(cuerpo);
            rotulo(t,"Políticas y firmas",true);
            rotulo(t,"Revise las políticas vigentes y si ya las firmó.",false);
            boton(t,"Consultar políticas y firmas",this::politicas);
            return;
        }
        if(datos==null){
            LinearLayout t=tarjeta(cuerpo); rotulo(t,estadoCarga,false);
            if(!ocupado)principal(t,"Reintentar",this::actualizar);
            return;
        }
        filtros=null;
        if(datos.administrador){
            Map<String,String> oficiales=new TreeMap<>();
            for(JSONObject n:datos.notas){
                String cip=PanelDatos.grupo(n),nombre=PanelDatos.valor(n,"oficial_constato");
                oficiales.put(cip,cip.isEmpty()?"Sin CIP asignado":nombre.isEmpty()?cip:nombre+" · "+cip);
            }
            filtro(oficial==null?"Todos los oficiales":oficiales.getOrDefault(oficial,"Elegir oficial"),()->{
                List<String> ids=new ArrayList<>(oficiales.keySet()),opciones=new ArrayList<>(oficiales.values()); opciones.add(0,"Todos los oficiales");
                new AlertDialog.Builder(pantalla).setTitle("Filtrar por oficial").setItems(opciones.toArray(new String[0]),(d,i)->{
                    oficial=i==0?null:ids.get(i-1);mostrar=30;dibujar();
                }).show();
            });
        }
        List<JSONObject> asignadas=new ArrayList<>();
        for(JSONObject n:datos.notas)if(!datos.administrador||oficial==null||oficial.equals(PanelDatos.grupo(n)))asignadas.add(n);
        if(!datos.recepcionDisponible || !datos.controlDisponible){
            LinearLayout aviso=tarjeta(cuerpo);
            if(!datos.recepcionDisponible)rotulo(aviso,"Recepción física pendiente de verificar en la base.",false);
            if(!datos.controlDisponible)rotulo(aviso,"La programación y el reporte de entregas necesitan activar el control de entregas en la base.",false);
        }
        if(entregas) entregas(asignadas); else resumen(asignadas);
        enlace(cuerpo,"Actualizar panel",this::actualizar);
    }
    /** Los filtros (oficial y periodo) comparten una fila sobre el resumen. */
    private void filtro(String texto,Runnable accion){
        if(filtros==null){
            filtros=fila(cuerpo);
            ((LinearLayout.LayoutParams)filtros.getLayoutParams()).setMargins(dp(6),dp(4),dp(6),dp(4));
        }
        MaterialButton b=boton(filtros,texto,accion); b.setMaxLines(2); b.setTextSize(13);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);
        if(filtros.getChildCount()>1)lp.setMarginStart(dp(8));
        b.setLayoutParams(lp);
    }
    private void resumen(List<JSONObject> filas){
        filtro("Periodo · "+periodo,()->new AlertDialog.Builder(pantalla).setTitle("Faltas por fecha de la falta")
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
        LinearLayout total=tarjeta(cuerpo);
        rotulo(total,casos.size()+" casos de falta · "+efectivos.size()+" efectivos con CIP",true).setTextSize(18);
        if(sinCip>0)rotulo(total,sinCip+" casos sin CIP del investigado, pendientes de identificación.",false);
        principal(total,datos.recepcionDisponible?"Ver entregas abiertas ("+porEntregar+")":"Ver estado de entregas",()->ir("entregas"));
        if (oficial == null) {
            seccion("Asistentes");
            LinearLayout asistentes=fila(tarjeta(cuerpo));
            MaterialButton jornada=boton(asistentes,"Preparar mi jornada",()->modulo("agente-jornada",null));
            if (datos.administrador) repartir(jornada,boton(asistentes,"Parte mensual",()->modulo("agente-parte",null)));
            else repartir(jornada);
        }
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
            LinearLayout carpetas=null;
            for(Map.Entry<String,int[]> e:conteos.entrySet()){
                int[] c=e.getValue();if(c[0]+c[1]+c[3]==0)continue;
                if(carpetas==null){seccion("Por oficial");carpetas=tarjeta(cuerpo);}
                String cip=e.getKey(),nombre=nombres.get(cip);
                String titulo=cip.isEmpty()?"Sin CIP asignado":nombre.isEmpty()?cip:nombre+" · "+cip;
                carpeta(carpetas,titulo,c[0]+" casos"+(datos.recepcionDisponible?" · "+c[1]+" abiertos · "+c[3]+" recibidos":"")+
                    (datos.controlDisponible&&datos.recepcionDisponible?" · "+c[2]+" vencidos":""),()->{oficial=cip;mostrar=30;dibujar();});
            }
        }
        seccion("Casos del periodo");lista(casos,false);
    }
    private void entregas(List<JSONObject> filas){
        seccion("Pendientes de todos los periodos");
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
            seccion("Recepción física confirmada");lista(recibidas,true);
            if(datos.administrador)principal(tarjeta(cuerpo),"Abrir recepción de Mesa de Partes",()->modulo("recepcion-fisica",null));
        }
    }
    private void lista(List<JSONObject> filas,boolean entrega){
        if(filas.isEmpty()){rotulo(tarjeta(cuerpo),"Sin registros para esta selección.",false);return;}
        for(JSONObject n:filas.subList(0,Math.min(mostrar,filas.size()))){
            String id=PanelDatos.valor(n,"id");
            LinearLayout caso=tarjeta(cuerpo);
            rotulo(caso,ExpedientePendiente.nombre(n),true);
            rotulo(caso,"Nota "+PanelDatos.valor(n,"numero_nota_falta")+" · Falta "+fecha(n,"fecha_falta"),false);
            if(entrega){
                rotulo(caso,datos.recepcionDisponible?PanelDatos.documento(n):"Recepción física por verificar",true).setTextColor(color(R.color.verde_pnp));
                if(!PanelDatos.valor(n,"recibido_at").isEmpty())rotulo(caso,"Recibido el "+fecha(n,"recibido_at"),false);
                else {
                    String limite=PanelDatos.valor(n,"fecha_limite"),reportada=PanelDatos.valor(n,"reportada_at");
                    if(!reportada.isEmpty())rotulo(caso,"Reportada el "+fecha(n,"reportada_at"),false);
                    boolean vencida=!limite.isEmpty() && limite.compareTo(hoy())<0 && reportada.isEmpty();
                    TextView plazo=rotulo(caso,limite.isEmpty()?"Sin fecha límite registrada":"Fecha límite "+fecha(n,"fecha_limite")+(vencida?" · Vencida":""),false);
                    if(vencida){plazo.setTextColor(color(R.color.texto_alerta));plazo.setTypeface(null,Typeface.BOLD);}
                    if(datos.controlDisponible && datos.recepcionDisponible && reportada.isEmpty()){
                        if(datos.administrador)principal(caso,"Programar entrega",()->elegirFecha(f->guardar(id,f)));
                        else if(PanelDatos.firmado(n))principal(caso,"Reportar entrega física",()->new AlertDialog.Builder(pantalla)
                            .setMessage("¿Ya entregó el expediente físico en Mesa de Partes? La oficina confirmará su recepción.")
                            .setNegativeButton("Volver",null).setPositiveButton("Reportar",(d,w)->guardar(id,null)).show());
                    }
                }
            }
            LinearLayout acciones=fila(caso); acciones.setPadding(0,dp(6),0,0);
            MaterialButton revisar=boton(acciones,"Revisar expediente",()->modulo("agente-expediente",id));
            MaterialButton ver=boton(acciones,"Ver expediente",()->modulo("detalle",id));
            revisar.setTextSize(13); ver.setTextSize(13); repartir(revisar,ver);
        }
        if(filas.size()>mostrar)enlace(cuerpo,"Ver más",()->{mostrar+=30;dibujar();});
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
