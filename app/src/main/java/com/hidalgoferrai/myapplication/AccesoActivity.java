package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class AccesoActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_acceso);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        findViewById(R.id.btnUsuario).setOnClickListener(v -> entrar(Sesion.Rol.USUARIO));
        findViewById(R.id.btnAdministrador).setOnClickListener(v -> entrar(Sesion.Rol.ADMINISTRADOR));
    }

    private void entrar(Sesion.Rol rol) {
        Sesion.setRol(rol);
        startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
