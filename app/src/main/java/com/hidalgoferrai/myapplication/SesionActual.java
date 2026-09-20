package com.hidalgoferrai.myapplication;

import android.content.Context;
import org.json.JSONObject;

/** Comparte la sesión vigente entre módulos sin volver a entregar un refresco ya rotado. */
final class SesionActual {
    private static JSONObject actual;
    private static long revision;
    private static final Object RENOVACION = new Object();
    private SesionActual() { }

    static synchronized void recibir(Context contexto, String json) {
        if (json == null) return;
        try {
            JSONObject nueva = new JSONObject(json);
            if (nueva.optString("access_token").isEmpty() || !nueva.has("user")) return;
            if (!nueva.has("expires_at")) nueva.put("expires_at",
                    System.currentTimeMillis() / 1000 + nueva.optLong("expires_in", 3600));
            actual = nueva;
            revision++;
            AlmacenSeguro.guardarRefresco(contexto, nueva.optString("refresh_token", null));
        } catch (Exception ignored) { }
    }

    /** Ejecutar fuera del hilo principal. */
    static String obtener(Context contexto) throws Exception {
        synchronized (RENOVACION) {
            long version;
            synchronized (SesionActual.class) {
                if (actual != null && actual.optLong("expires_at", 0) > System.currentTimeMillis() / 1000 + 60)
                    return actual.toString();
                version = revision;
            }
            String refresco = AlmacenSeguro.refresco(contexto);
            if (refresco == null) throw new SupabaseAuth.ErrorApi(401, "{}");
            String renovada = SupabaseAuth.renovarSesion(refresco).toString();
            synchronized (SesionActual.class) {
                // Una respuesta tardía no resucita una sesión cerrada ni pisa una más reciente.
                if (revision == version) recibir(contexto, renovada);
                if (actual == null) throw new SupabaseAuth.ErrorApi(401, "{}");
                return actual.toString();
            }
        }
    }

    static synchronized void borrar() { actual = null; revision++; }
}
