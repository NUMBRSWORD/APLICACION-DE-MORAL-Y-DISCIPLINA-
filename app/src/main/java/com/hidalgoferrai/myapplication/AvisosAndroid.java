package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import com.google.firebase.FirebaseApp;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONObject;

/** Registro voluntario del dispositivo para avisos genéricos, nunca datos del expediente. */
final class AvisosAndroid {
    private static final String ARCHIVO = "avisos_android";
    private static final String HABILITADO = "habilitado";
    private static final String USUARIO = "usuario";
    private static final Handler PRINCIPAL = new Handler(Looper.getMainLooper());

    private AvisosAndroid() { }

    static boolean configurado(Context contexto) {
        return !FirebaseApp.getApps(contexto.getApplicationContext()).isEmpty();
    }

    static boolean habilitado(Context contexto) {
        String usuario = Perfil.usuarioId(contexto);
        return usuario != null && usuario.equals(preferencias(contexto).getString(USUARIO, null))
                && preferencias(contexto).getBoolean(HABILITADO, false);
    }

    static void registrar(Context contexto, Runnable exito, Runnable fallo) {
        if (!configurado(contexto)) {
            PRINCIPAL.post(fallo);
            return;
        }
        Context app = contexto.getApplicationContext();
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(tarea -> {
            if (!tarea.isSuccessful() || tarea.getResult() == null) {
                PRINCIPAL.post(fallo);
                return;
            }
            registrarToken(app, tarea.getResult(), exito, fallo);
        });
    }

    /** FCM puede renovar el token sin abrir la app; reintenta al volver al inicio si falla. */
    static void tokenRenovado(Context contexto, String tokenFcm) {
        if (habilitado(contexto)) registrarToken(contexto.getApplicationContext(), tokenFcm,
                () -> { }, () -> { });
    }

    private static void registrarToken(Context contexto, String tokenFcm,
                                      Runnable exito, Runnable fallo) {
        new Thread(() -> {
            try {
                JSONObject sesion = new JSONObject(SesionActual.obtener(contexto));
                String usuarioSesion = sesion.getJSONObject("user").getString("id");
                String usuarioPerfil = Perfil.usuarioId(contexto);
                if (!usuarioSesion.equals(usuarioPerfil)) throw new IllegalStateException();
                SupabaseApi.registrarDispositivoAndroid(sesion.getString("access_token"), tokenFcm);
                // El cierre de sesión puede ocurrir mientras la petición está en vuelo.
                if (!usuarioSesion.equals(Perfil.usuarioId(contexto))) {
                    SupabaseApi.quitarDispositivoAndroid(sesion.getString("access_token"), tokenFcm);
                    throw new IllegalStateException();
                }
                preferencias(contexto).edit().putString(USUARIO, usuarioSesion)
                        .putBoolean(HABILITADO, true).apply();
                FirebaseMessaging.getInstance().setAutoInitEnabled(true);
                PRINCIPAL.post(exito);
            } catch (Exception ignored) {
                PRINCIPAL.post(fallo);
            }
        }, "registrar-avisos").start();
    }

    /** Borra el permiso local de inmediato e invalida el token aun si la red no responde. */
    static void cerrarSesion(Context contexto, String tokenSesion) {
        Context app = contexto.getApplicationContext();
        boolean registrado = preferencias(app).getBoolean(HABILITADO, false);
        preferencias(app).edit().clear().apply();
        if (!configurado(app)) return;
        FirebaseMessaging mensajeria = FirebaseMessaging.getInstance();
        mensajeria.setAutoInitEnabled(false);
        if (!registrado) {
            mensajeria.deleteToken();
            return;
        }
        mensajeria.getToken().addOnCompleteListener(tarea -> {
            String tokenFcm = tarea.isSuccessful() ? tarea.getResult() : null;
            new Thread(() -> {
                if (tokenSesion != null && tokenFcm != null) {
                    try {
                        SupabaseApi.quitarDispositivoAndroid(tokenSesion, tokenFcm);
                    } catch (Exception ignored) { }
                }
                mensajeria.deleteToken();
            }, "quitar-avisos").start();
        });
    }

    private static SharedPreferences preferencias(Context contexto) {
        return contexto.getApplicationContext().getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }
}
