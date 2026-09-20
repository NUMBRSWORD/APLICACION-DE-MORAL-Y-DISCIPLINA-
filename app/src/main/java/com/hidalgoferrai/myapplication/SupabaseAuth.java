package com.hidalgoferrai.myapplication;

import android.net.Uri;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

// Todos los métodos que llaman a la red son bloqueantes: usarlos fuera del hilo principal.
public final class SupabaseAuth {
    interface Transporte {
        String pedir(String metodo, String ruta, String token, String cuerpo, String preferencia)
                throws IOException;
    }

    // Punto de sustitución del transporte para las pruebas de la variante aislada QA.
    static volatile Transporte transporte = SupabaseAuth::pedirHttp;

    /** Error estructurado: la interfaz nunca muestra respuestas ni credenciales del servidor. */
    public static final class ErrorApi extends IOException {
        public final int estado;
        public final String codigo;

        ErrorApi(int estado, String respuesta) {
            super("HTTP " + estado);
            this.estado = estado;
            String valor = "";
            try {
                JSONObject json = new JSONObject(respuesta);
                valor = json.optString("error_code", json.optString("code", ""));
            } catch (JSONException ignored) { }
            codigo = valor;
        }
    }

    private static final int BASE64_URL = Base64.URL_SAFE | Base64.NO_WRAP | Base64.NO_PADDING;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SupabaseAuth() {
    }

    public static String nuevoVerificador() {
        byte[] bytes = new byte[48];
        ALEATORIO.nextBytes(bytes);
        return Base64.encodeToString(bytes, BASE64_URL);
    }

    private static String desafio(String verificador) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest(verificador.getBytes(StandardCharsets.US_ASCII));
            return Base64.encodeToString(resumen, BASE64_URL);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Uri urlAccesoGoogle(String verificador) {
        return Uri.parse(ConfigSupabase.URL + "/auth/v1/authorize").buildUpon()
                .appendQueryParameter("provider", "google")
                .appendQueryParameter("redirect_to", ConfigSupabase.REDIRECCION)
                .appendQueryParameter("code_challenge", desafio(verificador))
                .appendQueryParameter("code_challenge_method", "s256")
                .build();
    }

    public static JSONObject intercambiarCodigo(String codigo, String verificador)
            throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("auth_code", codigo)
                .put("code_verifier", verificador);
        JSONObject sesion = new JSONObject(pedir("POST", "/auth/v1/token?grant_type=pkce", null,
                cuerpo.toString(), null));
        // supabase-js espera expires_at en la sesión que lee del navegador. Si el servidor
        // no lo envía, se calcula aquí para que la app web no la dé por vencida.
        if (!sesion.has("expires_at")) {
            sesion.put("expires_at",
                    System.currentTimeMillis() / 1000 + sesion.optLong("expires_in", 3600));
        }
        return sesion;
    }

    /** Pide una sesión nueva con la llave de refresco, sin volver a pasar por Google. */
    public static JSONObject renovarSesion(String refresco) throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject().put("refresh_token", refresco);
        JSONObject sesion = new JSONObject(pedir("POST",
                "/auth/v1/token?grant_type=refresh_token", null, cuerpo.toString(), null));
        if (!sesion.has("expires_at")) {
            sesion.put("expires_at",
                    System.currentTimeMillis() / 1000 + sesion.optLong("expires_in", 3600));
        }
        return sesion;
    }

    /** Invalida la sesión actual en Supabase; el cierre local no depende de que responda. */
    public static void cerrarSesion(String token) throws IOException {
        pedir("POST", "/auth/v1/logout", token, "{}", null);
    }

    // Deja constancia de que la persona aceptó los términos y el tratamiento de sus datos.
    public static void registrarAceptacion(String token, String usuarioId)
            throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("user_id", usuarioId)
                .put("version", ConfigSupabase.VERSION_TERMINOS);
        pedir("POST", "/rest/v1/aceptaciones_terminos?on_conflict=user_id,version", token,
                cuerpo.toString(), "resolution=ignore-duplicates,return=minimal");
    }

    // Devuelve pendiente, aprobado o rechazado; null si no se pudo determinar (se trata como sin acceso).
    public static String estadoDeCuenta(String token, String usuarioId)
            throws IOException, JSONException {
        String ruta = "/rest/v1/profiles?select=estado&id=eq." + Uri.encode(usuarioId);
        JSONArray filas = new JSONArray(pedir("GET", ruta, token, null, null));
        return filas.length() == 0 ? null : filas.getJSONObject(0).optString("estado", null);
    }

    static String pedir(String metodo, String ruta, String token, String cuerpo,
                        String preferencia) throws IOException {
        return transporte.pedir(metodo, ruta, token, cuerpo, preferencia);
    }

    private static String pedirHttp(String metodo, String ruta, String token, String cuerpo,
                                    String preferencia) throws IOException {
        HttpURLConnection conexion = (HttpURLConnection) new URL(ConfigSupabase.URL + ruta).openConnection();
        try {
            conexion.setRequestMethod(metodo);
            conexion.setConnectTimeout(15000);
            conexion.setReadTimeout(20000);
            conexion.setRequestProperty("apikey", ConfigSupabase.ANON_KEY);
            conexion.setRequestProperty("Authorization",
                    "Bearer " + (token != null ? token : ConfigSupabase.ANON_KEY));
            conexion.setRequestProperty("Accept", "application/json");
            if (preferencia != null) {
                conexion.setRequestProperty("Prefer", preferencia);
            }
            if (cuerpo != null) {
                conexion.setDoOutput(true);
                conexion.setRequestProperty("Content-Type", "application/json");
                try (OutputStream salida = conexion.getOutputStream()) {
                    salida.write(cuerpo.getBytes(StandardCharsets.UTF_8));
                }
            }
            int codigo = conexion.getResponseCode();
            InputStream flujo = codigo >= 400 ? conexion.getErrorStream() : conexion.getInputStream();
            String respuesta = flujo == null ? "" : leer(flujo);
            if (codigo >= 400) {
                throw new ErrorApi(codigo, respuesta);
            }
            return respuesta;
        } finally {
            conexion.disconnect();
        }
    }

    private static String leer(InputStream flujo) throws IOException {
        try (InputStream entrada = flujo; ByteArrayOutputStream salida = new ByteArrayOutputStream()) {
            byte[] bloque = new byte[4096];
            int leidos;
            while ((leidos = entrada.read(bloque)) != -1) {
                salida.write(bloque, 0, leidos);
            }
            return salida.toString("UTF-8");
        }
    }

    // Datos con los que el administrador verifica la identidad antes de aprobar.
    // Se reenvía sobre la anterior si la persona corrige algo estando aún pendiente.
    public static void enviarSolicitud(String token, String usuarioId, String email, String grado,
                                       String apellidos, String nombres, String cip, String dni,
                                       String telefono) throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("user_id", usuarioId)
                .put("email", email)
                .put("grado", grado)
                .put("apellidos", apellidos)
                .put("nombres", nombres)
                .put("cip", cip)
                .put("dni", dni)
                .put("telefono", telefono);
        pedir("POST", "/rest/v1/solicitudes_acceso?on_conflict=user_id", token, cuerpo.toString(),
                "resolution=merge-duplicates,return=minimal");
    }

    public static boolean tieneSolicitud(String token, String usuarioId)
            throws IOException, JSONException {
        String ruta = "/rest/v1/solicitudes_acceso?select=user_id&user_id=eq." + Uri.encode(usuarioId);
        return new JSONArray(pedir("GET", ruta, token, null, null)).length() > 0;
    }
}
