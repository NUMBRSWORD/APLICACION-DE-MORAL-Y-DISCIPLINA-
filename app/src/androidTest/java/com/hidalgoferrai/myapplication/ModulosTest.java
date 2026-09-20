package com.hidalgoferrai.myapplication;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.provider.MediaStore;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebView;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.ColorUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;

@RunWith(AndroidJUnit4.class)
public class ModulosTest {
    private Context contexto;
    private SupabaseAuth.Transporte original;
    private final ArrayList<Uri> archivos = new ArrayList<>();
    private volatile String consulta;
    private volatile boolean fallo;
    private static final String SESION = "{\"access_token\":\"ficticio-modulos\",\"refresh_token\":\"refresco-ficticio\",\"user\":{\"id\":\"demo-modulos\"}}";
    private static final String FILAS = "[{\"id\":\"nota-demo\",\"grado\":\"S1 PNP\",\"apellidos\":\"DEMOSTRACIÓN\",\"nombres\":\"ANA\",\"numero_nota_falta\":\"001\",\"fecha_falta\":\"2026-09-18\",\"codigo_infraccion\":\"L21\",\"fecha_reincorporacion\":\"2026-09-19\",\"imputacion_generada_at\":\"2026-09-19T10:00:00Z\"},"
            + "{\"id\":\"cerrada\",\"orden_notificada_at\":\"2026-09-19\"},{\"id\":\"archivada\",\"archivo_leve_generada_at\":\"2026-09-19\"}]";
    @Before public void preparar() throws Exception {
        contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(contexto.getPackageName().endsWith(".qa"));
        SesionActual.borrar(); AlmacenSeguro.borrar(contexto); AlmacenSeguro.borrarRefresco(contexto);
        Perfil.guardar(contexto,"demo-modulos","S1 PNP","Cuenta Demostración","admin");
        SesionActual.recibir(contexto,SESION);
        original = SupabaseAuth.transporte;
        SupabaseAuth.transporte = (metodo,ruta,token,cuerpo,preferencia) -> {
            if(ruta.startsWith("/rest/v1/notas_informativas?")) {
                assertEquals("GET",metodo); consulta=ruta;
                if(fallo)throw new java.net.SocketTimeoutException("simulado");
                return FILAS;
            }
            throw new IOException("Solicitud no prevista en prueba aislada");
        };
        WebActivity.htmlPrueba = asset("modulos.html");
        WebActivity.moduloPrueba = asset("modulos.js");
    }
    @After public void cerrar() {
        main(() -> {
            for(Stage etapa: new Stage[]{Stage.RESUMED,Stage.STARTED,Stage.CREATED,Stage.STOPPED})
                for(Activity a:new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(etapa))) a.finish();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SupabaseAuth.transporte=original; SesionActual.borrar();
        WebActivity.htmlPrueba=null; WebActivity.moduloPrueba=null;
        for(Uri archivo:archivos)contexto.getContentResolver().delete(archivo,null,null);
    }
    private String asset(String nombre) throws IOException {
        try(InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(nombre);
            ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);
            return out.toString("UTF-8");
        }
    }
    private void main(Runnable r){InstrumentationRegistry.getInstrumentation().runOnMainSync(r);}
    private void esperar(int id, String texto) throws Exception {
        long limite=System.currentTimeMillis()+12000;
        do {
            boolean[] ok={false};main(()->{
                for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    View v=a.findViewById(id);
                    if(v!=null&&v.isShown()&&(texto==null||v instanceof TextView&&((TextView)v).getText().toString().contains(texto)))ok[0]=true;
                }
            });
            if(ok[0])return;Thread.sleep(80);
        }while(System.currentTimeMillis()<limite);
        fail("No apareció la vista de prueba: "+id);
    }
    private String js(ActivityScenario<WebActivity> escenario,String codigo) throws Exception {
        CountDownLatch latch=new CountDownLatch(1);String[] valor={null};
        escenario.onActivity(a->((WebView)a.findViewById(R.id.webView)).evaluateJavascript(codigo,v->{valor[0]=v;latch.countDown();}));
        assertTrue(latch.await(4,TimeUnit.SECONDS));return valor[0];
    }
    private Intent modulo(String vista){return new Intent(contexto,WebActivity.class).putExtra(WebActivity.EXTRA_VISTA,vista);}
    private void captura(String nombre) {
        main(()->{
            for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                if(a instanceof TokenActivity || (a.getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0)continue;
                if(a instanceof InicioActivity && ((InicioActivity)a).getSupportFragmentManager().findFragmentByTag("token-inferior")!=null)continue;
                View v=a.getWindow().getDecorView();Bitmap b=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(b));
                try(OutputStream out=new FileOutputStream(new File(contexto.getExternalFilesDir(null),nombre))){b.compress(Bitmap.CompressFormat.PNG,100,out);}
                catch(IOException e){throw new AssertionError(e);}finally{b.recycle();}
            }
        });
    }
    private Uri pdf(boolean valido) throws Exception {
        ContentValues values=new ContentValues();values.put(MediaStore.Downloads.DISPLAY_NAME,"expediente-demo-qa.pdf");
        values.put(MediaStore.Downloads.MIME_TYPE,"application/pdf");
        Uri uri=contexto.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,values);assertNotNull(uri);archivos.add(uri);
        try(OutputStream out=contexto.getContentResolver().openOutputStream(uri)){
            if(valido){
                PdfDocument doc=new PdfDocument();PdfDocument.Page page=doc.startPage(new PdfDocument.PageInfo.Builder(400,600,1).create());
                page.getCanvas().drawText("DOCUMENTO FICTICIO DE PRUEBA",30,40,new android.graphics.Paint());
                doc.finishPage(page);doc.writeTo(out);doc.close();
            }else out.write("No es un PDF".getBytes(StandardCharsets.UTF_8));
        }
        return uri;
    }
    @Test public void inicioAbreCargaPropiaYTokenDesdeAbajo() throws Exception {
        try(ActivityScenario<InicioActivity> s=ActivityScenario.launch(InicioActivity.class)) {
            esperar(R.id.btnMiToken,null);captura("inicio-modulos.png");
            s.onActivity(a->{
                View footer=a.findViewById(R.id.btnMiToken);View root=a.findViewById(R.id.main);
                assertTrue(footer.getTop()>root.getHeight()*0.65);
            });
            onView(withId(R.id.btnMiToken)).perform(click());
            s.onActivity(a->{
                TokenInferior panel=(TokenInferior)a.getSupportFragmentManager().findFragmentByTag("token-inferior");
                assertNotNull(panel);assertNotNull(panel.getDialog());
                assertTrue((panel.getDialog().getWindow().getAttributes().flags&WindowManager.LayoutParams.FLAG_SECURE)!=0);
                panel.dismissNow();
            });
            // La animación de salida de la ventana del diálogo puede absorber el
            // siguiente toque aunque el fragmento ya esté eliminado.
            Thread.sleep(400);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            onView(withText(R.string.subir_expediente)).perform(scrollTo(),click());
            esperar(R.id.btnEscanear,null);esperar(R.id.btnSeleccionarPdf,null);captura("subir-modulos.png");
        }
    }
    @Test public void seguimientoSoloPendientesYBusqueda() throws Exception {
        try(ActivityScenario<SeguimientoActivity> s=ActivityScenario.launch(SeguimientoActivity.class)) {
            esperar(R.id.tvCantidadPendientes,"1");
            assertTrue(consulta.contains("orden_notificada_at=is.null&archivo_leve_generada_at=is.null"));
            s.onActivity(a->assertEquals(1,((LinearLayout)a.findViewById(R.id.listaPendientes)).getChildCount()));
            captura("seguimiento-modulos.png");
            onView(withId(R.id.etBuscarPendientes)).perform(replaceText("no-existe"),closeSoftKeyboard());
            esperar(R.id.tvPendientesVacio,"No hay coincidencias");
        }
    }
    @Test public void errorDeSeguimientoNoSeConfundeConVacio() throws Exception {
        fallo=true;
        try(ActivityScenario<SeguimientoActivity> s=ActivityScenario.launch(SeguimientoActivity.class)){
            esperar(R.id.estadoPanel,null);
            s.onActivity(a->{assertEquals(View.GONE,a.findViewById(R.id.progreso).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.tvPendientesVacio).getVisibility());});
            fallo=false;onView(withId(R.id.btnReintentar)).perform(scrollTo(),click());esperar(R.id.tvCantidadPendientes,"1");
        }
    }
    @Test public void cadaModuloConservaDestinoInclusoAlRenovarSesion() throws Exception {
        for(String vista:new String[]{"cumplimiento","seguimiento","efectivos","recepcion"}) {
            try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo(vista))){
                esperar(R.id.webView,null);Thread.sleep(1400);
                assertEquals("true",js(s,"window.bootHasSession"));
                assertEquals("true",js(s,"!document.getElementById('view-"+vista+"').classList.contains('hidden')"));
                assertEquals("true",js(s,"document.getElementById('view-dashboard').classList.contains('hidden')"));
                s.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.cargaModulo).getVisibility()));
            }
        }
    }
    @Test public void detalleAbreNotaElegidaNoInicio() throws Exception {
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("detalle").putExtra(WebActivity.EXTRA_NOTA_ID,"nota-demo"))){
            esperar(R.id.webView,null);assertEquals("\"nota-demo\"",js(s,"window.openedNote"));
        }
    }
    @Test public void pdfPreparadoSeEntregaARevisionSinRegistrar() throws Exception {
        Uri archivo=pdf(true);
        try(ActivityScenario<ExpedienteActivity> s=ActivityScenario.launch(ExpedienteActivity.class)) {
            s.onActivity(a->a.prepararPdf(archivo));esperar(R.id.tarjetaPdf,null);captura("pdf-preparado.png");
            s.recreate();esperar(R.id.tarjetaPdf,null);
        }
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("registro")
                .putExtra(WebActivity.EXTRA_DOCUMENTO,archivo.toString()).putExtra(WebActivity.EXTRA_NOMBRE_DOCUMENTO,"expediente-demo-qa.pdf"))){
            esperar(R.id.webView,null);
            assertEquals("\"expediente-demo-qa.pdf\"",js(s,"window.chosenName"));
            assertEquals("false",js(s,"window.submitted"));
        }
    }
    @Test public void archivoInvalidoNoPermiteContinuar() throws Exception {
        Uri archivo=pdf(false);
        try(ActivityScenario<ExpedienteActivity> s=ActivityScenario.launch(ExpedienteActivity.class)){
            s.onActivity(a->a.prepararPdf(archivo));esperar(R.id.estadoPanel,null);
            s.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.tarjetaPdf).getVisibility()));
        }
    }
    @Test public void moduloAusenteMuestraErrorNoInicioGenerico() throws Exception {
        WebActivity.htmlPrueba=WebActivity.htmlPrueba.replace("data-view=\"cumplimiento\"","data-view=\"no-disponible\"");
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("cumplimiento"))){
            esperar(R.id.estadoPanel,null);
            s.onActivity(a->{assertEquals(View.INVISIBLE,a.findViewById(R.id.webView).getVisibility());assertEquals(View.GONE,a.findViewById(R.id.progreso).getVisibility());});
        }
    }

    @Test public void pasosReflejanDatosSinInventarAvances() throws Exception {
        JSONObject n = new JSONArray(FILAS).getJSONObject(0);
        List<ExpedientePendiente.Paso> pasos=ExpedientePendiente.pasos(n);
        assertEquals(6,pasos.size());
        for(int i=0;i<3;i++)assertEquals(ExpedientePendiente.Estado.COMPLETADO,pasos.get(i).estado);
        assertEquals(ExpedientePendiente.Estado.SIGUIENTE,pasos.get(3).estado);
        assertEquals(ExpedientePendiente.Estado.PENDIENTE,pasos.get(5).estado);
        n.put("orden_sancion_generada_at","2026-09-20");
        pasos=ExpedientePendiente.pasos(n);
        assertEquals(ExpedientePendiente.Estado.SIN_REGISTRO,pasos.get(3).estado);
        assertEquals(ExpedientePendiente.Estado.COMPLETADO,pasos.get(4).estado);
        assertEquals(ExpedientePendiente.Estado.SIGUIENTE,pasos.get(5).estado);
        n.remove("fecha_reincorporacion");
        assertEquals("Una orden posterior no inventa una reincorporación",ExpedientePendiente.Estado.SIGUIENTE,ExpedientePendiente.pasos(n).get(1).estado);
        n.put("codigo_infraccion","G39");
        pasos=ExpedientePendiente.pasos(n);assertEquals(2,pasos.size());
        assertEquals(R.string.paso_informe,pasos.get(1).titulo);
        assertEquals(ExpedientePendiente.Estado.POR_REVISAR,pasos.get(1).estado);
        n.put("codigo_infraccion",JSONObject.NULL);
        assertEquals(R.string.paso_clasificar,ExpedientePendiente.pasos(n).get(1).titulo);
        n.put("archivo_leve_generada_at","2026-09-20");
        assertFalse(ExpedientePendiente.pendiente(n));
        assertEquals(R.string.paso_archivo,ExpedientePendiente.pasos(n).get(1).titulo);
    }

    private void contrasteWeb(ActivityScenario<WebActivity> s,String selector) throws Exception {
        String resultado=js(s,"(()=>{const el=document.querySelector('"+selector+"');const c=getComputedStyle(el);let e=el,b=c.backgroundColor;while(b==='rgba(0, 0, 0, 0)'&&e.parentElement){e=e.parentElement;b=getComputedStyle(e).backgroundColor;}return [c.color,b];})()");
        JSONArray colores=new JSONArray(resultado);
        double contraste=ColorUtils.calculateContrast(rgb(colores.getString(0)),rgb(colores.getString(1)));
        assertTrue(selector+" requiere contraste >=4.5, obtenido "+contraste,contraste>=4.5);
    }
    private int rgb(String css) {
        String[] n=css.replaceAll("[^0-9.,]","").split(",");
        return Color.rgb(Integer.parseInt(n[0].trim()),Integer.parseInt(n[1].trim()),Integer.parseInt(n[2].trim()));
    }
    private void esperarTema(ActivityScenario<WebActivity> s,String tema) throws Exception {
        long fin=System.currentTimeMillis()+5000;
        do {
            if(("\""+tema+"\"").equals(js(s,"document.documentElement.getAttribute('data-native-theme')")))return;
            Thread.sleep(80);
        }while(System.currentTimeMillis()<fin);
        fail("No se aplicó el tema "+tema);
    }

    @Test public void temaWebTieneContrasteYSuCambioConservaFormulario() throws Exception {
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("cumplimiento"))){
            esperar(R.id.webView,null);
            js(s,"window.pruebaSinRecarga=123;document.getElementById('textoConservar').value='Mi borrador';");
            WebActivity[] originalActividad={null};s.onActivity(a->originalActividad[0]=a);
            for(int modo:new int[]{AppCompatDelegate.MODE_NIGHT_YES,AppCompatDelegate.MODE_NIGHT_NO,AppCompatDelegate.MODE_NIGHT_YES}){
                s.onActivity(a->a.getDelegate().setLocalNightMode(modo));
                String tema=modo==AppCompatDelegate.MODE_NIGHT_YES?"dark":"light";
                esperarTema(s,tema);
                s.onActivity(a->assertSame(originalActividad[0],a));
                assertEquals("123",js(s,"window.pruebaSinRecarga"));
                assertEquals("\"Mi borrador\"",js(s,"document.getElementById('textoConservar').value"));
                for(String selector:new String[]{"body",".card h2",".card .muted","input","select","textarea",".btn-primary",".pill-warning",".modal h3"})contrasteWeb(s,selector);
                Thread.sleep(180);captura("web-"+tema+"-corregido.png");
            }
        }
    }

    @Test public void seguimientoOscuroMuestraPasosLegibles() throws Exception {
        try(ActivityScenario<SeguimientoActivity> s=ActivityScenario.launch(SeguimientoActivity.class)){
            esperar(R.id.tvCantidadPendientes,"1");
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            esperar(R.id.tvCantidadPendientes,"1");Thread.sleep(250);
            s.onActivity(a->{
                LinearLayout lista=a.findViewById(R.id.listaPendientes);
                assertEquals(1,lista.getChildCount());
                int borde=a.getColorStateList(R.color.borde_campo).getColorForState(
                        new int[]{android.R.attr.state_enabled},Color.TRANSPARENT);
                assertTrue("El contorno del buscador debe distinguirse en oscuro",ColorUtils.calculateContrast(borde,a.getColor(R.color.fondo_app))>=3);
                assertTrue(ColorUtils.calculateContrast(a.getColor(R.color.texto_principal),a.getColor(R.color.superficie_tarjeta))>=4.5);
                assertTrue(ColorUtils.calculateContrast(a.getColor(R.color.verde_pnp),a.getColor(R.color.superficie_tarjeta))>=4.5);
                assertTrue(ColorUtils.calculateContrast(a.getColor(R.color.sobre_primario),a.getColor(R.color.verde_pnp))>=4.5);
                StringBuilder textos=new StringBuilder();recogerTextos(lista,textos);
                assertTrue(textos.toString().contains("3 de 6"));
                assertTrue(textos.toString().contains("Completado"));
                assertTrue(textos.toString().contains("Pendiente"));
                assertTrue(textos.toString().contains("Subir expediente"));
            });
            captura("seguimiento-dark-pasos.png");
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_NO));
            esperar(R.id.tvCantidadPendientes,"1");Thread.sleep(250);captura("seguimiento-light-pasos.png");
        }
        try(ActivityScenario<InicioActivity> s=ActivityScenario.launch(InicioActivity.class)){
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            esperar(R.id.btnMiToken,null);Thread.sleep(200);captura("inicio-dark-corregido.png");
        }
        try(ActivityScenario<ExpedienteActivity> s=ActivityScenario.launch(ExpedienteActivity.class)){
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            esperar(R.id.btnEscanear,null);Thread.sleep(200);captura("subir-dark-corregido.png");
        }
    }
    private void recogerTextos(View v,StringBuilder resultado){
        if(v instanceof TextView)resultado.append(((TextView)v).getText()).append('\n');
        if(v instanceof android.view.ViewGroup){
            android.view.ViewGroup grupo=(android.view.ViewGroup)v;
            for(int i=0;i<grupo.getChildCount();i++)recogerTextos(grupo.getChildAt(i),resultado);
        }
    }

    private void casoPrueba() throws Exception {
        WebActivity.moduloPrueba=asset("modulos.js")+"\n"+asset("casework.js");
    }
    private void esperarJs(ActivityScenario<WebActivity> s,String condicion) throws Exception {
        long limite=System.currentTimeMillis()+12000;
        do {if("true".equals(js(s,condicion)))return;Thread.sleep(80);}while(System.currentTimeMillis()<limite);
        fail("No se cumplió: "+condicion);
    }
    @Test public void cargaDigitalNoRecepcionaYConfirmacionExigeConformidad() throws Exception {
        casoPrueba();
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("recepcion-fisica"))){
            esperar(R.id.webView,null);
            assertEquals("0",js(s,"casesQa.receipts.length"));
            assertEquals("1",js(s,"document.querySelectorAll('#native-case-list article').length"));
            assertEquals("true",js(s,"document.querySelector('.native-verification button').disabled"));
            js(s,"document.querySelector('.native-verification button').click()");
            assertEquals("0",js(s,"casesQa.calls.length"));
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));
            esperarTema(s,"dark");
            contrasteWeb(s,".native-notice");captura("recepcion-fisica-pendiente.png");
            js(s,"(()=>{const c=document.querySelector('.native-verification input');c.checked=true;c.dispatchEvent(new Event('change'));const b=document.querySelector('.native-verification button');b.click();b.click();})()");
            esperarJs(s,"casesQa.receipts.length===1 && !!document.querySelector('.native-success')");
            assertEquals("1",js(s,"casesQa.calls.length"));
            assertEquals("0",js(s,"casesQa.appeals.length"));
            assertEquals("true",js(s,"/19 de (setiembre|septiembre) de 2026/.test(document.querySelector('.native-success').textContent)"));
            contrasteWeb(s,".native-success strong");captura("recepcion-fisica-confirmada.png");
        }
        // Otro ingreso consulta el mismo cargo persistido, sin permisos de administrador.
        WebActivity.moduloPrueba += "\nstate.role='viewer';window.casesQa.receipts=[{nota_id:'nota-subida',recibido_at:'2026-09-20T01:30:00Z',conformidad_verificada:true}];";
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("consulta"))){
            esperar(R.id.webView,null);
            esperarJs(s,"document.querySelectorAll('#native-case-list article').length===3");
            assertEquals("true",js(s,"document.querySelector('.native-success').textContent.includes('Su documento fue recibido el')"));
            assertEquals("0",js(s,"document.querySelectorAll('.native-verification').length"));
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));esperarTema(s,"dark");
            captura("consulta-expediente-recibido.png");
        }
    }
    @Test public void apelacionEsOpcionalIndependienteYNoDuplicaEnvio() throws Exception {
        casoPrueba();
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("detalle").putExtra(WebActivity.EXTRA_NOTA_ID,"nota-subida"))){
            esperar(R.id.webView,null);
            assertEquals("true",js(s,"document.querySelector('.native-appeal').textContent.includes('3 días hábiles')"));
            assertEquals("true",js(s,"document.querySelector('.native-appeal').textContent.includes('No es un paso obligatorio')"));
            assertEquals("false",js(s,"document.getElementById('view-nota-detail').textContent.includes('Notificación final')"));
            assertEquals("true",js(s,"document.getElementById('view-nota-detail').textContent.includes('Subir expediente')"));
            assertEquals("0",js(s,"casesQa.calls.length"));
            js(s,"(()=>{const p=document.querySelector('.native-appeal details');p.open=true;const f=p.querySelector('form');f.elements.fecha.value='2020-01-01';f.elements.conforme.checked=true;const dt=new DataTransfer();dt.items.add(new File(['no PDF'],'invalido.pdf',{type:'application/pdf'}));f.elements.archivo.files=dt.files;f.requestSubmit();})()");
            esperarJs(s,"document.querySelector('.native-appeal .native-feedback').textContent.includes('PDF válido')");
            assertEquals("0",js(s,"casesQa.uploads.length"));
            js(s,"(()=>{const f=document.querySelector('.native-appeal form');const dt=new DataTransfer();dt.items.add(new File(['%PDF-1.4 DEMOSTRACION'],'apelacion-demo.pdf',{type:'application/pdf'}));f.elements.archivo.files=dt.files;f.requestSubmit();f.requestSubmit();})()");
            esperarJs(s,"document.querySelector('.native-appeal').textContent.includes('Apelación registrada')");
            assertEquals("1",js(s,"casesQa.calls.length"));
            assertEquals("1",js(s,"casesQa.uploads.length"));
            assertEquals("0",js(s,"casesQa.receipts.length"));
            s.onActivity(a->a.getDelegate().setLocalNightMode(AppCompatDelegate.MODE_NIGHT_YES));esperarTema(s,"dark");
            js(s,"document.querySelector('.native-appeal').scrollIntoView()");Thread.sleep(200);captura("apelacion-opcional-registrada.png");
            js(s,"window.openQaNote('nota-archivo')");
            esperarJs(s,"document.querySelector('.native-appeal')===null");
        }
    }
    @Test public void sinMigracionNiErrorSeInventanRecepciones() throws Exception {
        casoPrueba();WebActivity.moduloPrueba+="\nwindow.casesQa.failTable='recepciones_fisicas';";
        try(ActivityScenario<WebActivity> s=ActivityScenario.launch(modulo("consulta"))){
            esperar(R.id.webView,null);
            assertEquals("true",js(s,"document.getElementById('native-case-info').textContent.includes('Supabase')"));
            assertEquals("0",js(s,"document.querySelectorAll('.native-success,.native-verification').length"));
            js(s,"casesQa.failTable=null;casesQa.failRpc=true;document.getElementById('native-case-refresh').click()");
            esperarJs(s,"document.querySelector('.native-verification')!==null");
            js(s,"(()=>{const c=document.querySelector('.native-verification input');c.checked=true;c.dispatchEvent(new Event('change'));document.querySelector('.native-verification button').click();})()");
            esperarJs(s,"document.querySelector('.native-verification .native-feedback').textContent.includes('No se pudo')");
            assertEquals("0",js(s,"casesQa.receipts.length"));
            assertEquals("0",js(s,"document.querySelectorAll('.native-success').length"));
        }
    }
}
