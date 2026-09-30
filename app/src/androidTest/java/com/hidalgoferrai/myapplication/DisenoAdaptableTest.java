package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.res.Configuration;
import android.text.Layout;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** Reproduce una pantalla de 320 dp con texto al 200%, sin tocar ajustes del teléfono. */
@RunWith(AndroidJUnit4.class)
public class DisenoAdaptableTest {
    private Context contexto(boolean oscuro) {
        Context app=InstrumentationRegistry.getInstrumentation().getTargetContext();
        Configuration c=new Configuration(app.getResources().getConfiguration());
        c.fontScale=2f;c.densityDpi=160;c.screenWidthDp=320;c.screenHeightDp=568;
        c.uiMode=(c.uiMode&~Configuration.UI_MODE_NIGHT_MASK)
                |(oscuro?Configuration.UI_MODE_NIGHT_YES:Configuration.UI_MODE_NIGHT_NO);
        return new ContextThemeWrapper(app.createConfigurationContext(c),R.style.Theme_MESADEPARTES);
    }
    private View medir(Context c,int recurso,int alto) {
        View v=LayoutInflater.from(c).inflate(recurso,null,false);
        medir(v,alto);return v;
    }
    private void medir(View v,int alto) {
        v.measure(View.MeasureSpec.makeMeasureSpec(320,View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(alto,View.MeasureSpec.EXACTLY));
        v.layout(0,0,320,alto);
    }
    private void sinRecortes(View v) {
        if(v.getVisibility()!=View.VISIBLE)return;
        if(v instanceof TextView){
            TextView t=(TextView)v;Layout l=t.getLayout();
            if(l!=null&&t.length()>0){
                String id=t.getId()==View.NO_ID?t.getText().toString():t.getResources().getResourceEntryName(t.getId());
                int n=l.getLineCount();
                assertTrue(id+": sin líneas",n>0);
                for(int i=0;i<n;i++)assertEquals(id+": texto abreviado",0,l.getEllipsisCount(i));
                assertEquals(id+": texto incompleto",l.getText().length(),l.getLineEnd(n-1));
                assertTrue(id+": recorte vertical",l.getLineBottom(n-1)<=t.getHeight()-t.getCompoundPaddingTop()-t.getCompoundPaddingBottom());
            }
        }
        if(v instanceof ViewGroup){
            ViewGroup g=(ViewGroup)v;
            for(int i=0;i<g.getChildCount();i++)sinRecortes(g.getChildAt(i));
        }
    }
    @Test public void accesoYSubidaConLetraGrandeNoRecortan() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            for(boolean dark:new boolean[]{false,true})for(int recurso:new int[]{R.layout.activity_login,R.layout.activity_expediente})
                sinRecortes(medir(contexto(dark),recurso,568));
        });
    }
    @Test public void tarjetasNoTruncanTitulosLargos() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            View v=LayoutInflater.from(contexto(true)).inflate(R.layout.item_acceso,null,false);
            ((TextView)v.findViewById(R.id.etiqueta)).setText(R.string.inicio_recepcionar);
            ((TextView)v.findViewById(R.id.detalle)).setText("Revise el expediente completo y confirme su recepción física.");
            v.measure(View.MeasureSpec.makeMeasureSpec(284,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
            v.layout(0,0,284,v.getMeasuredHeight());sinRecortes(v);
        });
    }
    @Test public void tokenPermiteLlegarACerrarEnPantallaBaja() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
            View v=medir(contexto(true),R.layout.sheet_token,280);
            ((TextView)v.findViewById(R.id.tvCodigoInferior)).setText("123 456");
            ((TextView)v.findViewById(R.id.tvTiempoInferior)).setText(R.string.token_sin_local);
            v.findViewById(R.id.btnVerificarInferior).setVisibility(View.VISIBLE);medir(v,280);
            sinRecortes(v);
            assertTrue("El panel debe poder desplazarse",v instanceof android.widget.ScrollView);
            assertTrue("Cerrar queda accesible al desplazar",v.canScrollVertically(1));
        });
    }
}
