package com.hidalgoferrai.myapplication;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.text.format.Formatter;
import android.view.View;
import android.widget.TextView;
import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning;
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult;
import java.io.InputStream;
import java.io.DataInputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExpedienteActivity extends AppCompatActivity {
    private final ExecutorService hilo = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Uri documento;
    private String nombre;
    private int operacion;
    private final ActivityResultLauncher<String[]> elegir = registerForActivityResult(
            new ActivityResultContracts.OpenDocument(), uri -> {
                if (uri != null) {
                    try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION); }
                    catch (SecurityException ignored) { }
                    prepararPdf(uri);
                }
            });
    private final ActivityResultLauncher<IntentSenderRequest> escaner = registerForActivityResult(
            new ActivityResultContracts.StartIntentSenderForResult(), resultado -> {
                if (resultado.getResultCode() != RESULT_OK) return;
                GmsDocumentScanningResult datos = GmsDocumentScanningResult.fromActivityResultIntent(resultado.getData());
                if (datos != null && datos.getPdf() != null) prepararPdf(datos.getPdf().getUri());
                else error(R.string.scanner_sin_resultado);
            });

    @Override protected void onCreate(Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_expediente);
        ((TextView)findViewById(R.id.tvModuloTitulo)).setText(R.string.subir_expediente);
        findViewById(R.id.btnAtras).setOnClickListener(v -> finish());
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(b.left,b.top,b.right,b.bottom); return insets;
        });
        findViewById(R.id.btnEscanear).setOnClickListener(v -> escanear());
        findViewById(R.id.btnSeleccionarPdf).setOnClickListener(v -> {
            operacion++; finEspera(); elegir.launch(new String[]{"application/pdf"});
        });
        findViewById(R.id.btnVerPdf).setOnClickListener(v -> {
            if (documento == null) return;
            try { startActivity(new Intent(Intent.ACTION_VIEW).setDataAndType(documento,"application/pdf")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)); }
            catch (Exception e) { error(R.string.web_sin_aplicacion); }
        });
        findViewById(R.id.btnContinuarPdf).setOnClickListener(v -> {
            if (documento == null) return;
            startActivity(new Intent(this, WebActivity.class).putExtra(WebActivity.EXTRA_VISTA,"registro")
                    .putExtra(WebActivity.EXTRA_DOCUMENTO,documento.toString())
                    .putExtra(WebActivity.EXTRA_NOMBRE_DOCUMENTO,nombre));
        });
        if (!Perfil.esAdministrador(this)) {
            findViewById(R.id.btnEscanear).setEnabled(false);
            findViewById(R.id.btnSeleccionarPdf).setEnabled(false);
            error(R.string.pdf_sin_permiso);
        } else if (saved != null && saved.getString("documento") != null) {
            prepararPdf(Uri.parse(saved.getString("documento")));
        }
    }

    private void escanear() {
        final int intento = ++operacion;
        findViewById(R.id.estadoPanel).setVisibility(View.GONE);
        findViewById(R.id.tvScannerEstado).setVisibility(View.VISIBLE);
        findViewById(R.id.btnEscanear).setEnabled(false);
        GmsDocumentScannerOptions opciones = new GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true).setPageLimit(20)
                .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build();
        handler.postDelayed(() -> {
            if (intento == operacion) { operacion++; finEspera(); error(R.string.scanner_error); }
        }, 45000);
        GmsDocumentScanning.getClient(opciones).getStartScanIntent(this)
                .addOnSuccessListener(sender -> {
                    if (isDestroyed() || intento != operacion) return;
                    operacion++; finEspera();
                    escaner.launch(new IntentSenderRequest.Builder(sender).build());
                }).addOnFailureListener(e -> {
                    if (isDestroyed() || intento != operacion) return;
                    operacion++; finEspera(); error(R.string.scanner_error);
                });
    }

    void prepararPdf(Uri uri) {
        final int intento = ++operacion;
        if (!Perfil.esAdministrador(this)) { error(R.string.pdf_sin_permiso); return; }
        finEspera();
        documento = null;
        findViewById(R.id.tarjetaPdf).setVisibility(View.GONE);
        hilo.execute(() -> {
            try {
                long tam = 0;
                String etiqueta = "documento.pdf";
                try (Cursor c = getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)) {
                    if(c!=null && c.moveToFirst()) etiqueta=c.getString(0);
                }
                try (InputStream in = getContentResolver().openInputStream(uri)) {
                    if(in==null) throw new IllegalArgumentException();
                    byte[] cabecera = new byte[5];
                    new DataInputStream(in).readFully(cabecera);
                    if(!new String(cabecera,StandardCharsets.US_ASCII).equals("%PDF-")) throw new IllegalArgumentException();
                    tam=5;
                    byte[] bloque = new byte[8192];
                    int n;
                    while((n=in.read(bloque))!=-1) {
                        tam+=n; if(tam>20*1024*1024) throw new IllegalArgumentException();
                    }
                }
                final String archivo = etiqueta == null ? "documento.pdf" : etiqueta;
                final long bytes = tam;
                runOnUiThread(() -> {
                    if(isDestroyed() || intento!=operacion) return;
                    documento=uri; nombre=archivo;
                    findViewById(R.id.estadoPanel).setVisibility(View.GONE);
                    ((TextView)findViewById(R.id.tvPdfNombre)).setText(getString(R.string.pdf_nombre,archivo,Formatter.formatFileSize(this,bytes)));
                    findViewById(R.id.tarjetaPdf).setVisibility(View.VISIBLE);
                });
            } catch(Exception e) { runOnUiThread(() -> { if(!isDestroyed() && intento==operacion) error(R.string.pdf_invalido); }); }
        });
    }
    private void finEspera() {
        findViewById(R.id.tvScannerEstado).setVisibility(View.GONE);
        findViewById(R.id.btnEscanear).setEnabled(Perfil.esAdministrador(this));
    }
    private void error(int texto) { Diseno.error(this,R.string.pdf_error,texto,false); }
    @Override protected void onSaveInstanceState(Bundle out) {
        if(documento!=null) out.putString("documento",documento.toString()); super.onSaveInstanceState(out);
    }
    @Override protected void onDestroy() { operacion++; handler.removeCallbacksAndMessages(null); hilo.shutdown(); super.onDestroy(); }
}
