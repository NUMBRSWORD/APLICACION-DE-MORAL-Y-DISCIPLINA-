package com.hidalgoferrai.myapplication;

import android.app.Application;

/** Restaura la preferencia visual antes de mostrar cualquier pantalla. */
public final class FaltosApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        // Antes que nada: si algo tumba la aplicación, que quede anotado. Sin esto,
        // un cierre inesperado en un teléfono sin cable no deja ningún rastro.
        Diagnostico.instalar(this);
        Tema.aplicar(this);
        Diagnostico.paso(this, "aplicación iniciada");
    }
}
