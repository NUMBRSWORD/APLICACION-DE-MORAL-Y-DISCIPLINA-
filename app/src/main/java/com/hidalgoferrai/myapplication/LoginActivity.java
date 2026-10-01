package com.hidalgoferrai.myapplication;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LoginActivity extends AppCompatActivity {

    static final String PREFERENCIAS = "acceso";
    private static final String VERIFICADOR = "verificador";
    private static final String VERSION_ACEPTADA = "version_aceptada";

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private SharedPreferences preferencias;
    private MaterialCheckBox cbAcepto;
    private MaterialButton btnGoogle;
    private LinearProgressIndicator progreso;
    private boolean tieneSesion;
    private boolean ocupado;
    private final ActivityResultLauncher<Intent> seguridad = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), resultado -> {
                if (resultado.getResultCode() != RESULT_OK) { cargando(false); return; }
                cargando(true);
                hilo.execute(() -> {
                    try { verificarSesion(new JSONObject(SesionActual.obtener(this)), false); }
                    catch (Exception e) { avisarError(e); }
                });
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        preferencias = getSharedPreferences(PREFERENCIAS, MODE_PRIVATE);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_login);
        Tema.prepararBoton(this);
        Diseno.bordes(this, false);

        cbAcepto = findViewById(R.id.cbAcepto);
        btnGoogle = findViewById(R.id.btnGoogle);
        progreso = findViewById(R.id.progreso);

        tieneSesion = AlmacenSeguro.refresco(this) != null;
        actualizarCuenta();
        Diseno.pasos(this, 1);
        cbAcepto.setOnCheckedChangeListener((boton, marcado) -> btnGoogle.setEnabled(!ocupado && marcado));
        findViewById(R.id.btnVerTerminos).setOnClickListener(v -> startActivity(
                new Intent(this, TerminosActivity.class)));
        findViewById(R.id.btnVerDatos).setOnClickListener(v -> startActivity(
                new Intent(this, TerminosActivity.class)
                        .putExtra(TerminosActivity.EXTRA_TIPO, TerminosActivity.TIPO_DATOS)));
        btnGoogle.setOnClickListener(v -> {
            if (ocupado || !cbAcepto.isChecked()) return;
            if (tieneSesion) restaurarSesion(); else iniciarGoogle();
        });
        findViewById(R.id.btnOtraCuenta).setOnClickListener(v -> {
            SesionActual.cerrarLocal(this);
            tieneSesion = false;
            actualizarCuenta();
        });
        findViewById(R.id.btnReintentar).setOnClickListener(v -> {
            if (tieneSesion && cbAcepto.isChecked()) restaurarSesion();
        });
        findViewById(R.id.btnInstitucional).setOnClickListener(v -> abrirAplicacion(null));
        findViewById(R.id.btnDiagnostico).setOnClickListener(v -> mostrarDiagnostico());

        procesarRetorno(getIntent());
        // Una sesión guardada no equivale a haber leído o aceptado esta pantalla.
        // Solo el botón Continuar puede avanzar; no hay temporizador ni salto automático.
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (cbAcepto != null) {
            procesarRetorno(intent);
        }
    }

    @Override
    protected void onDestroy() {
        vigilante.removeCallbacks(seAgotoLaEspera);
        hilo.shutdown();
        super.onDestroy();
    }

    private void iniciarGoogle() {
        String verificador = SupabaseAuth.nuevoVerificador();
        preferencias.edit()
                .putString(VERIFICADOR, verificador)
                .putString(VERSION_ACEPTADA, ConfigSupabase.VERSION_TERMINOS)
                .apply();
        try {
            Diagnostico.paso(this, "acceso: abriendo Google en el navegador");
            startActivity(new Intent(Intent.ACTION_VIEW, SupabaseAuth.urlAccesoGoogle(this, verificador)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.web_sin_aplicacion, Toast.LENGTH_SHORT).show();
        }
    }

    private void procesarRetorno(Intent intent) {
        Uri datos = intent.getData();
        if (datos == null || !getString(R.string.oauth_esquema).equals(datos.getScheme())
                || !"auth".equals(datos.getHost())) {
            return;
        }
        intent.setData(null);

        String codigo = datos.getQueryParameter("code");
        String error = datos.getQueryParameter("error_description");
        if (error == null && datos.getFragment() != null) {
            error = Uri.parse("?" + datos.getFragment()).getQueryParameter("error_description");
        }
        if (codigo == null) {
            avisar(getString(R.string.login_error_google, error != null ? error : "sin código"));
            return;
        }

        String verificador = preferencias.getString(VERIFICADOR, null);
        boolean acepto = ConfigSupabase.VERSION_TERMINOS.equals(
                preferencias.getString(VERSION_ACEPTADA, null));
        if (verificador == null || !acepto) {
            avisar(getString(R.string.login_error_sin_aceptacion));
            return;
        }
        preferencias.edit().remove(VERIFICADOR).apply();

        Diagnostico.paso(this, "acceso: vuelta de Google recibida, canjeando");
        cargando(true);
        hilo.execute(() -> verificarCuenta(codigo, verificador));
    }

    private void verificarCuenta(String codigo, String verificador) {
        try {
            JSONObject sesion = SupabaseAuth.intercambiarCodigo(codigo, verificador);
            Diagnostico.paso(this, "acceso: sesión obtenida");
            verificarSesion(sesion, true);
        } catch (IOException | JSONException e) {
            Diagnostico.fallo(this, "acceso: canje", e);
            avisarError(e);
        }
    }

    private void restaurarSesion() {
        if (ocupado) return;
        cargando(true);
        hilo.execute(() -> {
            try {
                JSONObject sesion = new JSONObject(SesionActual.obtener(this));
                verificarSesion(sesion, true);
            } catch (IOException | JSONException | RuntimeException e) {
                Diagnostico.fallo(this, "acceso: sesión guardada", e);
                avisarError(e);
            }
        });
    }

    private void verificarSesion(JSONObject sesion, boolean registrarAceptacion)
            throws IOException, JSONException {
        String token = sesion.getString("access_token");
        if (isFinishing() || isDestroyed()) return;
        SesionActual.recibir(this, sesion.toString());
        JSONObject usuario = sesion.getJSONObject("user");
        String usuarioId = usuario.getString("id");
        String correo = usuario.optString("email", null);
        if (registrarAceptacion) {
            SupabaseAuth.registrarAceptacion(token, usuarioId);
        }
        String estado = SupabaseAuth.estadoDeCuenta(token, usuarioId);
        if ("aprobado".equals(estado) && SupabaseApi.necesitaCambiarClave(token)) {
            SesionActual.recibir(this, sesion.toString());
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                cargando(false);
                seguridad.launch(new Intent(this, WebActivity.class)
                        .putExtra(WebActivity.EXTRA_VISTA, "seguridad"));
            });
            return;
        }
        boolean faltaIdentificarse = "pendiente".equals(estado)
                && !SupabaseAuth.tieneSolicitud(token, usuarioId);
        runOnUiThread(() -> continuarSegunEstado(
                sesion, estado, token, usuarioId, correo, faltaIdentificarse));
    }

    private void avisarError(Exception e) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            cargando(false);
            if (Errores.esSesion(e)) {
                SesionActual.cerrarLocal(this);
                tieneSesion = false;
                actualizarCuenta();
            }
            Diseno.error(this, R.string.error_acceso_titulo, Errores.mensaje(e), tieneSesion);
        });
    }

    private void continuarSegunEstado(JSONObject sesion, String estado, String token,
                                      String usuarioId, String correo, boolean faltaIdentificarse) {
        if (isFinishing() || isDestroyed()) return;
        cargando(false);
        if ("aprobado".equals(estado)) {
            startActivity(new Intent(this, FirmaActivity.class)
                    .putExtra(FirmaActivity.EXTRA_TOKEN, token)
                    .putExtra(FirmaActivity.EXTRA_USUARIO_ID, usuarioId)
                    .putExtra(FirmaActivity.EXTRA_SESION, sesion.toString()));
            finish();
        } else if (faltaIdentificarse) {
            startActivity(new Intent(this, SolicitudActivity.class)
                    .putExtra(SolicitudActivity.EXTRA_TOKEN, token)
                    .putExtra(SolicitudActivity.EXTRA_USUARIO_ID, usuarioId)
                    .putExtra(SolicitudActivity.EXTRA_EMAIL, correo));
            finish();
        } else if ("pendiente".equals(estado) || "rechazado".equals(estado)) {
            startActivity(new Intent(this, PendienteActivity.class)
                    .putExtra(PendienteActivity.EXTRA_RECHAZADA, "rechazado".equals(estado))
                    .putExtra(PendienteActivity.EXTRA_TOKEN, token));
            finish();
        } else {
            avisar(getString(R.string.login_error_cuenta));
        }
    }

    private void abrirAplicacion(String sesion) {
        Intent intent = new Intent(this, WebActivity.class);
        if (sesion != null) {
            intent.putExtra(WebActivity.EXTRA_SESION, sesion);
        }
        startActivity(intent);
        finish();
    }

    /**
     * Tiempo máximo que la pantalla puede quedarse esperando. Las llamadas tienen su
     * propio límite, pero si el navegador no vuelve del acceso con Google no hay nada
     * que falle: simplemente no pasa nada, y la persona se queda encerrada mirando la
     * barra. Esto convierte ese caso en un error con botón de reintentar.
     */
    private static final long ESPERA_MAXIMA_MS = 25000;
    private final android.os.Handler vigilante = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable seAgotoLaEspera = () -> {
        if (isFinishing() || isDestroyed() || !ocupado) return;
        Diagnostico.paso(this, "acceso: espera agotada");
        cargando(false);
        Diseno.error(this, R.string.error_acceso_titulo, R.string.login_espera_agotada, tieneSesion);
    };

    private void cargando(boolean activo) {
        ocupado = activo;
        vigilante.removeCallbacks(seAgotoLaEspera);
        if (activo) vigilante.postDelayed(seAgotoLaEspera, ESPERA_MAXIMA_MS);
        if (activo) findViewById(R.id.estadoPanel).setVisibility(View.GONE);
        progreso.setVisibility(activo ? View.VISIBLE : View.GONE);
        btnGoogle.setEnabled(!activo && cbAcepto.isChecked());
        cbAcepto.setEnabled(!activo);
        findViewById(R.id.btnInstitucional).setEnabled(!activo);
        findViewById(R.id.btnOtraCuenta).setEnabled(!activo);
        btnGoogle.setText(activo ? R.string.login_verificando
                : tieneSesion ? R.string.login_continuar_cuenta : R.string.login_google);
    }

    private void actualizarCuenta() {
        btnGoogle.setText(tieneSesion ? R.string.login_continuar_cuenta : R.string.login_google);
        findViewById(R.id.btnOtraCuenta).setVisibility(tieneSesion ? View.VISIBLE : View.GONE);
    }

    /**
     * Enseña el registro para poder copiarlo y mandarlo. Se muestra aquí, y no se
     * comparte como archivo, para no abrir una vía de salida de datos solo por esto:
     * lo que hay dentro ya está saneado y cabe en un mensaje.
     */
    private void mostrarDiagnostico() {
        String texto;
        java.io.File f = Diagnostico.archivo(this);
        // Lectura a mano: Files.readAllBytes necesita Android 8 y aquí se admite desde el 7.
        try (java.io.InputStream entrada = f != null && f.exists()
                ? new java.io.FileInputStream(f) : null) {
            if (entrada == null) texto = "";
            else {
                java.io.ByteArrayOutputStream todo = new java.io.ByteArrayOutputStream();
                byte[] bloque = new byte[8192];
                int leidos;
                while ((leidos = entrada.read(bloque)) != -1) todo.write(bloque, 0, leidos);
                texto = todo.toString("UTF-8");
            }
        } catch (java.io.IOException | RuntimeException e) { texto = ""; }
        if (texto.trim().isEmpty()) texto = getString(R.string.diagnostico_vacio);
        // Lo último es lo que importa; un registro largo no cabe en el diálogo.
        if (texto.length() > 4000) texto = texto.substring(texto.length() - 4000);
        final String copiable = texto;
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.diagnostico_titulo)
                .setMessage(getString(R.string.diagnostico_aviso) + "\n\n" + copiable)
                .setNegativeButton(android.R.string.ok, null)
                .setPositiveButton(R.string.diagnostico_copiar, (d, b) -> {
                    android.content.ClipboardManager cp = (android.content.ClipboardManager)
                            getSystemService(CLIPBOARD_SERVICE);
                    if (cp == null) return;
                    cp.setPrimaryClip(android.content.ClipData.newPlainText(
                            getString(R.string.diagnostico_titulo), copiable));
                    avisar(getString(R.string.diagnostico_copiado));
                })
                .show();
    }

    private void avisar(String mensaje) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show();
    }
}
