package com.hidalgoferrai.myapplication;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Códigos de recuperación del Token Digital.
 *
 * Si se pierde el teléfono, el código del token se pierde con él: sin estos códigos la
 * persona queda fuera hasta que un administrador le borre el factor, y un administrador
 * que pierda el suyo no tendría salida dentro de la aplicación. Se muestran una sola vez,
 * al activar el token, y cada uno sirve para una sola recuperación.
 */
public class RecuperacionActivity extends AppCompatActivity {

    public static final String EXTRA_TOKEN = "token";

    private boolean errorGeneracion;
    private TextView tvCodigos;
    private LinearProgressIndicator progreso;
    private MaterialButton btnContinuar, btnCopiar;
    private MaterialCheckBox cbGuardados;
    private String[] codigos;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        // Los códigos valen tanto como el token: no deben salir en capturas.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_recuperacion);
        Diseno.bordes(this, false);

        tvCodigos = findViewById(R.id.tvCodigos);
        progreso = findViewById(R.id.progreso);
        btnContinuar = findViewById(R.id.btnContinuar);
        btnCopiar = findViewById(R.id.btnCopiar);
        cbGuardados = findViewById(R.id.cbGuardados);

        cbGuardados.setOnCheckedChangeListener((v, marcado) -> btnContinuar.setEnabled(marcado));
        btnContinuar.setOnClickListener(v -> finish());
        btnCopiar.setOnClickListener(v -> copiar());
        // Salir sin confirmar dejaría a la persona creyendo que los tiene guardados.
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (cbGuardados.isChecked() || errorGeneracion) {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                } else {
                    Toast.makeText(RecuperacionActivity.this,
                            R.string.recuperacion_confirme, Toast.LENGTH_LONG).show();
                }
            }
        });

        Estado estado = new androidx.lifecycle.ViewModelProvider(this).get(Estado.class);
        estado.resultado.observe(this, resultado -> {
            if (resultado.codigos != null) {
                codigos = resultado.codigos;
                mostrar();
            } else mostrarError(resultado.error);
        });
        estado.generar(getApplicationContext(), getIntent().getStringExtra(EXTRA_TOKEN),
                savedInstanceState != null);
    }

    private void mostrarError(Exception error) {
        errorGeneracion = true;
        progreso.setVisibility(View.GONE);
        tvCodigos.setVisibility(View.VISIBLE);
        tvCodigos.setText("proceso_restaurado".equals(error.getMessage())
                ? R.string.recuperacion_interrumpida : Errores.mensaje(error));
        cbGuardados.setVisibility(View.GONE);
        btnContinuar.setVisibility(View.VISIBLE);
        btnContinuar.setEnabled(true);
        btnContinuar.setText(R.string.recuperacion_continuar_sin);
    }

    /** Conserva la única respuesta en memoria durante la rotación, nunca en Bundle/disco. */
    public static final class Estado extends androidx.lifecycle.ViewModel {
        final androidx.lifecycle.MutableLiveData<Resultado> resultado = new androidx.lifecycle.MutableLiveData<>();
        private final ExecutorService hilo = Executors.newSingleThreadExecutor();
        private boolean iniciado;
        void generar(Context app, String tokenIntent, boolean restaurada) {
            if (iniciado) return;
            iniciado = true;
            if (restaurada) {
                // El proceso murió: no invalidar automáticamente los códigos ya entregados.
                resultado.setValue(new Resultado(null, new java.io.IOException("proceso_restaurado")));
                return;
            }
            String usuario = SesionActual.usuario();
            hilo.execute(() -> {
                try {
                    String token = usuario != null || AlmacenSeguro.refresco(app) != null
                            ? SesionActual.token(app, usuario) : tokenIntent;
                    if (token == null) throw new SupabaseAuth.ErrorApi(401, "{}");
                    resultado.postValue(new Resultado(SupabaseApi.generarCodigosRecuperacion(token), null));
                } catch (Exception e) { resultado.postValue(new Resultado(null, e)); }
            });
        }
        @Override protected void onCleared() { hilo.shutdown(); }
    }

    private static final class Resultado {
        final String[] codigos;
        final Exception error;
        Resultado(String[] codigos, Exception error) { this.codigos = codigos; this.error = error; }
    }

    private void mostrar() {
        progreso.setVisibility(View.GONE);
        tvCodigos.setText(TextUtils.join("\n", codigos));
        tvCodigos.setVisibility(View.VISIBLE);
        findViewById(R.id.tvAviso).setVisibility(View.VISIBLE);
        btnCopiar.setVisibility(View.VISIBLE);
        cbGuardados.setVisibility(View.VISIBLE);
        btnContinuar.setVisibility(View.VISIBLE);
    }

    private void copiar() {
        if (codigos == null) return;
        ClipboardManager portapapeles = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (portapapeles == null) return;
        ClipData datos = ClipData.newPlainText(
                getString(R.string.recuperacion_titulo), TextUtils.join("\n", codigos));
        // Marca el contenido como sensible para que el sistema no lo previsualice.
        android.os.PersistableBundle extras = new android.os.PersistableBundle();
        extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
        datos.getDescription().setExtras(extras);
        portapapeles.setPrimaryClip(datos);
        Toast.makeText(this, R.string.recuperacion_copiados, Toast.LENGTH_SHORT).show();
    }
}
