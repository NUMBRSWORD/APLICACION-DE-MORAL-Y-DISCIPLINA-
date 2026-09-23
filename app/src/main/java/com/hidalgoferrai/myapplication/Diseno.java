package com.hidalgoferrai.myapplication;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;

final class Diseno {
    private Diseno() { }

    /**
     * Encabezado verde a pantalla completa: se pinta también detrás de la barra de estado y
     * baja su contenido lo necesario para que nada quede tapado. Como el fondo de esa zona
     * siempre es verde oscuro, la hora y los iconos del sistema se fuerzan en claro.
     */
    static void bordes(Activity pantalla, boolean conTeclado) {
        View raiz = pantalla.findViewById(R.id.main);
        View encabezado = pantalla.findViewById(R.id.encabezado);
        WindowCompat.getInsetsController(pantalla.getWindow(), raiz)
                .setAppearanceLightStatusBars(false);
        int arribaDelEncabezado = encabezado == null ? 0 : encabezado.getPaddingTop();
        ViewCompat.setOnApplyWindowInsetsListener(raiz, (vista, ventana) -> {
            int tipos = WindowInsetsCompat.Type.systemBars();
            if (conTeclado) tipos |= WindowInsetsCompat.Type.ime();
            Insets barras = ventana.getInsets(tipos);
            if (encabezado == null) {
                vista.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            } else {
                vista.setPadding(barras.left, 0, barras.right, barras.bottom);
                encabezado.setPadding(encabezado.getPaddingLeft(), arribaDelEncabezado + barras.top,
                        encabezado.getPaddingRight(), encabezado.getPaddingBottom());
            }
            return ventana;
        });
    }

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
