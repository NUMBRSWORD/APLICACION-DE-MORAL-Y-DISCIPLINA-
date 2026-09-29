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
    private static final java.util.concurrent.ExecutorService COLA = java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final String TOKEN = "token_fcm";

    private AvisosAndroid() { }

    static boolean configurado(Context contexto) {
        return !FirebaseApp.getApps(contexto.getApplicationContext()).isEmpty();
    }

    static boolean permitidos(Context contexto) {
        if (!androidx.core.app.NotificationManagerCompat.from(contexto).areNotificationsEnabled()) return false;
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            android.app.NotificationManager administrador = contexto.getSystemService(android.app.NotificationManager.class);
            android.app.NotificationChannel canal = administrador == null ? null
                    : administrador.getNotificationChannel(AvisosFirebaseService.CANAL);
            if (canal != null && canal.getImportance() == android.app.NotificationManager.IMPORTANCE_NONE) return false;
        }
        return true;
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
        registrarToken(app, null, exito, fallo);
    }

    /** FCM puede renovar el token sin abrir la app; reintenta al volver al inicio si falla. */
    static void tokenRenovado(Context contexto, String tokenFcm) {
        if (habilitado(contexto)) registrarToken(contexto.getApplicationContext(), tokenFcm,
                () -> { }, () -> { });
    }

    private static void registrarToken(Context contexto, String tokenFcm,
                                      Runnable exito, Runnable fallo) {
        long epoca = SesionActual.epoca();
        String usuarioEsperado = Perfil.usuarioId(contexto);
        java.util.concurrent.atomic.AtomicBoolean terminado = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable limite = () -> { if (terminado.compareAndSet(false, true)) fallo.run(); };
        PRINCIPAL.postDelayed(limite, 60000);
        COLA.execute(() -> {
            try {
                if (epoca != SesionActual.epoca() || usuarioEsperado == null) throw new IllegalStateException();
                String fcm = tokenFcm != null ? tokenFcm : com.google.android.gms.tasks.Tasks.await(
                        FirebaseMessaging.getInstance().getToken(), 25, java.util.concurrent.TimeUnit.SECONDS);
                JSONObject sesion = new JSONObject(SesionActual.obtener(contexto));
                String usuarioSesion = sesion.getJSONObject("user").getString("id");
                if (epoca != SesionActual.epoca() || !usuarioSesion.equals(usuarioEsperado)
                        || !usuarioSesion.equals(Perfil.usuarioId(contexto))) throw new IllegalStateException();
                SupabaseApi.registrarDispositivoAndroid(sesion.getString("access_token"), fcm);
                // El cierre de sesión puede ocurrir mientras la petición está en vuelo.
                if (epoca != SesionActual.epoca() || !usuarioSesion.equals(Perfil.usuarioId(contexto))) {
                    SupabaseApi.quitarDispositivoAndroid(sesion.getString("access_token"), fcm);
                    throw new IllegalStateException();
                }
                PRINCIPAL.post(() -> {
                    // La comprobación y la escritura se ejecutan en el mismo hilo que Salir.
                    if (epoca != SesionActual.epoca() || !usuarioSesion.equals(Perfil.usuarioId(contexto))) return;
                    preferencias(contexto).edit().putString(USUARIO, usuarioSesion).putString(TOKEN, fcm)
                            .putBoolean(HABILITADO, true).apply();
                    FirebaseMessaging.getInstance().setAutoInitEnabled(true);
                    PRINCIPAL.removeCallbacks(limite);
                    if (terminado.compareAndSet(false, true)) exito.run();
                });
            } catch (Exception ignored) {
                PRINCIPAL.post(() -> {
                    PRINCIPAL.removeCallbacks(limite);
                    if (terminado.compareAndSet(false, true)) fallo.run();
                });
            }
        });
    }

    /** Revoca el permiso local inmediatamente. El token FCM identifica la instalación, no la cuenta. */
    static void cerrarSesion(Context contexto, String tokenSesion) {
        Context app = contexto.getApplicationContext();
        String tokenGuardado = preferencias(app).getString(TOKEN, null);
        preferencias(app).edit().clear().apply();
        androidx.core.app.NotificationManagerCompat.from(app).cancelAll();
        if (configurado(app)) FirebaseMessaging.getInstance().setAutoInitEnabled(false);
        // Serializar con el registro evita retirar la asociación de una cuenta nueva.
        // No lanzar deleteToken: una respuesta tardía podría invalidar un registro posterior.
        COLA.execute(() -> {
            if (configurado(app)) {
                if (tokenSesion != null && tokenGuardado != null) {
                    try { SupabaseApi.quitarDispositivoAndroid(tokenSesion, tokenGuardado); }
                    catch (Exception ignored) { }
                }
            }
            if (tokenSesion != null) {
                try { SupabaseAuth.cerrarSesion(tokenSesion); }
                catch (Exception ignored) { }
            }
        });
    }

    private static SharedPreferences preferencias(Context contexto) {
        return contexto.getApplicationContext().getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }
}
