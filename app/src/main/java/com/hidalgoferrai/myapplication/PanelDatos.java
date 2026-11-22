package com.hidalgoferrai.myapplication;

import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

final class PanelDatos {
    final List<JSONObject> notas = new ArrayList<>();
    boolean administrador;
    boolean recepcionDisponible = true;
    boolean controlDisponible = true;
    private static final String CAMPOS = "id,oficial_constato,oficial_constato_cip,investigado_cip,grado,apellidos,nombres,"
            + "fecha_falta,numero_nota_falta,codigo_infraccion,fecha_reincorporacion,imputacion_generada_at,"
            + "fecha_descargo,orden_sancion_generada_at,orden_notificada_at,archivo_orden_notificacion_path,archivo_leve_generada_at";

    static String valor(JSONObject n,String campo) {
        return n.isNull(campo) ? "" : n.optString(campo).trim();
    }
    static boolean documentoPendiente(JSONObject n) {
        return !valor(n,"orden_sancion_generada_at").isEmpty() && valor(n,"archivo_leve_generada_at").isEmpty()
                && valor(n,"recibido_at").isEmpty();
    }
    static boolean firmado(JSONObject n) {
        return !valor(n,"archivo_orden_notificacion_path").isEmpty() && !valor(n,"orden_notificada_at").isEmpty();
    }
    static String documento(JSONObject n) {
        if (!valor(n,"recibido_at").isEmpty()) return "Expediente físico recibido";
        if (!valor(n,"reportada_at").isEmpty()) return "Entrega reportada · pendiente de recepción";
        if (firmado(n)) return "Entregar expediente físico en Mesa de Partes";
        if (!valor(n,"orden_notificada_at").isEmpty()) return "Entregar expediente firmado a la oficina";
        return "Notificar y entregar la orden de sanción";
    }
    static String grupo(JSONObject n) {
        return valor(n,"oficial_constato_cip");
    }
    private static boolean esquemaAusente(SupabaseAuth.ErrorApi e) {
        return "42P01".equals(e.codigo) || "PGRST205".equals(e.codigo);
    }
    static PanelDatos cargar(String token, String usuario) throws Exception {
        PanelDatos datos = new PanelDatos();
        datos.administrador = "admin".equals(SupabaseApi.rol(token,usuario));
        String filtro = "";
        if (!datos.administrador) {
            Object cip = new JSONTokener(SupabaseAuth.pedir("POST","/rest/v1/rpc/cip_actual",token,"{}",null)).nextValue();
            if (!(cip instanceof String) || !((String)cip).matches("[0-9]+")) throw new IOException("Cuenta sin CIP habilitado");
            filtro = "&oficial_constato_cip=eq." + Uri.encode((String)cip);
        }
        String cursor = "";
        while (!Thread.currentThread().isInterrupted()) {
            String url = "/rest/v1/notas_informativas?select=" + CAMPOS + filtro + "&order=id.asc&limit=100" + cursor;
            JSONArray pagina = new JSONArray(SupabaseAuth.pedir("GET",url,token,null,null));
            if (pagina.length()==0) break;
            java.util.Map<String,JSONObject> lote = new java.util.LinkedHashMap<>();
            for(int i=0;i<pagina.length();i++) {
                JSONObject n=pagina.getJSONObject(i);
                lote.put(n.getString("id"),n);
                datos.notas.add(n);
            }
            if(datos.recepcionDisponible) datos.recepcionDisponible = unir(token,"recepciones_fisicas","recibido_at",lote);
            if(datos.controlDisponible) datos.controlDisponible = unir(token,"control_entregas","fecha_limite,reportada_at",lote);
            if(pagina.length()<100) break;
            cursor="&id=gt."+Uri.encode(pagina.getJSONObject(pagina.length()-1).getString("id"));
        }
        datos.notas.sort((a,b)->valor(b,"fecha_falta").compareTo(valor(a,"fecha_falta")));
        return datos;
    }
    private static boolean unir(String token,String tabla,String campos,java.util.Map<String,JSONObject> lote) throws Exception {
        String ruta="/rest/v1/"+tabla+"?select=nota_id,"+campos+"&nota_id=in.("+android.text.TextUtils.join(",",lote.keySet())+")";
        try {
            JSONArray filas=new JSONArray(SupabaseAuth.pedir("GET",ruta,token,null,null));
            for(int i=0;i<filas.length();i++) {
                JSONObject f=filas.getJSONObject(i), n=lote.get(f.getString("nota_id"));
                if(n!=null) for(String k:campos.split(",")) n.put(k,f.opt(k));
            }
            return true;
        } catch(SupabaseAuth.ErrorApi e) { if(esquemaAusente(e)) return false; throw e; }
    }
}
