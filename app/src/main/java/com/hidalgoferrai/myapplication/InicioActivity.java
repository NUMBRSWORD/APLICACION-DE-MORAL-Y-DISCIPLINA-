package com.hidalgoferrai.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.webkit.CookieManager;
import android.webkit.WebStorage;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.Calendar;

import org.json.JSONObject;

/** Inicio breve por perfil. La gestión extensa continúa en la web. */
public class InicioActivity extends AppCompatActivity {

    public static final String EXTRA_SESION = "sesion";

    private String sesion;
    private String tokenActual;
    private final ActivityResultLauncher<String> permisoAvisos = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(), permitido -> {
                if (permitido) registrarAvisos();
                else ((TextView) findViewById(R.id.tvAvisosEstado))
                        .setText(R.string.avisos_permiso);
            });

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
        Tema.prepararBoton(this);
        Diseno.bordes(this, false);

        ((TextView) findViewById(R.id.tvSaludo)).setText(saludo());
        String grado = Perfil.grado(this);
        String nombre = Perfil.nombre(this);
        String nombreVisible = getString(R.string.inicio_nombre_formato, grado, nombre).trim();
        ((TextView) findViewById(R.id.tvNombre)).setText(nombreVisible);
        ((TextView) findViewById(R.id.tvRol)).setText(Perfil.esAdministrador(this)
                ? R.string.rol_administrador : R.string.rol_usuario);

        LinearLayout grid = findViewById(R.id.grid);
        boolean administrador = Perfil.esAdministrador(this);
        ((TextView) findViewById(R.id.tvPanelTitulo)).setText(administrador
                ? R.string.inicio_admin_titulo : R.string.inicio_usuario_titulo);
        ((TextView) findViewById(R.id.tvPanelTexto)).setText(administrador
                ? R.string.inicio_admin_texto : R.string.inicio_usuario_texto);
        findViewById(R.id.btnMiToken).setOnClickListener(v -> {
            if (getSupportFragmentManager().findFragmentByTag("token-inferior") == null)
                new TokenInferior().show(getSupportFragmentManager(), "token-inferior");
        });
        if (administrador) {
            agregar(grid, R.string.inicio_admin_panel, R.string.inicio_admin_panel_detalle,
                    R.drawable.ic_calendario, "panel");
            agregar(grid, R.string.inicio_subir_completo, R.string.inicio_subir_completo_detalle,
                    R.drawable.ic_expediente, "consulta");
            agregar(grid, R.string.inicio_recepcionar, R.string.inicio_recepcionar_detalle,
                    R.drawable.ic_recepcion, "recepcion-fisica");
        } else {
            agregar(grid, R.string.inicio_usuario_pendientes, R.string.inicio_usuario_pendientes_detalle,
                    R.drawable.ic_seguimiento, "pendientes");
            agregar(grid, R.string.inicio_subir_completo, R.string.inicio_usuario_subir_detalle,
                    R.drawable.ic_expediente, "consulta");
        }

        findViewById(R.id.btnSalir).setOnClickListener(v -> cerrarSesion());
        prepararAvisos();
        comprobarActualizacion();
        // Vía de baja exigida por Google Play; la equivalente pública está en la web.
        findViewById(R.id.btnEliminarCuenta).setOnClickListener(v -> startActivity(
                new Intent(this, EliminarCuentaActivity.class)
                        .putExtra(EliminarCuentaActivity.EXTRA_TOKEN, tokenActual)));
    }

    private void prepararAvisos() {
        if (!AvisosAndroid.configurado(this)) return;
        findViewById(R.id.tarjetaAvisos).setVisibility(View.VISIBLE);
        MaterialButton boton = findViewById(R.id.btnAvisos);
        boolean habilitado = AvisosAndroid.habilitado(this);
        boolean permiso = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this,
                Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        boton.setText(habilitado && permiso ? R.string.avisos_actualizar : R.string.avisos_activar);
        if (habilitado && permiso) {
            ((TextView) findViewById(R.id.tvAvisosEstado)).setText(R.string.avisos_activados);
            registrarAvisos();
        } else if (habilitado) {
            ((TextView) findViewById(R.id.tvAvisosEstado)).setText(R.string.avisos_permiso);
        }
        boton.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permisoAvisos.launch(Manifest.permission.POST_NOTIFICATIONS);
            } else registrarAvisos();
        });
    }

    private void registrarAvisos() {
        MaterialButton boton = findViewById(R.id.btnAvisos);
        boton.setEnabled(false);
        ((TextView) findViewById(R.id.tvAvisosEstado)).setText(R.string.avisos_conectando);
        AvisosAndroid.registrar(this, () -> {
            if (isFinishing() || isDestroyed()) return;
            boton.setEnabled(true);
            boton.setText(R.string.avisos_actualizar);
            ((TextView) findViewById(R.id.tvAvisosEstado)).setText(R.string.avisos_activados);
        }, () -> {
            if (isFinishing() || isDestroyed()) return;
            boton.setEnabled(true);
            ((TextView) findViewById(R.id.tvAvisosEstado)).setText(R.string.avisos_error);
        });
    }

    /**
     * La aplicación se reparte por archivo APK, así que nadie se entera de una versión
     * nueva si no se le avisa aquí. Solo muestra el aviso: descargar e instalar lo decide
     * la persona. Si no hay conexión, la pantalla queda igual.
     */
    private void comprobarActualizacion() {
        new Thread(() -> {
            Actualizacion.Nueva nueva = Actualizacion.comprobar(getApplicationContext());
            if (nueva == null) return;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                ((TextView) findViewById(R.id.tvAvisoActualizacion))
                        .setText(getString(R.string.actualizacion_aviso, nueva.version));
                findViewById(R.id.avisoActualizacion).setVisibility(View.VISIBLE);
                findViewById(R.id.btnActualizar).setOnClickListener(v -> {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(nueva.url)));
                    } catch (android.content.ActivityNotFoundException e) {
                        android.widget.Toast.makeText(this, R.string.web_sin_aplicacion,
                                android.widget.Toast.LENGTH_SHORT).show();
                    }
                });
            });
        }, "aviso-actualizacion").start();
    }

    private String saludo() {
        int hora = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        if (hora < 12) {
            return getString(R.string.inicio_saludo_manana);
        }
        return hora < 19 ? getString(R.string.inicio_saludo_tarde)
                : getString(R.string.inicio_saludo_noche);
    }

    /** Los destinos visibles son concretos; nunca abren el inicio genérico de la web. */
    private void agregar(LinearLayout grid, int etiqueta, int detalle, int icono, String vista) {
        View tarjeta = LayoutInflater.from(this).inflate(R.layout.item_acceso, grid, false);
        ((ImageView) tarjeta.findViewById(R.id.icono)).setImageResource(icono);
        ((TextView) tarjeta.findViewById(R.id.etiqueta)).setText(etiqueta);
        ((TextView) tarjeta.findViewById(R.id.detalle)).setText(detalle);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(dp(6), dp(6), dp(6), dp(6));
        tarjeta.setLayoutParams(lp);

        tarjeta.setOnClickListener(v -> {
            if ("pendientes".equals(vista)) {
                startActivity(new Intent(this, SeguimientoActivity.class));
            } else {
                Intent i = new Intent(this, WebActivity.class).putExtra(WebActivity.EXTRA_VISTA, vista);
                startActivity(i);
            }
        });
        grid.addView(tarjeta);
    }

    private void cerrarSesion() {
        AvisosAndroid.cerrarSesion(this, tokenActual);
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
