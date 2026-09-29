package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.Intent;
import android.os.Looper;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

/** Regresiones de la auditoría. Todo acceso de cuenta utiliza respuestas ficticias. */
@RunWith(AndroidJUnit4.class)
public class AuditoriaSesionTest {
    private Context app;
    private SupabaseAuth.Transporte original;
    private String sesion(String token, long vence) throws Exception {
        return new JSONObject().put("access_token", token).put("refresh_token", "refresh-" + token)
                .put("expires_at", vence).put("user", new JSONObject().put("id", "auditoria-qa")).toString();
    }
    private long futura() { return System.currentTimeMillis() / 1000 + 3600; }
    @Before public void preparar() {
        app = InstrumentationRegistry.getInstrumentation().getTargetContext();
        assertTrue(app.getPackageName().endsWith(".qa"));
        original = SupabaseAuth.transporte;
        SesionActual.borrar(); AlmacenSeguro.borrarRefresco(app); Perfil.borrar(app);
        SupabaseAuth.transporte = (m,r,t,c,p) -> { throw new java.io.IOException("Red no permitida"); };
    }
    @After public void cerrar() {
        SesionActual.borrar(); AlmacenSeguro.borrarRefresco(app); Perfil.borrar(app);
        SupabaseAuth.transporte = original;
    }
    @Test public void intentAntiguoNoReemplazaSesionRenovada() throws Exception {
        SesionActual.recibir(app, sesion("actual-aal2", futura()));
        SesionActual.restaurar(app, sesion("antigua-aal1", 1));
        assertEquals("actual-aal2", SesionActual.token(app, "auditoria-qa"));
        assertEquals("refresh-actual-aal2", AlmacenSeguro.refresco(app));
    }
    @Test public void mensajeWebTardioNoReabreCuentaCerrada() throws Exception {
        SesionActual.recibir(app, sesion("actual", futura()));
        long epoca = SesionActual.epoca();
        SesionActual.borrar(); AlmacenSeguro.borrarRefresco(app);
        SesionActual.recibirSiVigente(app, sesion("tardia", futura()), epoca);
        assertNull(SesionActual.usuario()); assertNull(AlmacenSeguro.refresco(app));
    }
    @Test public void noSeUsaLaSesionDeOtraPersona() throws Exception {
        SesionActual.recibir(app, sesion("actual", futura()));
        try { SesionActual.token(app, "otra-persona"); fail("Debe rechazar una cuenta distinta"); }
        catch (SupabaseAuth.ErrorApi e) { assertEquals(401, e.estado); }
    }
    @Test public void dosModulosRenuevanUnaSolaVez() throws Exception {
        SesionActual.recibir(app, sesion("vencida", 1));
        AtomicInteger renovaciones = new AtomicInteger();
        String nueva = sesion("renovada", futura());
        SupabaseAuth.transporte = (m,r,t,c,p) -> { renovaciones.incrementAndGet(); return nueva; };
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<String> a = pool.submit(() -> SesionActual.token(app, "auditoria-qa"));
            Future<String> b = pool.submit(() -> SesionActual.token(app, "auditoria-qa"));
            assertEquals("renovada", a.get(10, TimeUnit.SECONDS));
            assertEquals("renovada", b.get(10, TimeUnit.SECONDS));
            assertEquals(1, renovaciones.get());
        } finally { pool.shutdownNow(); }
    }
    @Test public void recuperacionRenuevaFueraDelHiloPrincipalYNoRegeneraAlRotar() throws Exception {
        SesionActual.recibir(app, sesion("vencida", 1));
        AtomicInteger generaciones = new AtomicInteger();
        String nueva = sesion("renovada", futura());
        CountDownLatch generado = new CountDownLatch(1);
        SupabaseAuth.transporte = (m,r,t,c,p) -> {
            assertNotEquals(Looper.getMainLooper(), Looper.myLooper());
            if (r.startsWith("/auth/v1/token?")) return nueva;
            if (r.equals("/rest/v1/rpc/generar_codigos_recuperacion")) {
                assertEquals("renovada", t); generaciones.incrementAndGet(); generado.countDown();
                return "[\"ABCDE-FGHIJ\",\"KLMNP-QRSTU\",\"VWXYZ-23456\",\"789AB-CDEFG\",\"HJKLM-NPQRS\",\"TUVWX-YZ234\",\"56789-ABCDE\",\"FGHJK-LMNPQ\"]";
            }
            throw new java.io.IOException("Ruta no prevista");
        };
        try (ActivityScenario<RecuperacionActivity> pantalla = ActivityScenario.launch(new Intent(app, RecuperacionActivity.class))) {
            assertTrue(generado.await(15, TimeUnit.SECONDS));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            pantalla.recreate();
            pantalla.onActivity(a -> assertTrue(((TextView)a.findViewById(R.id.tvCodigos)).getText().toString().contains("ABCDE-FGHIJ")));
            assertEquals(1, generaciones.get());
        }
    }
    @Test public void salirSoloRevocaEstaSesion() throws Exception {
        SupabaseAuth.transporte = (m,r,t,c,p) -> { assertEquals("/auth/v1/logout?scope=local", r); return ""; };
        SupabaseAuth.cerrarSesion("qa");
    }
}
