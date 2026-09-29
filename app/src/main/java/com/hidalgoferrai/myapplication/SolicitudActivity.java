package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import org.json.JSONException;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Datos con los que el administrador verifica quién es la persona antes de aprobarla. */
public class SolicitudActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "token";
    public static final String EXTRA_USUARIO_ID = "usuario_id";
    public static final String EXTRA_EMAIL = "email";

    private static final String PATRON_NOMBRE = "\\p{L}+([ '\\-]\\p{L}+)*";
    private static final String PATRON_CIP = "\\d{4,12}";
    private static final String PATRON_DNI = "\\d{8}";
    private static final String PATRON_TELEFONO = "\\d{9}";

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();

    private TextInputLayout layoutGrado, layoutApellidos, layoutNombres, layoutCip, layoutDni,
            layoutTelefono;
    private TextInputEditText etGrado, etApellidos, etNombres, etCip, etDni, etTelefono;
    private MaterialButton btnEnviar;
    private LinearProgressIndicator progreso;
    private TextInputLayout primerError;

    private String token, usuarioId, email;
    private boolean ocupado;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        token = getIntent().getStringExtra(EXTRA_TOKEN);
        usuarioId = getIntent().getStringExtra(EXTRA_USUARIO_ID);
        email = getIntent().getStringExtra(EXTRA_EMAIL);
        if (token == null || usuarioId == null) {
            finish();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_solicitud);
        Diseno.bordes(this, true);

        layoutGrado = findViewById(R.id.layoutGrado);
        layoutApellidos = findViewById(R.id.layoutApellidos);
        layoutNombres = findViewById(R.id.layoutNombres);
        layoutCip = findViewById(R.id.layoutCip);
        layoutDni = findViewById(R.id.layoutDni);
        layoutTelefono = findViewById(R.id.layoutTelefono);
        etGrado = findViewById(R.id.etGrado);
        etApellidos = findViewById(R.id.etApellidos);
        etNombres = findViewById(R.id.etNombres);
        etCip = findViewById(R.id.etCip);
        etDni = findViewById(R.id.etDni);
        etTelefono = findViewById(R.id.etTelefono);
        btnEnviar = findViewById(R.id.btnEnviar);
        progreso = findViewById(R.id.progreso);

        ((TextView) findViewById(R.id.tvCuenta))
                .setText(getString(R.string.solicitud_cuenta, email == null ? "-" : email));

        limpiar(etGrado, layoutGrado);
        limpiar(etApellidos, layoutApellidos);
        limpiar(etNombres, layoutNombres);
        limpiar(etCip, layoutCip);
        limpiar(etDni, layoutDni);
        limpiar(etTelefono, layoutTelefono);

        btnEnviar.setOnClickListener(v -> enviar());
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void enviar() {
        if (ocupado) return;
        if (!validar()) {
            if (primerError != null && primerError.getEditText() != null) {
                primerError.getEditText().requestFocus();
            }
            return;
        }
        cargando(true);
        String grado = texto(etGrado).trim().toUpperCase(Locale.ROOT);
        String apellidos = texto(etApellidos).trim();
        String nombres = texto(etNombres).trim();
        String cip = texto(etCip).trim();
        String dni = texto(etDni).trim();
        String telefono = texto(etTelefono).trim();
        hilo.execute(() -> {
            try {
                token = SesionActual.token(this, usuarioId);
                SupabaseAuth.enviarSolicitud(token, usuarioId, email, grado, apellidos, nombres,
                        cip, dni, telefono);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    startActivity(new Intent(this, PendienteActivity.class)
                            .putExtra(PendienteActivity.EXTRA_TOKEN, token));
                    finish();
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    cargando(false);
                    Toast.makeText(this, Errores.mensaje(e),
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private boolean validar() {
        primerError = null;
        boolean valido = true;
        if (texto(etGrado).trim().isEmpty()) {
            valido = error(layoutGrado, R.string.error_solicitud_grado);
        }
        if (!texto(etApellidos).trim().matches(PATRON_NOMBRE)) {
            valido = error(layoutApellidos, R.string.error_solicitud_apellidos);
        }
        if (!texto(etNombres).trim().matches(PATRON_NOMBRE)) {
            valido = error(layoutNombres, R.string.error_solicitud_nombres);
        }
        if (!texto(etCip).trim().matches(PATRON_CIP)) {
            valido = error(layoutCip, R.string.error_solicitud_cip);
        }
        if (!texto(etDni).trim().matches(PATRON_DNI)) {
            valido = error(layoutDni, R.string.error_solicitud_dni);
        }
        if (!texto(etTelefono).trim().matches(PATRON_TELEFONO)) {
            valido = error(layoutTelefono, R.string.error_solicitud_telefono);
        }
        return valido;
    }

    private boolean error(TextInputLayout layout, int mensaje) {
        layout.setError(getString(mensaje));
        if (primerError == null) {
            primerError = layout;
        }
        return false;
    }

    private void cargando(boolean activo) {
        ocupado = activo;
        progreso.setVisibility(activo ? View.VISIBLE : View.GONE);
        btnEnviar.setEnabled(!activo);
    }

    private static String texto(TextInputEditText campo) {
        Editable e = campo.getText();
        return e == null ? "" : e.toString();
    }

    private static void limpiar(TextInputEditText campo, TextInputLayout layout) {
        campo.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int i, int j, int k) {
            }

            @Override
            public void onTextChanged(CharSequence s, int i, int j, int k) {
                layout.setError(null);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });
    }
}
