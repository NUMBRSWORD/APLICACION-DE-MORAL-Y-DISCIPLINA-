package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.Gravity;
import android.graphics.drawable.GradientDrawable;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Locale;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SeguimientoActivity extends AppCompatActivity {
    private final ExecutorService hilo=Executors.newSingleThreadExecutor();
    private final ArrayList<JSONObject> notas=new ArrayList<>();
    private boolean cargando;
    private int offset;
    private LinearLayout lista;
    private TextInputEditText buscar;
    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved); EdgeToEdge.enable(this); setContentView(R.layout.activity_seguimiento);
        ((TextView)findViewById(R.id.tvModuloTitulo)).setText(R.string.acceso_seguimiento);
        findViewById(R.id.btnAtras).setOnClickListener(v->finish());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main),(v,i)->{
            Insets b=i.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.ime());
            v.setPadding(b.left,b.top,b.right,b.bottom);return i;
        });
        lista=findViewById(R.id.listaPendientes); buscar=findViewById(R.id.etBuscarPendientes);
        buscar.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence s,int a,int c,int d){}
            public void onTextChanged(CharSequence s,int a,int b,int c){ pintar(); }
            public void afterTextChanged(Editable e){}
        });
        findViewById(R.id.btnReintentar).setOnClickListener(v->cargar(false));
        findViewById(R.id.btnActualizarPendientes).setOnClickListener(v->cargar(false));
        findViewById(R.id.btnMasPendientes).setOnClickListener(v->cargar(true));
    }
    @Override protected void onResume(){super.onResume();cargar(false);}
    private void cargar(boolean mas) {
        if(cargando)return;cargando=true;
        findViewById(R.id.progreso).setVisibility(View.VISIBLE);
        findViewById(R.id.estadoPanel).setVisibility(View.GONE);
        findViewById(R.id.tvPendientesVacio).setVisibility(View.GONE);
        findViewById(R.id.btnMasPendientes).setEnabled(false);
        final int desde=mas?offset:0;
        hilo.execute(()->{
            try {
                String token=new JSONObject(SesionActual.obtener(this)).getString("access_token");
                JSONArray filas=SupabaseApi.pendientes(token,desde);
                runOnUiThread(()->{
                    if(isDestroyed())return;
                    if(!mas)notas.clear();
                    for(int j=0;j<filas.length();j++) {
                        JSONObject n=filas.optJSONObject(j);
                        if(n!=null&&ExpedientePendiente.pendiente(n))notas.add(n);
                    }
                    offset=desde+filas.length(); cargando=false;
                    findViewById(R.id.progreso).setVisibility(View.GONE);
                    findViewById(R.id.btnMasPendientes).setEnabled(true);
                    findViewById(R.id.btnMasPendientes).setVisibility(filas.length()==100?View.VISIBLE:View.GONE);
                    pintar();
                });
            }catch(Exception e){runOnUiThread(()->{
                if(isDestroyed())return;cargando=false;
                findViewById(R.id.progreso).setVisibility(View.GONE);
                Diseno.error(this,R.string.modulo_error,Errores.mensaje(e),true);
            });}
        });
    }
    private void pintar() {
        lista.removeAllViews();
        String q=buscar.getText()==null?"":buscar.getText().toString().toLowerCase(Locale.ROOT).trim();
        ((TextView)findViewById(R.id.tvCantidadPendientes)).setText(getString(R.string.seguimiento_contador,notas.size()));
        int visibles=0;
        for(JSONObject n:notas){
            String nombre=ExpedientePendiente.nombre(n);
            if(!(nombre+" "+n.optString("numero_nota_falta")).toLowerCase(Locale.ROOT).contains(q))continue;
            visibles++;
            MaterialCardView tarjeta=new MaterialCardView(this);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(14);tarjeta.setLayoutParams(lp);
            LinearLayout caja=new LinearLayout(this);caja.setOrientation(LinearLayout.VERTICAL);caja.setPadding(dp(18),dp(18),dp(18),dp(16));
            caja.addView(texto(nombre,18,true));
            caja.addView(texto(getString(R.string.seguimiento_nota,n.optString("numero_nota_falta","—"),n.optString("fecha_falta","—")),13,false));
            pintarPasos(caja, n);
            TextView estado=texto(getString(ExpedientePendiente.siguiente(n)),14,true);
            estado.setTextColor(getColor(R.color.verde_pnp));caja.addView(estado);
            MaterialButton abrir=new MaterialButton(this);abrir.setText(R.string.seguimiento_abrir);
            abrir.setOnClickListener(v->startActivity(new Intent(this,WebActivity.class)
                    .putExtra(WebActivity.EXTRA_VISTA,"detalle").putExtra(WebActivity.EXTRA_NOTA_ID,n.optString("id"))));
            caja.addView(abrir);tarjeta.addView(caja);lista.addView(tarjeta);
        }
        TextView vacio=findViewById(R.id.tvPendientesVacio);
        vacio.setVisibility(!cargando&&visibles==0?View.VISIBLE:View.GONE);
        vacio.setText(q.isEmpty()?R.string.seguimiento_vacio:R.string.seguimiento_sin_coincidencias);
    }
    private void pintarPasos(LinearLayout caja, JSONObject nota) {
        List<ExpedientePendiente.Paso> pasos = ExpedientePendiente.pasos(nota);
        int hechos = 0;
        for (ExpedientePendiente.Paso paso : pasos)
            if (paso.estado == ExpedientePendiente.Estado.COMPLETADO) hechos++;
        caja.addView(texto(getString(R.string.avance_titulo),16,true));
        caja.addView(texto(getString(R.string.avance_resumen,hechos,pasos.size()),13,false));
        for (int i=0; i<pasos.size(); i++) {
            ExpedientePendiente.Paso paso = pasos.get(i);
            boolean hecho = paso.estado == ExpedientePendiente.Estado.COMPLETADO;
            boolean actual = paso.estado == ExpedientePendiente.Estado.SIGUIENTE;
            int textoEstado = R.string.avance_pendiente;
            if (hecho) textoEstado = R.string.avance_hecho;
            else if (actual) textoEstado = R.string.avance_siguiente;
            else if (paso.estado == ExpedientePendiente.Estado.SIN_REGISTRO) textoEstado = R.string.avance_sin_registro;
            else if (paso.estado == ExpedientePendiente.Estado.POR_REVISAR) textoEstado = R.string.avance_por_revisar;
            String estado = getString(textoEstado);
            if (hecho && paso.fecha.matches("\\d{4}-\\d{2}-\\d{2}.*")) {
                String f=paso.fecha.substring(0,10);
                estado=getString(R.string.avance_fecha,f.substring(8)+"/"+f.substring(5,7)+"/"+f.substring(0,4));
            }
            LinearLayout fila = new LinearLayout(this);
            fila.setOrientation(LinearLayout.HORIZONTAL);
            fila.setGravity(Gravity.TOP);
            fila.setPadding(0,dp(4),0,dp(8));
            fila.setContentDescription(getString(R.string.avance_lectura,i+1,getString(paso.titulo),estado));
            fila.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            TextView punto = new TextView(this);
            punto.setText(hecho?"✓":String.valueOf(i+1));punto.setTextSize(14);punto.setGravity(Gravity.CENTER);
            punto.setTypeface(null,android.graphics.Typeface.BOLD);
            punto.setTextColor(getColor(hecho?R.color.sobre_primario:actual?R.color.texto_alerta:R.color.texto_secundario));
            punto.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            GradientDrawable fondo = new GradientDrawable();fondo.setShape(GradientDrawable.OVAL);
            fondo.setColor(getColor(hecho?R.color.verde_pnp:actual?R.color.estado_alerta:R.color.superficie_elevada));
            fondo.setStroke(dp(1),getColor(hecho?R.color.verde_pnp:actual?R.color.dorado:R.color.borde_suave));
            punto.setBackground(fondo);fila.addView(punto,new LinearLayout.LayoutParams(dp(30),dp(30)));
            LinearLayout etiquetas = new LinearLayout(this);etiquetas.setOrientation(LinearLayout.VERTICAL);
            etiquetas.setPadding(dp(12),0,0,0);
            etiquetas.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            TextView titulo=texto(getString(paso.titulo),14,true);titulo.setPadding(0,0,0,dp(2));
            TextView detalle=texto(estado,12,false);detalle.setPadding(0,0,0,dp(5));
            if (hecho) detalle.setTextColor(getColor(R.color.verde_pnp));
            etiquetas.addView(titulo);etiquetas.addView(detalle);
            fila.addView(etiquetas,new LinearLayout.LayoutParams(0,-2,1));caja.addView(fila);
        }
    }
    private TextView texto(String valor,int tam,boolean fuerte){
        TextView t=new TextView(this);t.setText(valor);t.setTextSize(tam);t.setTextColor(getColor(fuerte?R.color.texto_principal:R.color.texto_secundario));
        if(fuerte)t.setTypeface(null,android.graphics.Typeface.BOLD);
        t.setPadding(0,0,0,dp(10));return t;
    }
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy(){hilo.shutdown();super.onDestroy();}
}
