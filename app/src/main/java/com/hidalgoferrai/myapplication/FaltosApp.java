package com.hidalgoferrai.myapplication;

import android.app.Application;

/** Restaura la preferencia visual antes de mostrar cualquier pantalla. */
public final class FaltosApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Tema.aplicar(this);
    }
}
