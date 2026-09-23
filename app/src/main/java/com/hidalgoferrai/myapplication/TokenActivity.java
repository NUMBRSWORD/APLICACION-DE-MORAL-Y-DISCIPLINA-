package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.CircularProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Último paso del acceso: activación explícita, recuperación y verificación MFA. */
public class TokenActivity extends AppCompatActivity {
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_USUARIO_ID = "usuario_id";
    public static final String EXTRA_SESION = "sesion";

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler reloj = new Handler(Looper.getMainLooper());
    private TextView tvCodigo, tvRestante, tvEstado;
    private CircularProgressIndicator anillo;
    private MaterialButton btnEntrar;
    private TextInputLayout layoutCodigo;
    private TextInputEditText etCodigo;
    private String token, usuarioId, sesion, secreto, factorId;
    private boolean modoIngreso, externo, ocupado, listo, sesionVerificada;
    private Runnable reintento;
    private final androidx.activity.result.ActivityResultLauncher<Intent> codigosRespaldo =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), resultado -> mostrarListo());

    private final Runnable tic = new Runnable() {
        @Override public void run() {
            if (!listo || secreto == null || ocupado) return;
            try {
                String codigo = Totp.codigo(secreto);
                tvCodigo.setText(getString(R.string.token_codigo_formato,
                        codigo.substring(0, 3), codigo.substring(3)));
                int restantes = Totp.segundosRestantes();
                tvRestante.setText(getResources().getQuantityString(
                        R.plurals.token_expira_plural, restantes, restantes));
                anillo.setProgress(restantes, true);
                reloj.postDelayed(this, 1000);
            } catch (RuntimeException e) {
                listo = false;
                mostrarError(e, TokenActivity.this::preparar);
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getIntent().getStringExtra(EXTRA_TOKEN);
        usuarioId = getIntent().getStringExtra(EXTRA_USUARIO_ID);
        sesion = getIntent().getStringExtra(EXTRA_SESION);
        if (usuarioId == null) usuarioId = Perfil.usuarioId(this);
        if (usuarioId == null) { finish(); return; }
        modoIngreso = token != null && sesion != null;
        EdgeToEdge.enable(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_token);
        Diseno.bordes(this, true);
        Diseno.pasos(this, 3);
        tvCodigo = findViewById(R.id.tvCodigo);
        tvRestante = findViewById(R.id.tvRestante);
        tvEstado = findViewById(R.id.tvEstadoToken);
        anillo = findViewById(R.id.anillo);
        btnEntrar = findViewById(R.id.btnEntrar);
        layoutCodigo = findViewById(R.id.layoutCodigoExterno);
        etCodigo = findViewById(R.id.etCodigoExterno);
        btnEntrar.setOnClickListener(v -> {
            if (!ocupado) {
                if (listo || externo) verificar(); else activar();
            }
        });
        findViewById(R.id.btnReintentar).setOnClickListener(v -> {
            if (!ocupado && reintento != null) reintento.run();
        });
        findViewById(R.id.btnPerdiTelefono).setOnClickListener(v -> {
            if (!ocupado) pedirCodigoRecuperacion();
        });
        MaterialButton volver = findViewById(R.id.btnVolver);
        volver.setText(modoIngreso ? R.string.token_volver_politicas : R.string.volver_inicio);
        volver.setOnClickListener(v -> finish());
        findViewById(R.id.btnAccederDeNuevo).setOnClickListener(v -> {
            SesionActual.borrar();
            AlmacenSeguro.borrarRefresco(this);
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        secreto = AlmacenSeguro.secreto(this, usuarioId);
        factorId = AlmacenSeguro.factorId(this, usuarioId);
        if (!modoIngreso && secreto != null && AlmacenSeguro.tokenVerificado(this, usuarioId)) {
            mostrarListo();
        } else preparar();
    }

    @Override protected void onStart() {
        super.onStart();
        if (listo && !ocupado) iniciarReloj();
    }

    @Override protected void onStop() {
        reloj.removeCallbacks(tic);
        super.onStop();
    }

    @Override protected void onDestroy() {
        reloj.removeCallbacks(tic);
        hilo.shutdown();
        super.onDestroy();
    }

    private void publicar(Runnable accion) {
        runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) accion.run(); });
    }

    /** Consulta de solo lectura: abrir la pantalla no inscribe ni activa factores. */
    private void preparar() {
        if (ocupado) return;
        trabajando(R.string.token_comprobando);
        hilo.execute(() -> {
            try {
                if (token == null) {
                    String refresco = AlmacenSeguro.refresco(this);
                    if (refresco == null) throw new SupabaseAuth.ErrorApi(401, "{}");
                    actualizarSesion(SupabaseAuth.renovarSesion(refresco));
                }
                JSONArray factores = SupabaseApi.factores(token);
                String legacy = AlmacenSeguro.factorLegacy(this);
                String verificadoAjeno = null;
                boolean encontrado = false;
                boolean verificado = false;
                for (int i = 0; i < factores.length(); i++) {
                    JSONObject factor = factores.getJSONObject(i);
                    if (!"totp".equals(factor.optString("factor_type"))) continue;
                    String id = factor.getString("id");
                    boolean activo = "verified".equals(factor.optString("status"));
                    if (id.equals(legacy)) {
                        AlmacenSeguro.vincularTokenLegacy(this, usuarioId, id);
                        secreto = AlmacenSeguro.secreto(this, usuarioId);
                        factorId = AlmacenSeguro.factorId(this, usuarioId);
                    }
                    if (id.equals(factorId) && secreto != null) {
                        encontrado = true;
                        verificado = activo;
                    } else if (activo) verificadoAjeno = id;
                }
                if (encontrado) {
                    if (verificado) {
                        AlmacenSeguro.marcarTokenVerificado(this, usuarioId);
                        publicar(this::mostrarListo);
                    } else publicar(this::mostrarPorActivar);
                } else if (verificadoAjeno != null) {
                    factorId = verificadoAjeno;
                    secreto = null;
                    externo = true;
                    publicar(this::mostrarExterno);
                } else {
                    factorId = null;
                    secreto = null;
                    publicar(this::mostrarPorActivar);
                }
            } catch (Exception e) { publicar(() -> mostrarError(e, this::preparar)); }
        });
    }

    private void activar() {
        trabajando(R.string.token_activando);
        hilo.execute(() -> {
            try {
                if (factorId == null || secreto == null) {
                    JSONObject creado = SupabaseApi.crearToken(token,
                            "Faltos · " + UUID.randomUUID().toString().substring(0, 8));
                    factorId = creado.getString("id");
                    secreto = creado.getString("secreto");
                    // El pendiente cifrado permite reintentar sin duplicarlo ni perder el secreto.
                }
                AlmacenSeguro.guardarTokenPendiente(this, usuarioId, factorId, secreto);
                verificarEnServidor(Totp.codigo(secreto));
                AlmacenSeguro.marcarTokenVerificado(this, usuarioId);
                // Al activar se entregan los códigos de respaldo: son la única salida propia
                // si después se pierde el teléfono.
                publicar(() -> {
                    ocupado = false;
                    codigosRespaldo.launch(new Intent(this, RecuperacionActivity.class)
                            .putExtra(RecuperacionActivity.EXTRA_TOKEN, token));
                });
            } catch (Exception e) { publicar(() -> mostrarError(e, this::activar)); }
        });
    }

    /** Pide un código de respaldo y, si es válido, deja el token listo para activarse de nuevo. */
    private void pedirCodigoRecuperacion() {
        final com.google.android.material.textfield.TextInputEditText campo =
                new com.google.android.material.textfield.TextInputEditText(this);
        campo.setId(R.id.etCodigoRecuperacion);
        campo.setHint(R.string.recuperar_campo);
        campo.setSingleLine(true);
        int margen = Math.round(getResources().getDisplayMetrics().density * 24);
        android.widget.FrameLayout caja = new android.widget.FrameLayout(this);
        caja.setPadding(margen, margen / 2, margen, 0);
        caja.addView(campo);
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.recuperar_titulo)
                .setMessage(R.string.recuperar_mensaje)
                .setView(caja)
                .setNegativeButton(R.string.eliminar_cancelar, null)
                .setPositiveButton(R.string.recuperar_boton, (dialogo, boton) ->
                        usarCodigoRecuperacion(campo.getText() == null ? "" : campo.getText().toString()))
                .show();
    }

    private void usarCodigoRecuperacion(String codigo) {
        trabajando(R.string.recuperar_comprobando);
        hilo.execute(() -> {
            try {
                String estado = SupabaseApi.usarCodigoRecuperacion(token, codigo);
                if (!"ok".equals(estado)) {
                    publicar(() -> {
                        detenerCarga();
                        mostrarExterno();
                        Diseno.error(this, R.string.recuperar_titulo,
                                "bloqueado".equals(estado) ? R.string.recuperar_bloqueado
                                        : R.string.recuperar_invalido, false);
                    });
                    return;
                }
                // El servidor ya borró el factor: se empieza de cero en este teléfono.
                AlmacenSeguro.borrar(this);
                secreto = null;
                factorId = null;
                externo = false;
                publicar(() -> {
                    layoutCodigo.setVisibility(View.GONE);
                    activar();
                });
            } catch (Exception e) {
                publicar(() -> mostrarError(e, this::pedirCodigoRecuperacion));
            }
        });
    }

    private void verificar() {
        if (sesionVerificada && modoIngreso) { irAlInicio(); return; }
        String codigoExterno = etCodigo.getText() == null ? "" : etCodigo.getText().toString().trim();
        layoutCodigo.setError(null);
        if (externo && !codigoExterno.matches("[0-9]{6}")) {
            layoutCodigo.setError(getString(R.string.token_error_seis));
            etCodigo.requestFocus();
            return;
        }
        trabajando(R.string.token_verificando);
        hilo.execute(() -> {
            try {
                verificarEnServidor(externo ? codigoExterno : Totp.codigo(secreto));
                publicar(() -> {
                    ocupado = false;
                    if (modoIngreso) {
                        irAlInicio();
                    } else if (externo) finish();
                    else mostrarListo();
                });
            } catch (Exception e) { publicar(() -> mostrarError(e, this::verificar)); }
        });
    }

    private void verificarEnServidor(String codigo) throws Exception {
        String desafio = SupabaseApi.desafiar(token, factorId);
        actualizarSesion(SupabaseApi.verificar(token, factorId, desafio, codigo));
        sesionVerificada = true;
    }

    private void irAlInicio() {
        startActivity(new Intent(this, InicioActivity.class)
                .putExtra(InicioActivity.EXTRA_SESION, sesion)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private void actualizarSesion(JSONObject nueva) throws Exception {
        token = nueva.getString("access_token");
        sesion = nueva.toString();
        SesionActual.recibir(this, sesion);
        AlmacenSeguro.guardarRefresco(this, nueva.optString("refresh_token", null));
    }

    private void trabajando(int mensaje) {
        ocupado = true;
        reloj.removeCallbacks(tic);
        findViewById(R.id.estadoPanel).setVisibility(View.GONE);
        findViewById(R.id.btnAccederDeNuevo).setVisibility(View.GONE);
        tvEstado.setText(R.string.token_estado_preparando);
        tvCodigo.setText(R.string.token_activando_corto);
        tvRestante.setText(mensaje);
        anillo.setIndeterminate(true);
        btnEntrar.setEnabled(false);
        etCodigo.setEnabled(false);
        findViewById(R.id.btnReintentar).setEnabled(false);
    }

    private void detenerCarga() {
        ocupado = false;
        anillo.setIndeterminate(false);
        anillo.setProgress(0);
        etCodigo.setEnabled(true);
        findViewById(R.id.btnReintentar).setEnabled(true);
    }

    private void mostrarPorActivar() {
        detenerCarga();
        tvEstado.setText(R.string.token_estado_pendiente);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_listo_activar);
        btnEntrar.setText(R.string.token_activar_boton);
        btnEntrar.setEnabled(true);
    }

    private void mostrarListo() {
        detenerCarga();
        listo = true;
        tvEstado.setText(R.string.token_estado_seguro);
        btnEntrar.setText(R.string.token_continuar);
        btnEntrar.setVisibility(modoIngreso ? View.VISIBLE : View.GONE);
        btnEntrar.setEnabled(true);
        iniciarReloj();
    }

    private void mostrarExterno() {
        detenerCarga();
        tvEstado.setText(R.string.token_estado_externo);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_otro_dispositivo);
        layoutCodigo.setVisibility(View.VISIBLE);
        btnEntrar.setText(R.string.token_continuar);
        btnEntrar.setEnabled(true);
        // Salida propia para quien ya no tiene el teléfono donde activó el token.
        findViewById(R.id.btnPerdiTelefono).setVisibility(View.VISIBLE);
    }

    private void iniciarReloj() {
        reloj.removeCallbacks(tic);
        if (secreto != null) reloj.post(tic);
    }

    private void mostrarError(Exception error, Runnable accion) {
        detenerCarga();
        reloj.removeCallbacks(tic);
        reintento = accion;
        tvEstado.setText(R.string.token_estado_error);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_carga_detenida);
        btnEntrar.setEnabled(false);
        Diseno.error(this, R.string.token_error_titulo, Errores.mensaje(error), !Errores.esSesion(error));
        findViewById(R.id.btnAccederDeNuevo).setVisibility(Errores.esSesion(error) ? View.VISIBLE : View.GONE);
    }
}
