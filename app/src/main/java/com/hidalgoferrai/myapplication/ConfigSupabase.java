package com.hidalgoferrai.myapplication;

import android.content.Context;

public final class ConfigSupabase {

    public static final String URL = "https://tndjulaitywtoocqeeiy.supabase.co";

    // Clave pública (rol anon), la misma que ya publica la aplicación web en config.js.
    public static final String ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InRuZGp1bGFpdHl3dG9vY3FlZWl5Iiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODYzMTM2OTAsImV4cCI6MjEwMTg4OTY5MH0.PS6H11vN4jd_JIhAVVg1VPw4cy8s2L4_7VuTtLUNFiw";

    /**
     * Dirección de vuelta del acceso con Google. El esquema lo define cada variante
     * (`faltas` en la app real, `faltas-qa` en la de pruebas), igual que el filtro del
     * manifiesto: así la instalación de pruebas nunca recibe —ni entrega— la sesión de la
     * instalación real. Cada esquema debe estar autorizado en Supabase.
     */
    public static String redireccion(Context contexto) {
        return contexto.getString(R.string.oauth_esquema) + "://auth";
    }

    // Sube el número cuando cambien los términos o la política de datos: se vuelve a pedir la aceptación.
    public static final String VERSION_TERMINOS = "3";

    // Clave con la que supabase-js guarda la sesión en la aplicación web.
    public static final String CLAVE_SESION_WEB = "sb-tndjulaitywtoocqeeiy-auth-token";

    private ConfigSupabase() {
    }
}
