package com.hidalgoferrai.myapplication;

import android.os.Bundle;
import android.widget.TextView;
import android.text.Html;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class TerminosActivity extends AppCompatActivity {

    public static final String EXTRA_TIPO = "tipo";
    public static final String TIPO_DATOS = "datos";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_terminos);
        Diseno.bordes(this, false);

        boolean datos = TIPO_DATOS.equals(getIntent().getStringExtra(EXTRA_TIPO));
        ((TextView) findViewById(R.id.tvTitulo)).setText(
                datos ? R.string.datos_titulo : R.string.terminos_titulo);
        ((TextView) findViewById(R.id.tvTexto)).setText(
                datos ? R.string.datos_texto : R.string.terminos_texto);
        if (datos) {
            try (InputStream in = getAssets().open("aviso_privacidad.html");
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096]; int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                ((TextView) findViewById(R.id.tvTexto)).setText(Html.fromHtml(
                        out.toString("UTF-8"), Html.FROM_HTML_MODE_LEGACY));
            } catch (java.io.IOException ignored) { /* Se conserva el aviso incluido en recursos. */ }
        }
        findViewById(R.id.btnCerrar).setOnClickListener(v -> finish());
    }
}
