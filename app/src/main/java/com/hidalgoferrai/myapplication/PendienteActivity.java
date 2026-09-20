package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class PendienteActivity extends AppCompatActivity {

    public static final String EXTRA_RECHAZADA = "rechazada";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pendiente);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        boolean rechazada = getIntent().getBooleanExtra(EXTRA_RECHAZADA, false);
        ((TextView) findViewById(R.id.tvEstado)).setText(
                rechazada ? R.string.rechazada_estado : R.string.pendiente_estado);
        ((TextView) findViewById(R.id.tvTitulo)).setText(
                rechazada ? R.string.rechazada_titulo : R.string.pendiente_titulo);
        ((TextView) findViewById(R.id.tvMensaje)).setText(
                rechazada ? R.string.rechazada_mensaje : R.string.pendiente_mensaje);
        findViewById(R.id.btnVolver).setOnClickListener(v -> {
            AlmacenSeguro.borrarRefresco(this);
            Perfil.borrar(this);
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
    }
}
