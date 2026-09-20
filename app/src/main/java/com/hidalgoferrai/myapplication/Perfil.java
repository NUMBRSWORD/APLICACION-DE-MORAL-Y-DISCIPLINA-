package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Datos de la persona que la pantalla de inicio necesita mostrar sin conexión:
 * su grado, su nombre y su rol. No guarda nada sensible.
 */
public final class Perfil {

    private static final String ARCHIVO = "perfil";
    private static final String GRADO = "grado";
    private static final String NOMBRE = "nombre";
    private static final String ROL = "rol";
    private static final String USUARIO_ID = "usuario_id";

    private Perfil() {
    }

    public static void guardar(Context contexto, String usuarioId, String grado, String nombre,
                               String rol) {
        SharedPreferences.Editor editor = prefs(contexto).edit();
        if (usuarioId != null && !usuarioId.equals(prefs(contexto).getString(USUARIO_ID, null))) {
            editor.clear();
        }
        if (usuarioId != null && !usuarioId.isEmpty()) {
            editor.putString(USUARIO_ID, usuarioId);
        }
        if (grado != null && !grado.isEmpty()) {
            editor.putString(GRADO, grado);
        }
        if (nombre != null && !nombre.isEmpty()) {
            editor.putString(NOMBRE, nombre);
        }
        if (rol != null && !rol.isEmpty()) {
            editor.putString(ROL, rol);
        }
        editor.apply();
    }

    public static String grado(Context contexto) {
        return prefs(contexto).getString(GRADO, "");
    }

    public static String nombre(Context contexto) {
        return prefs(contexto).getString(NOMBRE, "");
    }

    public static boolean esAdministrador(Context contexto) {
        return "admin".equals(prefs(contexto).getString(ROL, null));
    }

    public static String usuarioId(Context contexto) {
        return prefs(contexto).getString(USUARIO_ID, null);
    }

    public static void borrar(Context contexto) {
        prefs(contexto).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context contexto) {
        return contexto.getApplicationContext().getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }
}
