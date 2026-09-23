package com.hidalgoferrai.myapplication;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.progressindicator.LinearProgressIndicator;

/** Consulta local del token: no abandona Inicio, no activa ni firma nada. */
public class TokenInferior extends BottomSheetDialogFragment {
    private final Handler reloj = new Handler(Looper.getMainLooper());
    private String secreto;
    private View contenido;
    private final Runnable tic = new Runnable() {
        @Override public void run() {
            if (contenido == null || secreto == null) return;
            try {
                String codigo = Totp.codigo(secreto);
                ((TextView) contenido.findViewById(R.id.tvCodigoInferior)).setText(
                        getString(R.string.token_codigo_formato, codigo.substring(0,3), codigo.substring(3)));
                int segundos = Totp.segundosRestantes();
                ((TextView) contenido.findViewById(R.id.tvTiempoInferior)).setText(
                        getResources().getQuantityString(R.plurals.token_expira_plural, segundos, segundos));
                ((LinearProgressIndicator) contenido.findViewById(R.id.progresoTokenInferior)).setProgress(segundos);
                reloj.postDelayed(this, 1000);
            } catch (RuntimeException e) { sinToken(); }
        }
    };
    @NonNull @Override public Dialog onCreateDialog(Bundle estado) {
        Dialog dialogo = super.onCreateDialog(estado);
        if (dialogo.getWindow() != null)
            dialogo.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        return dialogo;
    }
    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle estado) {
        contenido = inflater.inflate(R.layout.sheet_token, parent, false);
        String uid = Perfil.usuarioId(requireContext());
        if (uid != null && AlmacenSeguro.tokenVerificado(requireContext(), uid))
            secreto = AlmacenSeguro.secreto(requireContext(), uid);
        contenido.findViewById(R.id.btnCerrarToken).setOnClickListener(v -> dismiss());
        contenido.findViewById(R.id.btnVerificarInferior).setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), TokenActivity.class)
                    .putExtra(TokenActivity.EXTRA_USUARIO_ID, uid));
            dismiss();
        });
        // Quien activó su token antes de que existieran los códigos también puede obtenerlos.
        contenido.findViewById(R.id.btnCodigosRespaldo).setOnClickListener(v -> confirmarCodigos());
        if (secreto == null) sinToken();
        return contenido;
    }

    private void confirmarCodigos() {
        String acceso = tokenDeSesion();
        if (acceso == null) {
            android.widget.Toast.makeText(requireContext(), R.string.error_sesion,
                    android.widget.Toast.LENGTH_LONG).show();
            return;
        }
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.recuperacion_nuevos)
                .setMessage(R.string.recuperacion_reemplaza)
                .setNegativeButton(R.string.eliminar_cancelar, null)
                .setPositiveButton(R.string.recuperacion_generar, (d, b) -> {
                    startActivity(new Intent(requireContext(), RecuperacionActivity.class)
                            .putExtra(RecuperacionActivity.EXTRA_TOKEN, acceso));
                    dismiss();
                })
                .show();
    }

    private String tokenDeSesion() {
        try {
            String sesion = SesionActual.obtener(requireContext());
            if (sesion == null) return null;
            String acceso = new org.json.JSONObject(sesion).optString("access_token", null);
            return acceso == null || acceso.isEmpty() ? null : acceso;
        } catch (Exception e) {
            return null;
        }
    }
    private void sinToken() {
        secreto = null;
        ((TextView) contenido.findViewById(R.id.tvCodigoInferior)).setText("— — —");
        ((TextView) contenido.findViewById(R.id.tvTiempoInferior)).setText(R.string.token_sin_local);
        contenido.findViewById(R.id.progresoTokenInferior).setVisibility(View.GONE);
        contenido.findViewById(R.id.btnVerificarInferior).setVisibility(View.VISIBLE);
    }
    @Override public void onStart() { super.onStart(); reloj.post(tic); }
    @Override public void onStop() {
        reloj.removeCallbacks(tic);
        if (contenido != null) ((TextView) contenido.findViewById(R.id.tvCodigoInferior)).setText("");
        super.onStop();
    }
    @Override public void onDestroyView() { reloj.removeCallbacks(tic); contenido = null; secreto = null; super.onDestroyView(); }
}
