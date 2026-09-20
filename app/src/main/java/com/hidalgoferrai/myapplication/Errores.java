package com.hidalgoferrai.myapplication;

import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.net.ConnectException;

/** Mensajes recuperables sin filtrar el cuerpo de respuesta del servidor. */
final class Errores {
    private Errores() { }

    static boolean esSesion(Exception error) {
        if (!(error instanceof SupabaseAuth.ErrorApi)) return false;
        SupabaseAuth.ErrorApi api = (SupabaseAuth.ErrorApi) error;
        return api.estado == 401 || api.codigo.startsWith("refresh_token_")
                || "session_not_found".equals(api.codigo) || "bad_jwt".equals(api.codigo);
    }

    static int mensaje(Exception error) {
        if (esSesion(error)) return R.string.error_sesion;
        if (error instanceof SocketTimeoutException) return R.string.error_tiempo;
        if (error instanceof UnknownHostException || error instanceof ConnectException)
            return R.string.error_conexion;
        if (error instanceof SupabaseAuth.ErrorApi) {
            SupabaseAuth.ErrorApi api = (SupabaseAuth.ErrorApi) error;
            if (api.estado == 429) return R.string.error_limite;
            if (api.codigo.contains("verify_not_enabled") || api.codigo.contains("enroll_not_enabled"))
                return R.string.error_mfa_desactivado;
            if (api.codigo.contains("verification_failed") || api.codigo.contains("challenge_expired"))
                return R.string.error_codigo;
            if (api.codigo.contains("factor_name_conflict") || api.codigo.contains("factor_limit"))
                return R.string.error_factores;
            if (api.estado == 403 || "42501".equals(api.codigo)) return R.string.error_permisos;
            if (api.estado >= 500) return R.string.error_servidor;
        }
        return R.string.error_generico;
    }
}
