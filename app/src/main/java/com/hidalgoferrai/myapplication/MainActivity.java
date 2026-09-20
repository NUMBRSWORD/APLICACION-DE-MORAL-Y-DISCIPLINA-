package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Sesion.Rol rol = Sesion.getRol();
        if (rol == null) {
            irAAcceso();
            return;
        }
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        TextView tvRol = findViewById(R.id.tvRol);
        TextView tvDescripcionRol = findViewById(R.id.tvDescripcionRol);
        LinearLayout menu = findViewById(R.id.menu);

        if (rol == Sesion.Rol.ADMINISTRADOR) {
            tvRol.setText(getString(R.string.inicio_rol, getString(R.string.rol_administrador)));
            tvDescripcionRol.setText(R.string.rol_descripcion_administrador);
            agregarOpcion(menu, R.string.menu_registrar_falta, RegistroActivity.class);
            agregarOpcion(menu, R.string.menu_expedientes, null);
            agregarOpcion(menu, R.string.menu_efectivos, null);
            agregarOpcion(menu, R.string.menu_recepcion, null);
        } else {
            tvRol.setText(getString(R.string.inicio_rol, getString(R.string.rol_usuario)));
            tvDescripcionRol.setText(R.string.rol_descripcion_usuario);
            agregarOpcion(menu, R.string.menu_expedientes, null);
            agregarOpcion(menu, R.string.menu_seguimiento, null);
            agregarOpcion(menu, R.string.menu_cumplimiento, null);
        }

        findViewById(R.id.btnCambiarRol).setOnClickListener(v -> {
            Sesion.cerrar();
            irAAcceso();
        });
    }

    private void agregarOpcion(LinearLayout menu, int titulo, Class<?> destino) {
        MaterialButton boton = new MaterialButton(this);
        boton.setText(titulo);
        LinearLayout.LayoutParams parametros = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(56));
        parametros.topMargin = dp(12);
        boton.setLayoutParams(parametros);
        boton.setOnClickListener(v -> {
            if (destino != null) {
                startActivity(new Intent(this, destino));
            } else {
                Toast.makeText(this, R.string.menu_proximamente, Toast.LENGTH_SHORT).show();
            }
        });
        menu.addView(boton);
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private void irAAcceso() {
        startActivity(new Intent(this, AccesoActivity.class));
        finish();
    }
}
