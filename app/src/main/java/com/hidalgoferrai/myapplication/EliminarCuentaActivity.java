package com.hidalgoferrai.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Pedido de eliminación de la cuenta desde la propia aplicación.
 *
 * Google Play exige esta vía —y una equivalente en la web— para cualquier aplicación que
 * permita crear una cuenta. El pedido queda registrado en el servidor con la fecha; el
 * administrador es quien ejecuta el borrado y responde al correo de la persona.
 */
public class EliminarCuentaActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "token";

    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private MaterialButton btnSolicitar;
    private LinearProgressIndicator progreso;
    private TextView tvEstado;
    private TextInputEditText motivo;
    private String token;
    private String usuario;
    private boolean ocupado;
    /** Falso cuando ya hay un pedido en curso o no hay sesión: el botón no vuelve a activarse. */
    private boolean puedePedir = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_eliminar_cuenta);
        Diseno.bordes(this, true);

        token = getIntent().getStringExtra(EXTRA_TOKEN);
        usuario = SesionActual.usuario();
        btnSolicitar = findViewById(R.id.btnSolicitar);
        progreso = findViewById(R.id.progreso);
        tvEstado = findViewById(R.id.tvEstado);
        motivo = findViewById(R.id.motivo);

        findViewById(R.id.btnVolver).setOnClickListener(v -> finish());
        btnSolicitar.setOnClickListener(v -> confirmar());

        if (token == null && usuario == null && AlmacenSeguro.refresco(this) == null) {
            // Sin sesión no se puede identificar la cuenta: solo queda la vía pública.
            puedePedir = false;
            btnSolicitar.setEnabled(false);
            mostrarEstado(R.string.eliminar_sin_sesion);
            return;
        }
        consultarEstado();
    }

    @Override
    protected void onDestroy() {
        hilo.shutdown();
        super.onDestroy();
    }

    private void confirmar() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.eliminar_confirmar_titulo)
                .setMessage(R.string.eliminar_confirmar_mensaje)
                .setNegativeButton(R.string.eliminar_cancelar, null)
                .setPositiveButton(R.string.eliminar_confirmar_si, (dialogo, boton) -> enviar())
                .show();
    }

    private void consultarEstado() {
        cargando(true);
        hilo.execute(() -> {
            try {
                renovarToken();
                boolean pendiente = SupabaseApi.tieneEliminacionPendiente(token);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    cargando(false);
                    if (pendiente) yaPedida();
                });
            } catch (Exception e) {
                // No poder consultarlo no debe impedir pedirlo: el servidor evita duplicados.
                runOnUiThread(() -> { if (!isFinishing() && !isDestroyed()) cargando(false); });
            }
        });
    }

    private void enviar() {
        if (ocupado || !puedePedir) return;
        cargando(true);
        String texto = motivo.getText() == null ? null : motivo.getText().toString();
        hilo.execute(() -> {
            try {
                renovarToken();
                String estado = SupabaseApi.solicitarEliminacion(token, texto);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    cargando(false);
                    if ("ok".equals(estado) || "ya_pendiente".equals(estado)) {
                        yaPedida();
                        Toast.makeText(this, R.string.eliminar_registrada, Toast.LENGTH_LONG).show();
                    } else {
                        mostrarEstado(R.string.eliminar_error);
                    }
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    cargando(false);
                    mostrarEstado(Errores.mensaje(e));
                });
            }
        });
    }

    private void yaPedida() {
        puedePedir = false;
        btnSolicitar.setEnabled(false);
        motivo.setEnabled(false);
        mostrarEstado(R.string.eliminar_ya_pendiente);
    }

    private void mostrarEstado(int mensaje) {
        tvEstado.setText(mensaje);
        tvEstado.setVisibility(View.VISIBLE);
    }

    private void cargando(boolean activo) {
        ocupado = activo;
        progreso.setVisibility(activo ? View.VISIBLE : View.GONE);
        btnSolicitar.setEnabled(!activo && puedePedir);
    }

    private void renovarToken() throws Exception {
        if (usuario != null || AlmacenSeguro.refresco(this) != null)
            token = SesionActual.token(this, usuario);
        if (token == null) throw new SupabaseAuth.ErrorApi(401, "{}");
    }
}
