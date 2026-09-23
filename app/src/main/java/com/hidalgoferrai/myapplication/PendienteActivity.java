package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;

public class PendienteActivity extends AppCompatActivity {

    public static final String EXTRA_RECHAZADA = "rechazada";
    public static final String EXTRA_TOKEN = "token";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_pendiente);
        Diseno.bordes(this, false);

        boolean rechazada = getIntent().getBooleanExtra(EXTRA_RECHAZADA, false);
        ((TextView) findViewById(R.id.tvEstado)).setText(
                rechazada ? R.string.rechazada_estado : R.string.pendiente_estado);
        ((TextView) findViewById(R.id.tvTitulo)).setText(
                rechazada ? R.string.rechazada_titulo : R.string.pendiente_titulo);
        ((TextView) findViewById(R.id.tvMensaje)).setText(
                rechazada ? R.string.rechazada_mensaje : R.string.pendiente_mensaje);
        // Una cuenta que espera aprobación —o que fue rechazada— también puede pedir su baja.
        findViewById(R.id.btnEliminarCuenta).setOnClickListener(v -> startActivity(
                new Intent(this, EliminarCuentaActivity.class)
                        .putExtra(EliminarCuentaActivity.EXTRA_TOKEN,
                                getIntent().getStringExtra(EXTRA_TOKEN))));
        findViewById(R.id.btnVolver).setOnClickListener(v -> {
            AlmacenSeguro.borrarRefresco(this);
            Perfil.borrar(this);
            startActivity(new Intent(this, LoginActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK));
            finish();
        });
    }
}
