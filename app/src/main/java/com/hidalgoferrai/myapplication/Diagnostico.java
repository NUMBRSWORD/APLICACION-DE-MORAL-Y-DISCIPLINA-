package com.hidalgoferrai.myapplication;

import android.content.Context;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Registro de diagnóstico para cuando algo falla en un teléfono al que no se puede
 * conectar un cable.
 *
 * Anota los PASOS del acceso, no su contenido: así, si una pantalla se queda cargando
 * para siempre, el archivo dice hasta dónde llegó. Y guarda los fallos que tumban la
 * aplicación, que de otro modo se pierden al cerrarse.
 *
 * REGLA: aquí no entra nada secreto. Ni tokens, ni códigos, ni claves, ni el código de
 * autorización de Google. Solo el nombre del paso y, si hay error, su tipo y el mensaje
 * ya saneado que la aplicación muestra al usuario. Este archivo se comparte por correo,
 * así que tiene que poder leerse sin riesgo.
 */
public final class Diagnostico {
    private static final String ARCHIVO = "diagnostico.txt";
    /** Un archivo que crece sin freno acabaría llenando el teléfono. */
    private static final long TOPE_BYTES = 128 * 1024;

    private Diagnostico() {
    }

    /**
     * Almacenamiento interno de la aplicación: ninguna otra app puede leerlo. El externo
     * (Android/data) es legible por otras apps con permiso de almacenamiento en Android 7–10.
     */
    public static File archivo(Context contexto) {
        return new File(contexto.getFilesDir(), ARCHIVO);
    }

    /** Anota un paso. El texto debe ser corto y sin datos personales ni secretos. */
    public static void paso(Context contexto, String texto) {
        escribir(contexto, texto);
    }

    /** Anota un fallo: su tipo y su mensaje, nunca la respuesta cruda del servidor. */
    public static void fallo(Context contexto, String donde, Throwable error) {
        escribir(contexto, donde + " → " + error.getClass().getSimpleName()
                + ": " + String.valueOf(error.getMessage()));
    }

    /**
     * Recoge los fallos que cierran la aplicación. Se instala una sola vez, al arrancar,
     * y respeta al manejador anterior para no tragarse el informe del sistema.
     */
    public static void instalar(Context contexto) {
        final Context app = contexto.getApplicationContext();
        // Las versiones 1.7 iniciales lo escribían en el almacenamiento externo.
        try {
            File externo = app.getExternalFilesDir(null);
            if (externo != null) new File(externo, ARCHIVO).delete();
        } catch (SecurityException ignorado) { /* Nada que limpiar. */ }
        final Thread.UncaughtExceptionHandler anterior = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((hilo, error) -> {
            try {
                StringWriter pila = new StringWriter();
                error.printStackTrace(new PrintWriter(pila));
                escribir(app, "CIERRE INESPERADO en " + hilo.getName() + "\n" + pila);
            } catch (RuntimeException ignorado) {
                // Si ni esto se puede escribir, al menos que siga el manejador del sistema.
            }
            if (anterior != null) anterior.uncaughtException(hilo, error);
        });
    }

    private static synchronized void escribir(Context contexto, String texto) {
        File destino = archivo(contexto);
        if (destino == null) return;
        try {
            if (destino.exists() && destino.length() > TOPE_BYTES) {
                // Se empieza de cero en vez de recortar: lo último es lo que interesa.
                if (!destino.delete()) return;
            }
            String sello = new SimpleDateFormat("dd/MM HH:mm:ss", Locale.US).format(new Date());
            try (FileWriter escritor = new FileWriter(destino, true)) {
                escritor.write(sello + "  " + texto + "\n");
            }
        } catch (java.io.IOException | SecurityException ignorado) {
            // El diagnóstico nunca puede ser la causa de un fallo.
        }
    }
}
