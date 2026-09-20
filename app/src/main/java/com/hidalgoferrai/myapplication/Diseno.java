package com.hidalgoferrai.myapplication;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

final class Diseno {
    private Diseno() { }

    static void pasos(Activity pantalla, int activo) {
        int[] ids = {R.id.pasoAcceso, R.id.pasoPoliticas, R.id.pasoToken};
        for (int i = 0; i < ids.length; i++) {
            TextView paso = pantalla.findViewById(ids[i]);
            if (paso == null) continue;
            paso.setBackgroundResource(i + 1 == activo ? R.drawable.bg_step_active : R.drawable.bg_step);
            paso.setTextColor(pantalla.getColor(i + 1 == activo
                    ? R.color.oro_sobre_verde : R.color.texto_sobre_verde));
            paso.setAlpha(i + 1 > activo ? 0.65f : 1f);
        }
    }

    static void error(Activity pantalla, int titulo, int mensaje, boolean reintentar) {
        pantalla.findViewById(R.id.estadoPanel).setVisibility(View.VISIBLE);
        ((TextView) pantalla.findViewById(R.id.estadoTitulo)).setText(titulo);
        ((TextView) pantalla.findViewById(R.id.estadoMensaje)).setText(mensaje);
        pantalla.findViewById(R.id.btnReintentar).setVisibility(reintentar ? View.VISIBLE : View.GONE);
    }
}
