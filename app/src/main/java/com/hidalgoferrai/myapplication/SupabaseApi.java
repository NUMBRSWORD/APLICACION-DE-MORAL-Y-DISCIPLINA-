package com.hidalgoferrai.myapplication;

import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * Llamadas a Supabase para las políticas institucionales y para el token de un solo uso.
 * Todas son bloqueantes: usarlas fuera del hilo principal.
 */
public final class SupabaseApi {

    private SupabaseApi() {
    }

    /** El servidor es quien determina si una cuenta tiene un cambio de clave pendiente. */
    public static boolean necesitaCambiarClave(String token) throws IOException {
        String valor = SupabaseAuth.pedir("POST", "/rest/v1/rpc/necesita_cambiar_clave", token, "{}", null).trim();
        if (!"true".equals(valor) && !"false".equals(valor)) throw new IOException("Estado de seguridad no disponible");
        return "true".equals(valor);
    }

    // ---------- Eliminación de la cuenta a pedido de la persona ----------

    /** Devuelve "ok", "ya_pendiente" o "sin_sesion"; el servidor evita pedidos repetidos. */
    public static String solicitarEliminacion(String token, String motivo)
            throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject().put("p_motivo", motivo == null ? JSONObject.NULL : motivo);
        return SupabaseAuth.pedir("POST", "/rest/v1/rpc/solicitar_eliminacion_cuenta", token,
                cuerpo.toString(), null).trim().replace("\"", "");
    }

    public static boolean tieneEliminacionPendiente(String token) throws IOException {
        String valor = SupabaseAuth.pedir("POST", "/rest/v1/rpc/tiene_eliminacion_pendiente",
                token, "{}", null).trim();
        return "true".equals(valor);
    }

    public static JSONArray pendientes(String token, int offset) throws IOException, JSONException {
        String ruta = "/rest/v1/notas_informativas?select=id,grado,apellidos,nombres,numero_nota_falta,"
                + "fecha_falta,codigo_infraccion,fecha_reincorporacion,imputacion_generada_at,fecha_descargo,"
                + "orden_sancion_generada_at,orden_notificada_at,archivo_leve_generada_at"
                + "&orden_notificada_at=is.null&archivo_leve_generada_at=is.null"
                + "&order=fecha_falta.desc,id.asc&limit=100&offset=" + Math.max(0, offset);
        return new JSONArray(SupabaseAuth.pedir("GET", ruta, token, null, null));
    }

    // ---------- Datos declarados por la persona ----------

    public static JSONObject miSolicitud(String token, String usuarioId)
            throws IOException, JSONException {
        String ruta = "/rest/v1/solicitudes_acceso?select=grado,apellidos,nombres&user_id=eq."
                + Uri.encode(usuarioId);
        JSONArray filas = new JSONArray(SupabaseAuth.pedir("GET", ruta, token, null, null));
        return filas.length() == 0 ? null : filas.getJSONObject(0);
    }

    /** "admin" o "viewer"; null si no se pudo leer. */
    public static String rol(String token, String usuarioId) throws IOException, JSONException {
        String ruta = "/rest/v1/profiles?select=role&id=eq." + Uri.encode(usuarioId);
        JSONArray filas = new JSONArray(SupabaseAuth.pedir("GET", ruta, token, null, null));
        return filas.length() == 0 ? null : filas.getJSONObject(0).optString("role", null);
    }

    // ---------- Políticas institucionales ----------

    public static JSONArray documentosInstitucionales(String token)
            throws IOException, JSONException {
        String ruta = "/rest/v1/documentos_institucionales"
                + "?select=id,titulo,contenido,version&order=created_at.asc";
        return new JSONArray(SupabaseAuth.pedir("GET", ruta, token, null, null));
    }

    /** Devuelve "idDocumento:version" de cada política ya firmada por esta persona. */
    public static Set<String> misFirmas(String token, String usuarioId)
            throws IOException, JSONException {
        String ruta = "/rest/v1/firmas_documentos"
                + "?select=documento_id,documento_version&firmante_id=eq." + Uri.encode(usuarioId);
        JSONArray filas = new JSONArray(SupabaseAuth.pedir("GET", ruta, token, null, null));
        Set<String> firmadas = new HashSet<>();
        for (int i = 0; i < filas.length(); i++) {
            JSONObject f = filas.getJSONObject(i);
            firmadas.add(f.getString("documento_id") + ":" + f.getInt("documento_version"));
        }
        return firmadas;
    }

    /** Nombre y grado con que firmó la última vez; sirve cuando no hay solicitud. */
    public static JSONObject miIdentidadFirmada(String token, String usuarioId)
            throws IOException, JSONException {
        String ruta = "/rest/v1/firmas_documentos"
                + "?select=firmante_nombre,firmante_grado&firmante_id=eq." + Uri.encode(usuarioId)
                + "&order=firmado_at.desc&limit=1";
        JSONArray filas = new JSONArray(SupabaseAuth.pedir("GET", ruta, token, null, null));
        return filas.length() == 0 ? null : filas.getJSONObject(0);
    }

    public static void firmar(String token, String documentoId, int version, String firmanteId,
                              String nombre, String grado, String cargo)
            throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("documento_id", documentoId)
                .put("documento_version", version)
                .put("firmante_id", firmanteId)
                .put("firmante_nombre", nombre)
                .put("firmante_grado", grado == null || grado.isEmpty() ? JSONObject.NULL : grado)
                .put("firmante_cargo", cargo);
        SupabaseAuth.pedir("POST", "/rest/v1/firmas_documentos", token, cuerpo.toString(),
                "return=minimal");
    }

    // ---------- Token de un solo uso (segundo factor) ----------

    /** Factor ya activado, o null si esta cuenta todavía no tiene token. */
    public static String factorVerificado(String token) throws IOException, JSONException {
        JSONArray todos = factores(token);
        for (int i = 0; i < todos.length(); i++) {
            JSONObject f = todos.getJSONObject(i);
            if ("verified".equals(f.optString("status")) && "totp".equals(f.optString("factor_type"))) {
                return f.getString("id");
            }
        }
        return null;
    }

    /** Los factores se obtienen de /user; /factors solo admite el alta mediante POST. */
    public static JSONArray factores(String token) throws IOException, JSONException {
        JSONObject usuario = new JSONObject(
                SupabaseAuth.pedir("GET", "/auth/v1/user", token, null, null));
        JSONArray factores = usuario.optJSONArray("factors");
        return factores == null ? new JSONArray() : factores;
    }

    /** Crea el token. Devuelve {id, secreto}; queda pendiente de activar. */
    public static JSONObject crearToken(String token, String nombreAmistoso)
            throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("factor_type", "totp")
                .put("friendly_name", nombreAmistoso);
        JSONObject r = new JSONObject(
                SupabaseAuth.pedir("POST", "/auth/v1/factors", token, cuerpo.toString(), null));
        return new JSONObject()
                .put("id", r.getString("id"))
                .put("secreto", r.getJSONObject("totp").getString("secret"));
    }

    public static String desafiar(String token, String factorId) throws IOException, JSONException {
        String ruta = "/auth/v1/factors/" + Uri.encode(factorId) + "/challenge";
        return new JSONObject(SupabaseAuth.pedir("POST", ruta, token, "{}", null)).getString("id");
    }

    /** Comprueba el código. Devuelve la sesión nueva, ya con el segundo factor cumplido. */
    public static JSONObject verificar(String token, String factorId, String desafioId, String codigo)
            throws IOException, JSONException {
        JSONObject cuerpo = new JSONObject()
                .put("challenge_id", desafioId)
                .put("code", codigo);
        String ruta = "/auth/v1/factors/" + Uri.encode(factorId) + "/verify";
        JSONObject sesion = new JSONObject(
                SupabaseAuth.pedir("POST", ruta, token, cuerpo.toString(), null));
        if (!sesion.has("expires_at")) {
            sesion.put("expires_at",
                    System.currentTimeMillis() / 1000 + sesion.optLong("expires_in", 3600));
        }
        return sesion;
    }
}
