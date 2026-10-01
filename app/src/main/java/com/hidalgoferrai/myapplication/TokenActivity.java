package com.hidalgoferrai.myapplication;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
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

/**
 * Último paso del acceso: activación del Token Digital, recuperación y verificación.
 *
 * Esta aplicación YA NO genera el código. Lo guarda una app de códigos del propio
 * teléfono (Google Authenticator, Microsoft Authenticator, Contraseñas en iPhone),
 * igual que hace la web con su QR. El motivo es práctico: esas apps respaldan las
 * cuentas en la nube, así que perder el teléfono deja de significar perder el token
 * —que era el problema real— y la persona usa la misma app para todo lo demás.
 *
 * Aquí solo se crea el factor en el servidor, se entrega la URI otpauth:// a esa app
 * y se verifica el código de seis dígitos que la persona escribe.
 */
public class TokenActivity extends AppCompatActivity {
    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_USUARIO_ID = "usuario_id";
    public static final String EXTRA_SESION = "sesion";

    private static final String PLAY_AUTENTICADOR =
            "https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2";

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler principal = new Handler(Looper.getMainLooper());
    private TextView tvCodigo, tvRestante, tvEstado;
    private CircularProgressIndicator anillo;
    private MaterialButton btnEntrar, btnAutenticador, btnCopiarClave;
    private TextInputLayout layoutCodigo;
    private TextInputEditText etCodigo;
    private String token, usuarioId, sesion, factorId;
    /** Clave en claro del factor recién creado: solo vive mientras dura la activación. */
    private String secretoNuevo, uriNueva;
    /** Token anterior (el que generaba esta app): se retira cuando el nuevo ya está verificado. */
    private String factorAnterior;
    /** Clave guardada por versiones anteriores dentro de la aplicación. */
    private String secretoLocal;
    private boolean modoIngreso, activando, ocupado, sesionVerificada;
    private Runnable reintento;
    private final androidx.activity.result.ActivityResultLauncher<Intent> codigosRespaldo =
            registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts
                    .StartActivityForResult(), resultado -> terminarActivacion());

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getIntent().getStringExtra(EXTRA_TOKEN);
        usuarioId = getIntent().getStringExtra(EXTRA_USUARIO_ID);
        sesion = getIntent().getStringExtra(EXTRA_SESION);
        SesionActual.restaurar(this, sesion);
        if (usuarioId == null) usuarioId = Perfil.usuarioId(this);
        if (usuarioId == null) { finish(); return; }
        modoIngreso = token != null && sesion != null;
        EdgeToEdge.enable(this);
        // La pantalla muestra la clave del token: no debe salir en capturas.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_token);
        Diseno.bordes(this, true);
        Diseno.pasos(this, 3);
        tvCodigo = findViewById(R.id.tvCodigo);
        tvRestante = findViewById(R.id.tvRestante);
        tvEstado = findViewById(R.id.tvEstadoToken);
        anillo = findViewById(R.id.anillo);
        btnEntrar = findViewById(R.id.btnEntrar);
        btnAutenticador = findViewById(R.id.btnAutenticador);
        btnCopiarClave = findViewById(R.id.btnCopiarClave);
        layoutCodigo = findViewById(R.id.layoutCodigoExterno);
        etCodigo = findViewById(R.id.etCodigoExterno);
        btnEntrar.setOnClickListener(v -> {
            if (ocupado) return;
            if (activando || factorId != null) verificar(); else activar();
        });
        btnAutenticador.setOnClickListener(v -> abrirAutenticador());
        btnCopiarClave.setOnClickListener(v -> copiarClave());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> {
            if (!ocupado && reintento != null) reintento.run();
        });
        findViewById(R.id.btnPerdiTelefono).setOnClickListener(v -> {
            if (!ocupado) pedirCodigoRecuperacion();
        });
        MaterialButton volver = findViewById(R.id.btnVolver);
        volver.setText(modoIngreso ? R.string.token_volver_politicas : R.string.volver_inicio);
        volver.setOnClickListener(v -> {
            if (activando) cancelarActivacion(); else finish();
        });
        findViewById(R.id.btnAccederDeNuevo).setOnClickListener(v -> {
            SesionActual.cerrarLocal(this);
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
        secretoLocal = AlmacenSeguro.secreto(this, usuarioId);
        factorId = AlmacenSeguro.factorId(this, usuarioId);
        preparar();
    }

    @Override protected void onDestroy() {
        principal.removeCallbacksAndMessages(null);
        hilo.shutdown();
        secretoNuevo = null;
        secretoLocal = null;
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
                renovarSesion();
                JSONArray factores = SupabaseApi.factores(token);
                String legacy = AlmacenSeguro.factorLegacy(this);
                String verificado = null;
                for (int i = 0; i < factores.length(); i++) {
                    JSONObject factor = factores.getJSONObject(i);
                    if (!"totp".equals(factor.optString("factor_type"))) continue;
                    String id = factor.getString("id");
                    if (id.equals(legacy)) {
                        AlmacenSeguro.vincularTokenLegacy(this, usuarioId, id);
                        secretoLocal = AlmacenSeguro.secreto(this, usuarioId);
                    }
                    if ("verified".equals(factor.optString("status"))) verificado = id;
                }
                final String activo = verificado;
                if (activo == null) {
                    factorId = null;
                    publicar(this::mostrarPorActivar);
                    return;
                }
                factorId = activo;
                boolean esElLocal = activo.equals(AlmacenSeguro.factorId(this, usuarioId))
                        && secretoLocal != null;
                if (esElLocal) {
                    AlmacenSeguro.marcarTokenVerificado(this, usuarioId);
                    // Token de versiones anteriores: se usa su clave una última vez para
                    // entrar y acto seguido se ofrece mudarlo a la app de códigos.
                    publicar(this::entrarConTokenDeLaApp);
                } else publicar(this::mostrarPedirCodigo);
            } catch (Exception e) { publicar(() -> mostrarError(e, this::preparar)); }
        });
    }

    // ---------------------------------------------------------------------
    // Activación con app de códigos
    // ---------------------------------------------------------------------

    private void activar() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.token_autenticador_titulo)
                .setMessage(R.string.token_autenticador_pasos)
                .setNegativeButton(R.string.eliminar_cancelar, null)
                .setPositiveButton(R.string.token_autenticador_entendido, (d, b) -> crearFactor())
                .show();
    }

    private void crearFactor() {
        trabajando(R.string.token_activando);
        hilo.execute(() -> {
            try {
                renovarSesion();
                JSONObject creado = SupabaseApi.crearToken(token,
                        "Faltos · " + UUID.randomUUID().toString().substring(0, 8));
                factorId = creado.getString("id");
                secretoNuevo = creado.getString("secreto");
                uriNueva = creado.optString("uri", "");
                publicar(() -> {
                    activando = true;
                    mostrarActivando();
                    abrirAutenticador();
                });
            } catch (Exception e) { publicar(() -> mostrarError(e, this::crearFactor)); }
        });
    }

    /**
     * Entrega la cuenta a la app de códigos. Si no hay ninguna instalada, se enseña la
     * clave para agregarla a mano y un enlace para instalarla: quedarse sin salida aquí
     * dejaría a la persona con un factor creado y sin poder verificarlo.
     */
    private void abrirAutenticador() {
        if (uriNueva != null && !uriNueva.isEmpty()) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uriNueva))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return;
            } catch (ActivityNotFoundException ignorado) { /* Se resuelve a mano abajo. */ }
        }
        mostrarClaveAMano();
    }

    /**
     * Sin app de códigos instalada: la clave se enseña en la propia pantalla, con un
     * botón para copiarla y otro para instalar la app. Se muestra aquí y no en un
     * diálogo porque la persona la necesita a la vista mientras la escribe en la otra
     * aplicación.
     */
    private void mostrarClaveAMano() {
        if (secretoNuevo == null) return;
        tvEstado.setText(R.string.token_sin_autenticador_titulo);
        tvRestante.setText(getString(R.string.token_sin_autenticador) + "\n\n" + secretoNuevo);
        btnCopiarClave.setVisibility(View.VISIBLE);
        btnAutenticador.setText(R.string.token_instalar_autenticador);
        btnAutenticador.setVisibility(View.VISIBLE);
        btnAutenticador.setOnClickListener(v -> abrirPlayStore());
    }

    private void copiarClave() {
        if (secretoNuevo == null) return;
        ClipboardManager portapapeles = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (portapapeles == null) return;
        ClipData dato = ClipData.newPlainText(getString(R.string.token_clave_manual), secretoNuevo);
        // Marca del sistema para que el portapapeles no muestre la clave en vistas previas.
        dato.getDescription().getExtras();
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        dato.getDescription().setExtras(extras);
        portapapeles.setPrimaryClip(dato);
        android.widget.Toast.makeText(this, R.string.token_clave_copiada, android.widget.Toast.LENGTH_LONG).show();
    }

    private void abrirPlayStore() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(PLAY_AUTENTICADOR))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        } catch (ActivityNotFoundException e) {
            Diseno.error(this, R.string.token_sin_autenticador_titulo, R.string.token_sin_play, false);
        }
    }

    /** Si abandona a medias, el factor sin verificar se retira: no se deja basura en la cuenta. */
    private void cancelarActivacion() {
        final String aRetirar = factorId;
        activando = false;
        factorId = factorAnterior;
        factorAnterior = null;
        secretoNuevo = null;
        uriNueva = null;
        if (aRetirar == null) { finish(); return; }
        trabajando(R.string.token_activando);
        hilo.execute(() -> {
            try { renovarSesion(); SupabaseApi.eliminarFactor(token, aRetirar); }
            catch (Exception ignorado) { /* Sin verificar no sirve para entrar; no se insiste. */ }
            publicar(() -> {
                detenerCarga();
                android.widget.Toast.makeText(this, R.string.token_activacion_cancelada, android.widget.Toast.LENGTH_LONG).show();
                finish();
            });
        });
    }

    // ---------------------------------------------------------------------
    // Verificación
    // ---------------------------------------------------------------------

    private void verificar() {
        if (sesionVerificada && modoIngreso && !activando) { irAlInicio(); return; }
        String codigo = etCodigo.getText() == null ? "" : etCodigo.getText().toString().trim();
        layoutCodigo.setError(null);
        if (!codigo.matches("[0-9]{6}")) {
            layoutCodigo.setError(getString(R.string.token_error_seis));
            etCodigo.requestFocus();
            return;
        }
        trabajando(R.string.token_verificando);
        hilo.execute(() -> {
            try {
                verificarEnServidor(codigo);
                if (activando) {
                    AlmacenSeguro.marcarTokenEnAppDeCodigos(this, usuarioId, factorId);
                    retirarTokenAnterior();
                    publicar(() -> {
                        ocupado = false;
                        secretoNuevo = null;
                        uriNueva = null;
                        codigosRespaldo.launch(new Intent(this, RecuperacionActivity.class)
                                .putExtra(RecuperacionActivity.EXTRA_TOKEN, token));
                    });
                } else publicar(() -> { ocupado = false; if (modoIngreso) irAlInicio(); else finish(); });
            } catch (Exception e) { publicar(() -> mostrarError(e, this::verificar)); }
        });
    }

    /**
     * El token viejo solo se retira cuando el nuevo ya quedó verificado. Al revés
     * dejaría a la persona sin ninguno si algo fallara en medio.
     */
    private void retirarTokenAnterior() {
        if (factorAnterior == null) return;
        try {
            SupabaseApi.eliminarFactor(token, factorAnterior);
            AlmacenSeguro.borrar(this);
            secretoLocal = null;
        } catch (java.io.IOException e) {
            publicar(() -> new AlertDialog.Builder(this)
                    .setMessage(R.string.token_anterior_pendiente)
                    .setPositiveButton(android.R.string.ok, null).show());
        }
        factorAnterior = null;
    }

    private void terminarActivacion() {
        activando = false;
        if (modoIngreso) irAlInicio(); else finish();
    }

    private void verificarEnServidor(String codigo) throws Exception {
        renovarSesion();
        String desafio = SupabaseApi.desafiar(token, factorId);
        actualizarSesion(SupabaseApi.verificar(token, factorId, desafio, codigo));
        sesionVerificada = true;
    }

    // ---------------------------------------------------------------------
    // Mudanza del token que generaba la propia aplicación
    // ---------------------------------------------------------------------

    /**
     * Quien ya tenía el token dentro de la app entra sin escribir nada: la clave local
     * sirve esta última vez. Después se le propone pasarlo a su app de códigos, y si
     * dice que no, conserva el que tiene: nadie se queda fuera por no decidir ahora.
     */
    private void entrarConTokenDeLaApp() {
        trabajando(R.string.token_verificando);
        hilo.execute(() -> {
            try {
                verificarEnServidor(Totp.codigo(secretoLocal));
                publicar(() -> { ocupado = false; proponerMudanza(); });
            } catch (Exception e) {
                // Si la clave local ya no vale, se pide el código como a cualquiera.
                publicar(() -> { ocupado = false; mostrarPedirCodigo(); });
            }
        });
    }

    private void proponerMudanza() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.token_mudanza_titulo)
                .setMessage(R.string.token_mudanza_mensaje)
                .setCancelable(false)
                .setNegativeButton(R.string.token_mudanza_despues, (d, b) -> {
                    android.widget.Toast.makeText(this, R.string.token_mudanza_pendiente, android.widget.Toast.LENGTH_LONG).show();
                    if (modoIngreso) irAlInicio(); else finish();
                })
                .setPositiveButton(R.string.token_mudanza_continuar, (d, b) -> {
                    factorAnterior = factorId;
                    factorId = null;
                    activar();
                })
                .show();
    }

    // ---------------------------------------------------------------------
    // Recuperación
    // ---------------------------------------------------------------------

    /** Pide un código de respaldo y, si es válido, deja el token listo para activarse de nuevo. */
    private void pedirCodigoRecuperacion() {
        final TextInputEditText campo = new TextInputEditText(this);
        campo.setId(R.id.etCodigoRecuperacion);
        campo.setHint(R.string.recuperar_campo);
        campo.setSingleLine(true);
        int margen = Math.round(getResources().getDisplayMetrics().density * 24);
        android.widget.FrameLayout caja = new android.widget.FrameLayout(this);
        caja.setPadding(margen, margen / 2, margen, 0);
        caja.addView(campo);
        new AlertDialog.Builder(this)
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
                renovarSesion();
                String estado = SupabaseApi.usarCodigoRecuperacion(token, codigo);
                if (!"ok".equals(estado)) {
                    publicar(() -> {
                        detenerCarga();
                        mostrarPedirCodigo();
                        Diseno.error(this, R.string.recuperar_titulo,
                                "bloqueado".equals(estado) ? R.string.recuperar_bloqueado
                                        : R.string.recuperar_invalido, false);
                    });
                    return;
                }
                // El servidor ya borró el factor: se empieza de cero en este teléfono.
                AlmacenSeguro.borrar(this);
                secretoLocal = null;
                factorId = null;
                factorAnterior = null;
                publicar(() -> {
                    detenerCarga();
                    layoutCodigo.setVisibility(View.GONE);
                    activar();
                });
            } catch (Exception e) {
                publicar(() -> mostrarError(e, this::pedirCodigoRecuperacion));
            }
        });
    }

    // ---------------------------------------------------------------------
    // Navegación y pantalla
    // ---------------------------------------------------------------------

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
    }

    private void renovarSesion() throws Exception {
        token = SesionActual.token(this, usuarioId);
        sesion = SesionActual.obtener(this);
    }

    private void trabajando(int mensaje) {
        ocupado = true;
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
        activando = false;
        tvEstado.setText(R.string.token_estado_pendiente);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_listo_activar);
        layoutCodigo.setVisibility(View.GONE);
        btnAutenticador.setVisibility(View.GONE);
        btnCopiarClave.setVisibility(View.GONE);
        btnEntrar.setText(R.string.token_activar_boton);
        btnEntrar.setVisibility(View.VISIBLE);
        btnEntrar.setEnabled(true);
    }

    /** Activación en curso: la cuenta ya está en la app de códigos y falta el código. */
    private void mostrarActivando() {
        detenerCarga();
        tvEstado.setText(R.string.token_estado_pedir);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_pedir_codigo);
        layoutCodigo.setVisibility(View.VISIBLE);
        btnAutenticador.setText(R.string.token_abrir_autenticador);
        btnAutenticador.setOnClickListener(v -> abrirAutenticador());
        btnAutenticador.setVisibility(View.VISIBLE);
        btnCopiarClave.setVisibility(View.GONE);
        btnEntrar.setText(R.string.token_continuar);
        btnEntrar.setVisibility(View.VISIBLE);
        btnEntrar.setEnabled(true);
        findViewById(R.id.btnPerdiTelefono).setVisibility(View.GONE);
    }

    /** Ingreso normal: el código siempre lo escribe la persona desde su app. */
    private void mostrarPedirCodigo() {
        detenerCarga();
        activando = false;
        tvEstado.setText(R.string.token_estado_pedir);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_pedir_codigo);
        layoutCodigo.setVisibility(View.VISIBLE);
        btnAutenticador.setVisibility(View.GONE);
        btnCopiarClave.setVisibility(View.GONE);
        btnEntrar.setText(R.string.token_continuar);
        btnEntrar.setVisibility(View.VISIBLE);
        btnEntrar.setEnabled(true);
        // Salida propia para quien ya no tiene la app donde guardó el token.
        findViewById(R.id.btnPerdiTelefono).setVisibility(View.VISIBLE);
    }

    private void mostrarError(Exception error, Runnable accion) {
        detenerCarga();
        reintento = accion;
        tvEstado.setText(R.string.token_estado_error);
        tvCodigo.setText(R.string.token_bloqueado_icono);
        tvRestante.setText(R.string.token_carga_detenida);
        btnEntrar.setEnabled(false);
        Diseno.error(this, R.string.token_error_titulo, Errores.mensaje(error), !Errores.esSesion(error));
        findViewById(R.id.btnAccederDeNuevo).setVisibility(Errores.esSesion(error) ? View.VISIBLE : View.GONE);
    }
}
