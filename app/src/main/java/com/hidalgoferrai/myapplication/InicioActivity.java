package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Calendar;

import org.json.JSONObject;

/** Pantalla de inicio: saludo y cuadrícula de accesos, incluido el Token Digital. */
public class InicioActivity extends AppCompatActivity {

    public static final String EXTRA_SESION = "sesion";

    private String sesion;
    private String tokenActual;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sesion = getIntent().getStringExtra(EXTRA_SESION);
        if (sesion != null) {
            SesionActual.recibir(this, sesion);
            try {
                tokenActual = new JSONObject(sesion).optString("access_token", null);
            } catch (Exception ignored) {
                tokenActual = null;
            }
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_inicio);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        ((TextView) findViewById(R.id.tvSaludo)).setText(saludo());
        String grado = Perfil.grado(this);
        String nombre = Perfil.nombre(this);
        String nombreVisible = getString(R.string.inicio_nombre_formato, grado, nombre).trim();
        ((TextView) findViewById(R.id.tvNombre)).setText(nombreVisible);
        ((TextView) findViewById(R.id.tvRol)).setText(Perfil.esAdministrador(this)
                ? R.string.rol_administrador : R.string.rol_usuario);

        GridLayout grid = findViewById(R.id.grid);
        findViewById(R.id.btnMiToken).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("token-inferior") == null)
                new TokenInferior().show(getSupportFragmentManager(), "token-inferior");
        });
        agregar(grid, R.string.subir_expediente, R.drawable.ic_expediente, "subir");
        agregar(grid, R.string.acceso_seguimiento, R.drawable.ic_seguimiento, "pendientes");
        agregar(grid, R.string.acceso_cumplimiento, R.drawable.ic_cumplimiento, "cumplimiento");
        if (Perfil.esAdministrador(this)) {
            agregar(grid, R.string.acceso_personal, R.drawable.ic_personal, "efectivos");
            agregar(grid, R.string.recepcion_fisica, R.drawable.ic_recepcion, "recepcion-fisica");
        }
        agregar(grid, R.string.consulta_expediente, R.drawable.ic_mas, "consulta");

        findViewById(R.id.btnSalir).setOnClickListener(v -> cerrarSesion());
    }

    private String saludo() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora < 12) {
            return getString(R.string.inicio_saludo_manana);
        }
        return hora < 19 ? getString(R.string.inicio_saludo_tarde)
                : getString(R.string.inicio_saludo_noche);
    }

    /** Cada tarjeta tiene un destino independiente; ninguna vuelve al inicio web. */
    private void agregar(GridLayout grid, int etiqueta, int icono, String vista) {
        View tarjeta = LayoutInflater.from(this).inflate(R.layout.item_acceso, grid, false);
        ((ImageView) tarjeta.findViewById(R.id.icono)).setImageResource(icono);
        ((TextView) tarjeta.findViewById(R.id.etiqueta)).setText(etiqueta);
        int detalle = R.string.acceso_mas_detalle;
        if (etiqueta == R.string.acceso_token) detalle = R.string.acceso_token_detalle;
        else if (etiqueta == R.string.subir_expediente) detalle = R.string.subir_tarjeta_detalle;
        else if (etiqueta == R.string.acceso_seguimiento) detalle = R.string.acceso_seguimiento_detalle;
        else if (etiqueta == R.string.acceso_cumplimiento) detalle = R.string.acceso_cumplimiento_detalle;
        else if (etiqueta == R.string.acceso_personal) detalle = R.string.acceso_personal_detalle;
        else if (etiqueta == R.string.acceso_recepcion) detalle = R.string.acceso_recepcion_detalle;
        else if (etiqueta == R.string.modulo_archivo) detalle = R.string.modulo_archivo_detalle;
        else if (etiqueta == R.string.consulta_expediente) detalle = R.string.consulta_expediente_detalle;
        else if (etiqueta == R.string.recepcion_fisica) detalle = R.string.recepcion_fisica_detalle;
        ((TextView) tarjeta.findViewById(R.id.detalle)).setText(detalle);

        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1, 1f);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        tarjeta.setLayoutParams(lp);

        tarjeta.setOnClickListener(v -> {
            if ("subir".equals(vista)) {
                startActivity(new Intent(this, ExpedienteActivity.class));
            } else if ("pendientes".equals(vista)) {
                startActivity(new Intent(this, SeguimientoActivity.class));
            } else {
                Intent i = new Intent(this, WebActivity.class).putExtra(WebActivity.EXTRA_VISTA, vista);
                startActivity(i);
            }
        });
        grid.addView(tarjeta);
    }

    private void cerrarSesion() {
        SesionActual.borrar();
        Perfil.borrar(this);
        AlmacenSeguro.borrarRefresco(this);
        WebStorage.getInstance().deleteAllData();
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        if (tokenActual != null) {
            String tokenParaCerrar = tokenActual;
            new Thread(() -> {
                try {
                    SupabaseAuth.cerrarSesion(tokenParaCerrar);
                } catch (Exception ignored) {
                    // La sesión local ya quedó cerrada aunque el servidor no responda.
                }
            }, "cerrar-sesion").start();
        }
        startActivity(new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
        finish();
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }
}
