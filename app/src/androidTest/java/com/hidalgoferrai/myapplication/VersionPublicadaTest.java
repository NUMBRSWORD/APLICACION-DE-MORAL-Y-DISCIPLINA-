package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeNoException;

/**
 * La aplicación se reparte por archivo APK y avisa de versiones nuevas comparándose con
 * un archivo publicado en la web. Si se sube un APK y se olvida ese archivo —o al revés—
 * el aviso deja de funcionar sin que nadie lo note. Esta prueba lee el archivo real y
 * comprueba que declara la misma versión que se acaba de compilar.
 *
 * Se omite sola si el equipo no tiene salida a internet.
 */
@RunWith(AndroidJUnit4.class)
public class VersionPublicadaTest {

    private static final String FUENTE =
            "https://numbrsword.github.io/moral-y-disciplina/descargas/version.json";

    @Test public void elArchivoDeVersionPublicadoCoincideConLaAppCompilada() throws Exception {
        Context contexto = InstrumentationRegistry.getInstrumentation().getTargetContext();
        JSONObject publicado;
        try {
            publicado = new JSONObject(descargar());
        } catch (IOException sinRed) {
            assumeNoException("Sin acceso a la web publicada", sinRed);
            return;
        }

        PackageInfo info = contexto.getPackageManager().getPackageInfo(contexto.getPackageName(), 0);
        long instalada = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                ? info.getLongVersionCode() : info.versionCode;

        assertEquals("El archivo de versión publicado no coincide con este APK",
                instalada, publicado.optLong("versionCode", -1));
        // La variante de pruebas añade «-qa» al nombre; lo que debe coincidir es la versión.
        assertEquals("El nombre de versión publicado no coincide con este APK",
                info.versionName.replaceFirst("-qa$", ""), publicado.optString("versionName"));
        assertTrue("El enlace de descarga debe ser https",
                publicado.optString("url").startsWith("https://"));
    }

    private static String descargar() throws IOException {
        HttpURLConnection conexion = (HttpURLConnection) new URL(FUENTE).openConnection();
        conexion.setConnectTimeout(15000);
        conexion.setReadTimeout(15000);
        conexion.setUseCaches(false);
        conexion.setRequestProperty("Cache-Control", "no-cache");
        try {
            if (conexion.getResponseCode() != 200) throw new IOException("Sin archivo de versión");
            try (InputStream entrada = conexion.getInputStream()) {
                ByteArrayOutputStream salida = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int leidos;
                while ((leidos = entrada.read(buffer)) != -1) salida.write(buffer, 0, leidos);
                return salida.toString(StandardCharsets.UTF_8.name());
            }
        } finally {
            conexion.disconnect();
        }
    }
}
