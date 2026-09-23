package com.hidalgoferrai.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import androidx.test.runner.lifecycle.Stage;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.not;

/** Solo se ejecuta en com.hidalgoferrai.myapplication.qa. Nunca usa la red real. */
@RunWith(AndroidJUnit4.class)
public class FlujoAccesoTest {
    private Context contexto;
    private SupabaseAuth.Transporte original;
    private final AtomicInteger consultas = new AtomicInteger();
    private final AtomicInteger altas = new AtomicInteger();
    private final AtomicInteger verificaciones = new AtomicInteger();
    private volatile boolean falloFactores, falloVerificacion, documentosVacios, firmado;
    private volatile boolean clavePendiente, falloClave;
    private volatile boolean eliminacionPendiente, falloEliminacion;
    private volatile boolean codigosEntregados;
    private volatile String factores = "[]";
    private static final String UID = "usuario-prueba-aislada";
    private static final String SESION = "{\"access_token\":\"sesion-ficticia\",\"refresh_token\":\"refresco-ficticio\",\"user\":{\"id\":\"usuario-prueba-aislada\",\"email\":\"prueba@example.invalid\"}}";

    @Before public void preparar() {
        contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue("No ejecutar pruebas sobre datos reales", contexto.getPackageName().endsWith(".qa"));
        AlmacenSeguro.borrar(contexto);
        AlmacenSeguro.borrarRefresco(contexto);
        Perfil.borrar(contexto);
        SesionActual.borrar();
        original = SupabaseAuth.transporte;
        SupabaseAuth.transporte = this::responder;
    }

    @After public void cerrar() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Stage etapa : new Stage[]{Stage.RESUMED, Stage.STARTED, Stage.CREATED, Stage.STOPPED}) {
                for (Activity actividad : new ArrayList<>(ActivityLifecycleMonitorRegistry.getInstance()
                        .getActivitiesInStage(etapa))) actividad.finish();
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SupabaseAuth.transporte = original;
        WebActivity.htmlPrueba=null; WebActivity.moduloPrueba=null;
        SesionActual.borrar();
    }

    private String responder(String metodo, String ruta, String token, String cuerpo, String preferencia)
            throws IOException {
        consultas.incrementAndGet();
        if (ruta.startsWith("/auth/v1/token?")) return SESION;
        if (ruta.equals("/auth/v1/user")) {
            if (falloFactores) throw new SocketTimeoutException("simulado");
            return "{\"factors\":" + factores + "}";
        }
        if (ruta.equals("/auth/v1/factors") && metodo.equals("POST")) {
            altas.incrementAndGet();
            return "{\"id\":\"factor-prueba\",\"totp\":{\"secret\":\"GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ\"}}";
        }
        if (ruta.endsWith("/challenge")) return "{\"id\":\"desafio-prueba\"}";
        if (ruta.endsWith("/verify")) {
            verificaciones.incrementAndGet();
            if (falloVerificacion) throw new SocketTimeoutException("simulado");
            return SESION;
        }
        if (ruta.startsWith("/rest/v1/profiles?select=estado")) return "[{\"estado\":\"aprobado\"}]";
        if (ruta.startsWith("/rest/v1/profiles?select=role")) return "[{\"role\":\"admin\"}]";
        if (ruta.equals("/rest/v1/rpc/necesita_cambiar_clave")) {
            if (falloClave) throw new SocketTimeoutException("simulado");
            return clavePendiente ? "true" : "false";
        }
        if (ruta.equals("/rest/v1/rpc/generar_codigos_recuperacion")) {
            codigosEntregados = true;
            return "[\"ABCDE-FGHIJ\",\"KLMNP-QRSTU\",\"VWXYZ-23456\",\"789AB-CDEFG\","
                    + "\"HJKLM-NPQRS\",\"TUVWX-YZ234\",\"56789-ABCDE\",\"FGHJK-LMNPQ\"]";
        }
        if (ruta.equals("/rest/v1/rpc/usar_codigo_recuperacion")) {
            if (cuerpo != null && cuerpo.contains("ABCDE-FGHIJ")) {
                factores = "[]";
                return "\"ok\"";
            }
            return "\"invalido\"";
        }
        if (ruta.equals("/rest/v1/rpc/tiene_eliminacion_pendiente")) return eliminacionPendiente ? "true" : "false";
        if (ruta.equals("/rest/v1/rpc/solicitar_eliminacion_cuenta")) {
            if (falloEliminacion) throw new SocketTimeoutException("simulado");
            eliminacionPendiente = true;
            return "\"ok\"";
        }
        if (ruta.startsWith("/rest/v1/aceptaciones_terminos")) return "";
        if (ruta.startsWith("/rest/v1/solicitudes_acceso"))
            return "[{\"grado\":\"S1 PNP\",\"nombres\":\"Cuenta\",\"apellidos\":\"Demostración\"}]";
        if (ruta.startsWith("/rest/v1/documentos_institucionales")) return documentosVacios ? "[]" :
            "[{\"id\":\"politica-prueba\",\"titulo\":\"Uso responsable de la información\",\"contenido\":\"Documento de demostración para comprobar la lectura y la firma.\\n\\nLa información institucional debe tratarse con responsabilidad y utilizarse únicamente dentro de las funciones autorizadas.\\n\\nEsta prueba no registra una firma real.\",\"version\":2}]";
        if (ruta.startsWith("/rest/v1/firmas_documentos")) {
            if (metodo.equals("POST")) { firmado = true; return ""; }
            return firmado ? "[{\"documento_id\":\"politica-prueba\",\"documento_version\":2}]" : "[]";
        }
        throw new IOException("Ruta no prevista por la prueba");
    }

    private Intent tokenIntent() {
        return new Intent(contexto, TokenActivity.class).putExtra(TokenActivity.EXTRA_TOKEN, "sesion-ficticia")
                .putExtra(TokenActivity.EXTRA_USUARIO_ID, UID).putExtra(TokenActivity.EXTRA_SESION, SESION);
    }

    private Intent firmaIntent() {
        return new Intent(contexto, FirmaActivity.class).putExtra(FirmaActivity.EXTRA_TOKEN, "sesion-ficticia")
                .putExtra(FirmaActivity.EXTRA_USUARIO_ID, UID).putExtra(FirmaActivity.EXTRA_SESION, SESION);
    }

    private void esperarTexto(int id, String texto) throws Exception {
        long limite = System.currentTimeMillis() + 8000;
        do {
            final boolean[] encontrado = {false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                    TextView v = a.findViewById(id);
                    if (v != null && v.getText().toString().contains(texto)) encontrado[0] = true;
                }
            });
            if (encontrado[0]) return;
            Thread.sleep(60);
        } while (System.currentTimeMillis() < limite);
        fail("No apareció el estado esperado: " + texto);
    }

    private void captura(String nombre) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            for (Activity a : ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)) {
                // Nunca captura pantallas con tokens o contraseñas, ni la aplicación de uso real.
                if (a instanceof TokenActivity || (a.getWindow().getAttributes().flags
                        & android.view.WindowManager.LayoutParams.FLAG_SECURE)!=0) continue;
                View vista = a.getWindow().getDecorView();
                Bitmap imagen = Bitmap.createBitmap(vista.getWidth(), vista.getHeight(), Bitmap.Config.ARGB_8888);
                vista.draw(new Canvas(imagen));
                try (FileOutputStream salida = new FileOutputStream(new File(contexto.getExternalFilesDir(null), nombre))) {
                    imagen.compress(Bitmap.CompressFormat.PNG, 100, salida);
                } catch (IOException e) { throw new AssertionError(e); }
                imagen.recycle();
            }
        });
    }

    @Test public void sesionGuardadaNoSaltaLaAceptacion() throws Exception {
        AlmacenSeguro.guardarRefresco(contexto, "refresco-ficticio");
        try (ActivityScenario<LoginActivity> escenario = ActivityScenario.launch(LoginActivity.class)) {
            Thread.sleep(500);
            assertEquals(0, consultas.get());
            escenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnGoogle).isEnabled()));
            escenario.recreate();
            assertEquals(0, consultas.get());
            captura("acceso-claro.png");
            onView(withId(R.id.btnVerTerminos)).perform(scrollTo(), click());
            esperarTexto(R.id.tvTitulo, contexto.getString(R.string.terminos_titulo));
            assertEquals(0, consultas.get());
        }
    }

    @Test public void continuarConAceptacionAbrePoliticasNoToken() throws Exception {
        AlmacenSeguro.guardarRefresco(contexto, "refresco-ficticio");
        try (ActivityScenario<LoginActivity> escenario = ActivityScenario.launch(LoginActivity.class)) {
            onView(withId(R.id.cbAcepto)).perform(scrollTo(), click());
            onView(withId(R.id.btnGoogle)).perform(scrollTo(), click());
            esperarTexto(R.id.tvResumenFirmas, "0 de 1");
            assertEquals(0, altas.get());
            captura("politicas-claro.png");
        }
    }
    private String assetQa(String nombre) throws IOException {
        try(java.io.InputStream in=InstrumentationRegistry.getInstrumentation().getContext().getAssets().open(nombre);
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream()){
            byte[] bytes=new byte[4096];int n;while((n=in.read(bytes))!=-1)out.write(bytes,0,n);
            return out.toString("UTF-8");
        }
    }
    @Test public void clavePendienteVuelveALecturaDePoliticasTrasConfirmarServidor() throws Exception {
        clavePendiente=true;
        WebActivity.htmlPrueba=assetQa("modulos.html");
        WebActivity.moduloPrueba=assetQa("modulos.js")+"\n"+assetQa("github_updates.js")+"\nupdatesQa.pending=true;";
        AlmacenSeguro.guardarRefresco(contexto,"refresco-ficticio");
        try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
            onView(withId(R.id.cbAcepto)).perform(scrollTo(),click());
            onView(withId(R.id.btnGoogle)).perform(scrollTo(),click());
            esperarTexto(R.id.tvModuloTitulo,contexto.getString(R.string.modulo_seguridad));
            long fin=System.currentTimeMillis()+10000;final boolean[] listo={false};
            while(!listo[0]&&System.currentTimeMillis()<fin){
                InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                    for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                        if(a instanceof WebActivity&&a.findViewById(R.id.webView).isShown())listo[0]=true;
                });Thread.sleep(80);
            }
            assertTrue(listo[0]);assertEquals(0,altas.get());assertFalse(firmado);
            clavePendiente=false; // Simula la confirmación del servidor, no un cambio real.
            InstrumentationRegistry.getInstrumentation().runOnMainSync(()->{
                for(Activity a:ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED))
                    if(a instanceof WebActivity)((android.webkit.WebView)a.findViewById(R.id.webView)).evaluateJavascript("updatesQa.resume()",null);
            });
            esperarTexto(R.id.tvResumenFirmas,"0 de 1");assertFalse(firmado);assertEquals(0,altas.get());
        }
    }
    @Test public void errorAlConsultarClaveNoPermiteAvanzar() throws Exception {
        falloClave=true;AlmacenSeguro.guardarRefresco(contexto,"refresco-ficticio");
        try(ActivityScenario<LoginActivity> s=ActivityScenario.launch(LoginActivity.class)){
            onView(withId(R.id.cbAcepto)).perform(scrollTo(),click());
            onView(withId(R.id.btnGoogle)).perform(scrollTo(),click());
            esperarTexto(R.id.estadoTitulo,contexto.getString(R.string.error_acceso_titulo));
            s.onActivity(a->assertEquals(View.GONE,a.findViewById(R.id.progreso).getVisibility()));
            assertFalse(firmado);assertEquals(0,altas.get());
        }
    }

    @Test public void documentosVaciosNoHabilitanAcceso() throws Exception {
        documentosVacios = true;
        try (ActivityScenario<FirmaActivity> escenario = ActivityScenario.launch(firmaIntent())) {
            esperarTexto(R.id.estadoTitulo, "No se encontraron");
            escenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnContinuar).isEnabled()));
            captura("politicas-vacias.png");
        }
    }

    @Test public void falloDeRedDetieneAnilloYPermiteReintentar() throws Exception {
        falloFactores = true;
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "REQUIERE");
            escenario.onActivity(a -> {
                assertFalse(((CircularProgressIndicator) a.findViewById(R.id.anillo)).isIndeterminate());
                assertEquals(View.VISIBLE, a.findViewById(R.id.estadoPanel).getVisibility());
                assertTrue(a.findViewById(R.id.btnReintentar).isEnabled());
            });
            falloFactores = false;
            onView(withId(R.id.btnReintentar)).perform(scrollTo(), click());
            esperarTexto(R.id.tvEstadoToken, "LISTO PARA ACTIVAR");
            assertEquals(0, altas.get());
        }
    }

    @Test public void activacionFallidaReutilizaFactorPendiente() throws Exception {
        falloVerificacion = true;
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "LISTO PARA ACTIVAR");
            assertEquals(0, altas.get());
            onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
            esperarTexto(R.id.tvEstadoToken, "REQUIERE");
            assertEquals(1, altas.get());
            assertFalse(AlmacenSeguro.tokenVerificado(contexto, UID));
            assertNotNull(AlmacenSeguro.secreto(contexto, UID));
            falloVerificacion = false;
            onView(withId(R.id.btnReintentar)).perform(scrollTo(), click());
            cerrarCodigosDeRespaldo();
            esperarTexto(R.id.tvEstadoToken, "PROTECCIÓN ACTIVA");
            assertEquals(1, altas.get());
            assertEquals(2, verificaciones.get());
            assertTrue(AlmacenSeguro.tokenVerificado(contexto, UID));
            onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
            esperarTexto(R.id.tvNombre, "");
            assertEquals("No reutilizar el mismo TOTP tras activarlo", 2, verificaciones.get());
        }
    }

    @Test public void factorEnOtroTelefonoOfreceIngresoDeCodigo() throws Exception {
        factores = "[{\"id\":\"otro-factor\",\"factor_type\":\"totp\",\"status\":\"verified\"}]";
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "OTRO DISPOSITIVO");
            escenario.onActivity(a -> {
                assertFalse(((CircularProgressIndicator) a.findViewById(R.id.anillo)).isIndeterminate());
                assertEquals(View.VISIBLE, a.findViewById(R.id.layoutCodigoExterno).getVisibility());
            });
            onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
            assertEquals(0, verificaciones.get());
            assertEquals(0, altas.get());
        }
    }

    @Test public void leerFirmarYContinuarRequiereAccionesExplicitas() throws Exception {
        try (ActivityScenario<FirmaActivity> escenario = ActivityScenario.launch(firmaIntent())) {
            esperarTexto(R.id.tvResumenFirmas, "0 de 1");
            onView(withText(R.string.firma_boton)).check(matches(org.hamcrest.Matchers.not(isEnabled())));
            onView(withText(R.string.firma_leer)).perform(scrollTo(), click());
            onView(withText(R.string.firma_he_leido)).perform(click());
            onView(withId(R.id.etCargo)).perform(scrollTo(), replaceText("Oficial de prueba"), closeSoftKeyboard());
            onView(withText(R.string.firma_boton)).perform(scrollTo(), click());
            assertFalse(firmado);
            onView(withText(R.string.firma_confirmar_boton)).perform(click());
            esperarTexto(R.id.tvResumenFirmas, "1 de 1");
            assertEquals(0, altas.get());
            escenario.onActivity(a -> assertTrue(a.findViewById(R.id.btnContinuar).isEnabled()));
            onView(withId(R.id.btnContinuar)).perform(click());
            esperarTexto(R.id.tvEstadoToken, "LISTO PARA ACTIVAR");
        }
    }

    @Test public void erroresNoMuestranRespuestaSensible() {
        SupabaseAuth.ErrorApi error = new SupabaseAuth.ErrorApi(400,
                "{\"error_code\":\"mfa_verification_failed\",\"message\":\"secreto-no-visible\"}");
        assertEquals(R.string.error_codigo, Errores.mensaje(error));
        assertFalse(error.getMessage().contains("secreto-no-visible"));
    }

    @Test public void capturasVisualesClaraOscuraEInicio() throws Exception {
        AlmacenSeguro.guardarRefresco(contexto, "refresco-ficticio");
        try (ActivityScenario<LoginActivity> escenario = ActivityScenario.launch(LoginActivity.class)) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            captura("acceso-claro.png");
            escenario.onActivity(a -> a.getDelegate().setLocalNightMode(
                    androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            captura("acceso-oscuro.png");
        }
        Perfil.guardar(contexto, UID, "S1 PNP", "Cuenta Demostración", "admin");
        try (ActivityScenario<InicioActivity> escenario = ActivityScenario.launch(
                new Intent(contexto, InicioActivity.class).putExtra(InicioActivity.EXTRA_SESION, SESION))) {
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            captura("inicio-claro.png");
        }
        try (ActivityScenario<FirmaActivity> escenario = ActivityScenario.launch(firmaIntent())) {
            esperarTexto(R.id.tvResumenFirmas, "0 de 1");
            captura("politicas-claro.png");
        }
    }

    /** Al activar el token se entregan los códigos de respaldo antes de seguir. */
    @Test public void activarElTokenEntregaCodigosDeRespaldo() throws Exception {
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "LISTO PARA ACTIVAR");
            onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
            esperarTexto(R.id.tvCodigos, "ABCDE-FGHIJ");
            assertTrue(codigosEntregados);
            // No se puede continuar sin confirmar que quedaron guardados.
            onView(withId(R.id.btnContinuar)).check(matches(not(isEnabled())));
            onView(withId(R.id.cbGuardados)).perform(scrollTo(), click());
            onView(withId(R.id.btnContinuar)).perform(scrollTo(), click());
            esperarTexto(R.id.tvEstadoToken, "PROTECCIÓN ACTIVA");
        }
    }

    /** Perder el teléfono ya no deja a nadie fuera: un código de respaldo reactiva el token. */
    @Test public void codigoDeRespaldoPermiteActivarEnOtroTelefono() throws Exception {
        factores = "[{\"id\":\"factor-de-otro\",\"factor_type\":\"totp\",\"status\":\"verified\"}]";
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "TOKEN EN OTRO DISPOSITIVO");
            onView(withId(R.id.btnPerdiTelefono)).perform(scrollTo(), click());
            onView(withId(R.id.etCodigoRecuperacion)).inRoot(isDialog())
                    .perform(replaceText("ABCDE-FGHIJ"));
            onView(withText(R.string.recuperar_boton)).inRoot(isDialog()).perform(click());
            // Se inscribe un token nuevo y se vuelven a entregar los códigos.
            esperarTexto(R.id.tvCodigos, "ABCDE-FGHIJ");
            assertEquals(1, altas.get());
        }
    }

    /** Un código equivocado no borra nada ni deja activar. */
    @Test public void codigoDeRespaldoInvalidoNoActivaNada() throws Exception {
        factores = "[{\"id\":\"factor-de-otro\",\"factor_type\":\"totp\",\"status\":\"verified\"}]";
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "TOKEN EN OTRO DISPOSITIVO");
            onView(withId(R.id.btnPerdiTelefono)).perform(scrollTo(), click());
            onView(withId(R.id.etCodigoRecuperacion)).inRoot(isDialog())
                    .perform(replaceText("ZZZZZ-ZZZZZ"));
            onView(withText(R.string.recuperar_boton)).inRoot(isDialog()).perform(click());
            esperarTexto(R.id.estadoMensaje, "no es válido");
            assertEquals(0, altas.get());
        }
    }

    /** Vía de baja exigida por Google Play: debe registrarse una sola vez y quedar a la vista. */
    @Test public void eliminarCuentaRegistraElPedidoUnaSolaVez() throws Exception {
        Intent intent = new Intent(contexto, EliminarCuentaActivity.class)
                .putExtra(EliminarCuentaActivity.EXTRA_TOKEN, "sesion-ficticia");
        try (ActivityScenario<EliminarCuentaActivity> escenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.btnSolicitar)).perform(scrollTo(), click());
            onView(withText(R.string.eliminar_confirmar_si)).perform(click());
            esperarTexto(R.id.tvEstado, "pedido de eliminación en curso");
            escenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnSolicitar).isEnabled()));
        }
        // Al volver a entrar, la pantalla ya muestra el pedido en curso y no admite otro.
        try (ActivityScenario<EliminarCuentaActivity> escenario = ActivityScenario.launch(intent)) {
            esperarTexto(R.id.tvEstado, "pedido de eliminación en curso");
            escenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnSolicitar).isEnabled()));
        }
    }

    /** Sin sesión no se puede identificar la cuenta: se indica la vía pública. */
    @Test public void eliminarCuentaSinSesionExplicaLaViaPublica() throws Exception {
        try (ActivityScenario<EliminarCuentaActivity> escenario = ActivityScenario.launch(
                new Intent(contexto, EliminarCuentaActivity.class))) {
            esperarTexto(R.id.tvEstado, "debe haber iniciado sesión");
            escenario.onActivity(a -> assertFalse(a.findViewById(R.id.btnSolicitar).isEnabled()));
        }
    }

    @Test public void recrearPantallaRecuperaActivacionPendiente() throws Exception {
        falloVerificacion = true;
        try (ActivityScenario<TokenActivity> escenario = ActivityScenario.launch(tokenIntent())) {
            esperarTexto(R.id.tvEstadoToken, "LISTO PARA ACTIVAR");
            onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
            esperarTexto(R.id.tvEstadoToken, "REQUIERE");
            factores = "[{\"id\":\"factor-prueba\",\"factor_type\":\"totp\",\"status\":\"unverified\"}]";
            escenario.recreate();
            esperarTexto(R.id.tvEstadoToken, "LISTO PARA ACTIVAR");
            falloVerificacion = false;
            onView(withId(R.id.btnEntrar)).perform(scrollTo(), click());
            cerrarCodigosDeRespaldo();
            esperarTexto(R.id.tvEstadoToken, "PROTECCIÓN ACTIVA");
            assertEquals(1, altas.get());
        }
    }

    /** Tras activar siempre aparecen los códigos de respaldo: hay que confirmarlos para seguir. */
    private void cerrarCodigosDeRespaldo() throws Exception {
        esperarTexto(R.id.tvCodigos, "-");
        onView(withId(R.id.cbGuardados)).perform(scrollTo(), click());
        onView(withId(R.id.btnContinuar)).perform(scrollTo(), click());
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }
}
