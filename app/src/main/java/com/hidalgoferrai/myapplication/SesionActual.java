package com.hidalgoferrai.myapplication;

import android.content.Context;
import org.json.JSONObject;

/** Comparte la sesión vigente entre módulos sin volver a entregar un refresco ya rotado. */
final class SesionActual {
    private static JSONObject actual;
    private static long revision;
    private static long cuentaRevision;
    private static final Object RENOVACION = new Object();
    private SesionActual() { }

    /** Los Intent sobreviven a la rotación: nunca deben reemplazar un refresco ya rotado. */
    static synchronized void restaurar(Context contexto, String json) {
        if (actual == null) recibir(contexto, json);
    }

    static synchronized long epoca() { return cuentaRevision; }

    static synchronized void recibirSiVigente(Context contexto, String json, long epoca) {
        if (epoca == cuentaRevision) recibir(contexto, json);
    }

    static synchronized String usuario() {
        return actual == null ? null : actual.optJSONObject("user").optString("id", null);
    }

    static synchronized String accesoEnMemoria() {
        return actual == null ? null : actual.optString("access_token", null);
    }

    /** Ejecutar desde la interfaz. Se conserva solo el TOTP cifrado ligado a su propietario. */
    static void cerrarLocal(Context contexto) {
        AvisosAndroid.cerrarSesion(contexto, accesoEnMemoria());
        borrar();
        AlmacenSeguro.borrarRefresco(contexto);
        Perfil.borrar(contexto);
        android.webkit.WebStorage.getInstance().deleteAllData();
        android.webkit.CookieManager.getInstance().removeAllCookies(null);
        android.webkit.CookieManager.getInstance().flush();
    }

    /** Ejecutar en un trabajador; impide operar sobre otra cuenta desde una pantalla antigua. */
    static String token(Context contexto, String usuarioEsperado)
            throws java.io.IOException, org.json.JSONException {
        JSONObject sesion = new JSONObject(obtener(contexto));
        if (usuarioEsperado != null && !usuarioEsperado.equals(sesion.getJSONObject("user").getString("id")))
            throw new SupabaseAuth.ErrorApi(401, "{}");
        return sesion.getString("access_token");
    }

    static synchronized void recibir(Context contexto, String json) {
        if (json == null) return;
        try {
            JSONObject nueva = new JSONObject(json);
            JSONObject usuario = nueva.optJSONObject("user");
            if (nueva.optString("access_token").isEmpty() || usuario == null
                    || usuario.optString("id").isEmpty()) return;
            if (!nueva.has("expires_at")) nueva.put("expires_at",
                    System.currentTimeMillis() / 1000 + nueva.optLong("expires_in", 3600));
            if (actual != null && !usuario.optString("id").equals(usuario())) cuentaRevision++;
            actual = nueva;
            revision++;
            AlmacenSeguro.guardarRefresco(contexto, nueva.optString("refresh_token", null));
        } catch (Exception ignored) { }
    }

    /** Ejecutar fuera del hilo principal. */
    static String obtener(Context contexto) throws java.io.IOException, org.json.JSONException {
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

    static synchronized void borrar() { actual = null; revision++; cuentaRevision++; }
}
