package com.hidalgoferrai.myapplication;

import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class TerminosActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";
    public static final String TIPO_DATOS = "datos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terminos);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });

        boolean datos = TIPO_DATOS.equals(getIntent().getStringExtra(EXTRA_TIPO));
        ((TextView) findViewById(R.id.tvTitulo)).setText(
                datos ? R.string.datos_titulo : R.string.terminos_titulo);
        ((TextView) findViewById(R.id.tvTexto)).setText(
                datos ? R.string.datos_texto : R.string.terminos_texto);
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
    }
}
