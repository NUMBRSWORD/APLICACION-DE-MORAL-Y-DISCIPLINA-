package com.hidalgoferrai.myapplication;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;

import androidx.appcompat.app.AppCompatDelegate;

import com.google.android.material.button.MaterialButton;

/** Tema compartido por pantallas nativas y módulos web. */
public final class Tema {
    private static final String PREFERENCIAS = "apariencia";
    private static final String CLAVE_MODO = "modo_noche";

    private Tema() { }

    public static void aplicar(Context contexto) {
        int modo = contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
                .getInt(CLAVE_MODO, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        AppCompatDelegate.setDefaultNightMode(modo);
    }

    public static boolean esOscuro(Context contexto) {
        return (contexto.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    public static void alternar(Context contexto) {
        int modo = esOscuro(contexto) ? AppCompatDelegate.MODE_NIGHT_NO
                : AppCompatDelegate.MODE_NIGHT_YES;
        contexto.getSharedPreferences(PREFERENCIAS, Context.MODE_PRIVATE)
                .edit().putInt(CLAVE_MODO, modo).apply();
        AppCompatDelegate.setDefaultNightMode(modo);
    }

    public static void prepararBoton(Activity actividad) {
        MaterialButton boton = actividad.findViewById(R.id.btnTema);
        if (boton == null) return;
        mostrarEstado(boton, esOscuro(actividad));
        boton.setOnClickListener(v -> {
            boolean activarOscuro = !esOscuro(actividad);
            mostrarEstado(boton, activarOscuro);
            alternar(actividad);
        });
    }

    private static void mostrarEstado(MaterialButton boton, boolean oscuro) {
        boton.setIconResource(oscuro ? R.drawable.ic_sun : R.drawable.ic_moon);
        boton.setContentDescription(boton.getContext().getString(oscuro
                ? R.string.tema_activar_claro : R.string.tema_activar_oscuro));
    }
}
