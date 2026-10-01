package com.hidalgoferrai.myapplication;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.annotation.NonNull;
import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

/**
 * «Mi Token Digital» del inicio: dice en qué estado está y deja generar códigos de
 * recuperación nuevos.
 *
 * Ya no muestra ningún código: desde esta versión el token lo guarda la app de
 * códigos del teléfono, no esta aplicación. Enseñar aquí un código sería enseñar
 * uno que el servidor ya no espera.
 */
public class TokenInferior extends BottomSheetDialogFragment {
    private View contenido;
    private String usuarioId;
    /** Token de versiones anteriores, todavía dentro de la aplicación. */
    private boolean enLaApp;

    @NonNull @Override public Dialog onCreateDialog(Bundle estado) {
        Dialog dialogo = super.onCreateDialog(estado);
        if (dialogo.getWindow() != null)
            dialogo.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        return dialogo;
    }

    @Override public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle estado) {
        contenido = inflater.inflate(R.layout.sheet_token, parent, false);
        usuarioId = Perfil.usuarioId(requireContext());
        boolean verificado = usuarioId != null
                && AlmacenSeguro.tokenVerificado(requireContext(), usuarioId);
        enLaApp = verificado && AlmacenSeguro.secreto(requireContext(), usuarioId) != null;

        // El hueco del código y su reloj dejan de tener sentido.
        contenido.findViewById(R.id.progresoTokenInferior).setVisibility(View.GONE);
        ((TextView) contenido.findViewById(R.id.tvCodigoInferior)).setVisibility(View.GONE);
        ((TextView) contenido.findViewById(R.id.tvTiempoInferior)).setText(
                !verificado ? R.string.token_inferior_sin
                        : enLaApp ? R.string.token_inferior_en_app
                        : R.string.token_inferior_activo);

        com.google.android.material.button.MaterialButton accion =
                contenido.findViewById(R.id.btnVerificarInferior);
        accion.setVisibility(View.VISIBLE);
        accion.setText(!verificado ? R.string.token_inferior_activar
                : enLaApp ? R.string.token_inferior_pasar
                : R.string.token_titulo);
        accion.setOnClickListener(v -> {
            startActivity(new Intent(requireContext(), TokenActivity.class)
                    .putExtra(TokenActivity.EXTRA_USUARIO_ID, usuarioId));
            dismiss();
        });

        contenido.findViewById(R.id.btnCerrarToken).setOnClickListener(v -> dismiss());
        // Quien activó su token antes de que existieran los códigos también puede obtenerlos.
        contenido.findViewById(R.id.btnCodigosRespaldo).setOnClickListener(v -> confirmarCodigos());
        contenido.findViewById(R.id.btnCodigosRespaldo)
                .setVisibility(verificado ? View.VISIBLE : View.GONE);
        return contenido;
    }

    private void confirmarCodigos() {
        new androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.recuperacion_nuevos)
                .setMessage(R.string.recuperacion_reemplaza)
                .setNegativeButton(R.string.eliminar_cancelar, null)
                .setPositiveButton(R.string.recuperacion_generar, (d, b) -> {
                    startActivity(new Intent(requireContext(), RecuperacionActivity.class));
                    dismiss();
                })
                .show();
    }

    @Override public void onDestroyView() { contenido = null; super.onDestroyView(); }
}
