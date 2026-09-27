package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Aviso de versión nueva.
 *
 * La aplicación se reparte por archivo APK, fuera de la tienda, así que nadie se entera
 * de que salió una versión salvo que se le avise. Esto solo consulta un archivo publicado
 * en la propia web y muestra un aviso con el enlace: nunca descarga ni instala nada por su
 * cuenta. Si la consulta falla, la pantalla sigue igual que siempre.
 */
public final class Actualizacion {

    private static final String FUENTE =
            "https://numbrsword.github.io/moral-y-disciplina/descargas/version.json";
    private static final int MAX_BYTES = 8 * 1024;

    /** Respuesta simulada; solo la usan las pruebas de la variante qa. */
    static volatile String respuestaPrueba;

    /** Datos de la versión publicada, o null si no hay ninguna más nueva. */
    public static final class Nueva {
        public final String version;
        public final String url;

        Nueva(String version, String url) {
            this.version = version;
            this.url = url;
        }
    }

    private Actualizacion() {
    }

    /** Bloqueante: llamar fuera del hilo principal. Devuelve null si ya está al día. */
    public static Nueva comprobar(Context contexto) {
        try {
            JSONObject datos = new JSONObject(respuestaPrueba != null ? respuestaPrueba : descargar());
            long publicada = datos.optLong("versionCode", 0);
            if (publicada <= instalada(contexto)) return null;
            String url = datos.optString("url", "");
            if (!url.startsWith("https://")) return null;
            return new Nueva(datos.optString("versionName", ""), url);
        } catch (Exception e) {
            // Sin conexión o respuesta rara: no se avisa nada.
            return null;
        }
    }

    private static long instalada(Context contexto) throws PackageManager.NameNotFoundException {
        PackageInfo info = contexto.getPackageManager().getPackageInfo(contexto.getPackageName(), 0);
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;
    }

    private static String descargar() throws IOException {
        HttpURLConnection conexion = (HttpURLConnection) new URL(FUENTE).openConnection();
        conexion.setConnectTimeout(8000);
        conexion.setReadTimeout(8000);
        conexion.setUseCaches(false);
        conexion.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (conexion.getResponseCode() != 200) throw new IOException("Sin archivo de versión");
            try (InputStream entrada = conexion.getInputStream()) {
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1) {
                    if (salida.size() + leidos > MAX_BYTES) throw new IOException("Respuesta demasiado grande");
                    salida.write(buffer, 0, leidos);
                }
                return salida.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conexion.disconnect();
        }
    }
}
