package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ScrollView;
import java.util.HashSet;
import java.util.ArrayList;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Lectura y firma de las políticas institucionales. Es un paso obligatorio: el token
 * solo se activa después de firmar, igual que la banca no entrega la clave sin contrato.
 */
public class FirmaActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_USUARIO_ID = "usuario_id";
    public static final String EXTRA_SESION = "sesion";

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    private LinearLayout contenedor;
    private LinearProgressIndicator progreso;
    private MaterialButton btnContinuar;
    private TextInputLayout layoutNombre, layoutCargo;
    private TextInputEditText etGrado, etNombre, etCargo;

    private String token, usuarioId, sesion;
    private JSONArray documentos;
    private Set<String> firmadas;
    private final Set<String> leidas = new HashSet<>();
    private boolean cargando, firmando;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getIntent().getStringExtra(EXTRA_TOKEN);
        usuarioId = getIntent().getStringExtra(EXTRA_USUARIO_ID);
        sesion = getIntent().getStringExtra(EXTRA_SESION);
        if (token == null || usuarioId == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_firma);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        contenedor = findViewById(R.id.contenedor);
        progreso = findViewById(R.id.progreso);
        btnContinuar = findViewById(R.id.btnContinuar);
        layoutNombre = findViewById(R.id.layoutNombre);
        layoutCargo = findViewById(R.id.layoutCargo);
        etGrado = findViewById(R.id.etGrado);
        etNombre = findViewById(R.id.etNombre);
        etCargo = findViewById(R.id.etCargo);

        if (savedInstanceState != null) {
            ArrayList<String> guardadas = savedInstanceState.getStringArrayList("leidas");
            if (guardadas != null) leidas.addAll(guardadas);
        }
        Diseno.pasos(this, 2);
        btnContinuar.setOnClickListener(v -> irAlToken());
        findViewById(R.id.btnReintentar).setOnClickListener(v -> cargar());
        findViewById(R.id.btnVolver).setOnClickListener(v -> {
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
            finish();
        });
        cargar();
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        outState.putStringArrayList("leidas", new ArrayList<>(leidas));
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void cargar() {
        if (cargando || firmando) return;
        cargando = true;
        btnContinuar.setEnabled(false);
        findViewById(R.id.estadoPanel).setVisibility(View.GONE);
        progreso.setVisibility(View.VISIBLE);
        hilo.execute(() -> {
            try {
                JSONArray docs = SupabaseApi.documentosInstitucionales(token);
                Set<String> mias = SupabaseApi.misFirmas(token, usuarioId);
                JSONObject solicitud = SupabaseApi.miSolicitud(token, usuarioId);
                // Si no declaró solicitud (cuenta anterior al registro), se toma de su firma.
                JSONObject firmada = solicitud != null ? null
                        : SupabaseApi.miIdentidadFirmada(token, usuarioId);
                String gradoPerfil = solicitud != null
                        ? solicitud.optString("grado", null)
                        : firmada == null ? null : firmada.optString("firmante_grado", null);
                String nombrePerfil = solicitud != null
                        ? (solicitud.optString("nombres", "") + " "
                        + solicitud.optString("apellidos", "")).trim()
                        : firmada == null ? null : firmada.optString("firmante_nombre", null);
                Perfil.guardar(this, usuarioId, gradoPerfil, nombrePerfil,
                        SupabaseApi.rol(token, usuarioId));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    cargando = false;
                    documentos = docs;
                    firmadas = mias;
                    if (solicitud != null) {
                        if (texto(etGrado).isEmpty()) {
                            etGrado.setText(solicitud.optString("grado", ""));
                        }
                        if (texto(etNombre).isEmpty()) {
                            etNombre.setText(nombrePerfil);
                        }
                    } else if (firmada != null) {
                        etGrado.setText(gradoPerfil);
                        etNombre.setText(nombrePerfil);
                    }
                    pintar();
                });
            } catch (IOException | JSONException | RuntimeException e) {
                avisarError(R.string.firma_error_carga, e);
            }
        });
    }

    private void pintar() {
        progreso.setVisibility(View.GONE);
        contenedor.removeAllViews();
        if (documentos == null || documentos.length() == 0) {
            btnContinuar.setEnabled(false);
            Diseno.error(this, R.string.firma_sin_documentos, R.string.firma_sin_documentos_texto, true);
            return;
        }
        int pendientes = 0;
        for (int i = 0; i < documentos.length(); i++) {
            JSONObject doc = documentos.optJSONObject(i);
            if (doc == null) {
                continue;
            }
            int version = doc.optInt("version", 1);
            boolean yaFirmada = firmadas.contains(doc.optString("id") + ":" + version);
            if (!yaFirmada) {
                pendientes++;
            }
            contenedor.addView(tarjeta(doc, version, yaFirmada));
        }
        boolean todoFirmado = pendientes == 0;
        btnContinuar.setEnabled(todoFirmado && !firmando);
        ((TextView) findViewById(R.id.tvResumenFirmas)).setText(
                getString(R.string.firma_resumen, documentos.length() - pendientes, documentos.length()));
        LinearProgressIndicator avance = findViewById(R.id.avanceFirmas);
        avance.setMax(documentos.length());
        avance.setProgress(documentos.length() - pendientes, true);
        ((TextView) findViewById(R.id.tvPieFirma)).setText(todoFirmado
                ? R.string.firma_todo_listo : R.string.firma_revisar_aviso);
        for (View campo : new View[]{etGrado, etNombre, etCargo}) {
            campo.setEnabled(!todoFirmado && !firmando);
        }
    }

    private View tarjeta(JSONObject doc, int version, boolean yaFirmada) {
        MaterialCardView tarjeta = new MaterialCardView(this);
        tarjeta.setRadius(dp(22));
        tarjeta.setCardElevation(dp(1));
        tarjeta.setStrokeWidth(dp(1));
        tarjeta.setStrokeColor(getColor(yaFirmada ? R.color.verde_pnp : R.color.borde_suave));
        tarjeta.setCardBackgroundColor(getColor(R.color.superficie_tarjeta));
        LinearLayout caja = new LinearLayout(this);
        caja.setOrientation(LinearLayout.VERTICAL);
        caja.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(16);
        tarjeta.setLayoutParams(lp);

        TextView titulo = new TextView(this);
        titulo.setText(doc.optString("titulo"));
        titulo.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
        titulo.setTextColor(getColor(R.color.texto_principal));
        caja.addView(titulo);

        TextView sub = new TextView(this);
        sub.setText(getString(R.string.firma_version, version));
        sub.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodySmall);
        caja.addView(sub);

        String clave = doc.optString("id") + ":" + version;
        MaterialButton leer = new MaterialButton(this);
        leer.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        leer.setTextColor(getColor(R.color.verde_pnp));
        leer.setIconTint(android.content.res.ColorStateList.valueOf(getColor(R.color.verde_pnp)));
        leer.setElevation(0);
        leer.setText(leidas.contains(clave) ? R.string.firma_releer : R.string.firma_leer);
        leer.setIconResource(R.drawable.ic_documento);
        leer.setOnClickListener(v -> leerDocumento(doc, version));
        leer.setEnabled(!firmando);
        caja.addView(leer);

        if (yaFirmada) {
            TextView ok = new TextView(this);
            ok.setText(R.string.firma_firmada);
            ok.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleSmall);
            ok.setTextColor(getColor(R.color.verde_pnp));
            LinearLayout.LayoutParams lpo = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpo.topMargin = dp(12);
            ok.setLayoutParams(lpo);
            caja.addView(ok);
        } else {
            MaterialButton boton = new MaterialButton(this);
            boton.setText(R.string.firma_boton);
            boton.setEnabled(leidas.contains(clave) && !firmando);
            LinearLayout.LayoutParams lpb = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
            lpb.topMargin = dp(12);
            boton.setLayoutParams(lpb);
            boton.setOnClickListener(v -> firmar(doc.optString("id"), version, boton));
            caja.addView(boton);
        }
        tarjeta.addView(caja);
        return tarjeta;
    }

    private void firmar(String documentoId, int version, MaterialButton boton) {
        if (firmando || cargando || !leidas.contains(documentoId + ":" + version)) return;
        layoutNombre.setError(null);
        layoutCargo.setError(null);
        String nombre = texto(etNombre);
        String cargo = texto(etCargo);
        if (nombre.isEmpty()) {
            layoutNombre.setError(getString(R.string.firma_error_nombre));
            etNombre.requestFocus();
            return;
        }
        if (cargo.isEmpty()) {
            layoutCargo.setError(getString(R.string.firma_error_cargo));
            etCargo.requestFocus();
            return;
        }
        String grado = texto(etGrado);
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.firma_confirmar_titulo)
                .setMessage(getString(R.string.firma_confirmar_texto, nombre, cargo, version))
                .setNegativeButton(R.string.cancelar, null)
                .setPositiveButton(R.string.firma_confirmar_boton, (dialogo, cual) ->
                        guardarFirma(documentoId, version, nombre, grado, cargo))
                .show();
    }

    private void guardarFirma(String documentoId, int version, String nombre, String grado, String cargo) {
        if (firmando) return;
        firmando = true;
        pintar();
        progreso.setVisibility(View.VISIBLE);
        hilo.execute(() -> {
            try {
                SupabaseApi.firmar(token, documentoId, version, usuarioId, nombre, grado, cargo);
                Perfil.guardar(this, usuarioId, grado, nombre, null);
                Set<String> mias = SupabaseApi.misFirmas(token, usuarioId);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    firmando = false;
                    firmadas = mias;
                    Toast.makeText(this, R.string.firma_ok, Toast.LENGTH_SHORT).show();
                    pintar();
                });
            } catch (IOException | JSONException | RuntimeException e) {
                avisarError(R.string.firma_error_firmar, e);
            }
        });
    }

    private void leerDocumento(JSONObject doc, int version) {
        ScrollView scroll = new ScrollView(this);
        TextView contenido = new TextView(this);
        contenido.setText(doc.optString("contenido"));
        contenido.setTextSize(16);
        contenido.setTextColor(getColor(R.color.texto_principal));
        contenido.setLineSpacing(dp(6), 1f);
        contenido.setTextIsSelectable(true);
        contenido.setPadding(dp(24), dp(16), dp(24), dp(24));
        scroll.addView(contenido);
        new MaterialAlertDialogBuilder(this)
                .setTitle(doc.optString("titulo"))
                .setView(scroll)
                .setNegativeButton(R.string.terminos_cerrar, null)
                .setPositiveButton(R.string.firma_he_leido, (d, which) -> {
                    leidas.add(doc.optString("id") + ":" + version);
                    pintar();
                }).show();
    }

    private void irAlToken() {
        if (cargando || firmando || documentos == null || documentos.length() == 0) return;
        for (int i = 0; i < documentos.length(); i++) {
            JSONObject doc = documentos.optJSONObject(i);
            if (doc == null || !firmadas.contains(doc.optString("id") + ":" + doc.optInt("version", 1))) return;
        }
        startActivity(new Intent(this, TokenActivity.class)
                .putExtra(TokenActivity.EXTRA_TOKEN, token)
                .putExtra(TokenActivity.EXTRA_USUARIO_ID, usuarioId)
                .putExtra(TokenActivity.EXTRA_SESION, sesion));
    }

    private void avisarError(int plantilla, Exception e) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            cargando = false;
            firmando = false;
            progreso.setVisibility(View.GONE);
            btnContinuar.setEnabled(false);
            Diseno.error(this, R.string.firma_error_titulo, Errores.mensaje(e), true);
        });
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private static String texto(TextInputEditText campo) {
        return campo.getText() == null ? "" : campo.getText().toString().trim();
    }
}
