package com.hidalgoferrai.myapplication;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.security.KeyStore;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Guarda el secreto del token cifrado con una clave del almacén de claves de Android.
 * Esa clave no sale del dispositivo y no se puede exportar, así que el secreto no queda
 * legible aunque alguien saque una copia de los datos de la aplicación.
 */
public final class AlmacenSeguro {

    private static final String ARCHIVO = "token_seguro";
    private static final String CLAVE_SECRETO = "totp_secreto";
    private static final String CLAVE_FACTOR = "totp_factor";
    private static final String CLAVE_USUARIO = "totp_usuario";
    private static final String CLAVE_VERIFICADO = "totp_verificado";
    private static final String CLAVE_REFRESCO = "refresco";
    private static final String ALIAS = "faltas_totp";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private static final int TAM_IV = 12;
    private static final int TAM_ETIQUETA = 128;

    private AlmacenSeguro() {
    }

    public static void guardarToken(Context contexto, String usuarioId, String factorId,
                                    String secreto) {
        guardarToken(contexto, usuarioId, factorId, secreto, true);
    }

    public static void guardarTokenPendiente(Context contexto, String usuarioId, String factorId,
                                             String secreto) {
        guardarToken(contexto, usuarioId, factorId, secreto, false);
    }

    private static void guardarToken(Context contexto, String usuarioId, String factorId,
                                     String secreto, boolean verificado) {
        if (usuarioId == null || usuarioId.isEmpty()) {
            throw new IllegalArgumentException("El token debe pertenecer a un usuario");
        }
        try {
            boolean guardado = prefs(contexto).edit()
                    .putString(CLAVE_USUARIO, usuarioId)
                    .putString(CLAVE_FACTOR, factorId)
                    .putString(CLAVE_SECRETO, cifrar(secreto))
                    .putBoolean(CLAVE_VERIFICADO, verificado)
                    .commit();
            if (!guardado) throw new IllegalStateException("No se pudo guardar el token");
        } catch (GeneralSecurityException | java.io.IOException e) {
            throw new IllegalStateException("No se pudo guardar el token", e);
        }
    }

    public static boolean tokenVerificado(Context contexto, String usuarioId) {
        return esDelUsuario(contexto, usuarioId)
                && prefs(contexto).getBoolean(CLAVE_VERIFICADO, false);
    }

    public static void marcarTokenVerificado(Context contexto, String usuarioId) {
        if (esDelUsuario(contexto, usuarioId))
            prefs(contexto).edit().putBoolean(CLAVE_VERIFICADO, true).apply();
    }

    public static String secreto(Context contexto, String usuarioId) {
        if (!esDelUsuario(contexto, usuarioId)) {
            return null;
        }
        String guardado = prefs(contexto).getString(CLAVE_SECRETO, null);
        if (guardado == null) {
            return null;
        }
        try {
            return descifrar(guardado);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            // La clave del almacén se perdió (por ejemplo, tras restaurar el teléfono):
            // el token deja de servir y hay que volver a activarlo.
            borrar(contexto);
            return null;
        }
    }

    public static String factorId(Context contexto, String usuarioId) {
        return esDelUsuario(contexto, usuarioId)
                ? prefs(contexto).getString(CLAVE_FACTOR, null) : null;
    }

    /**
     * Guarda cifrada la llave de refresco de la sesión. Con ella la aplicación puede
     * pedir una sesión nueva al servidor sin volver a iniciar sesión con Google, que es
     * lo que necesita para activar el token o volver a entrar.
     */
    public static void guardarRefresco(Context contexto, String refresco) {
        if (refresco == null || refresco.isEmpty()) {
            return;
        }
        try {
            prefs(contexto).edit().putString(CLAVE_REFRESCO, cifrar(refresco)).apply();
        } catch (GeneralSecurityException | java.io.IOException e) {
            // Sin llave de refresco la aplicación sigue funcionando: solo pedirá
            // iniciar sesión otra vez cuando la necesite.
        }
    }

    public static String refresco(Context contexto) {
        String guardado = prefs(contexto).getString(CLAVE_REFRESCO, null);
        if (guardado == null) {
            return null;
        }
        try {
            return descifrar(guardado);
        } catch (GeneralSecurityException | java.io.IOException | IllegalArgumentException e) {
            prefs(contexto).edit().remove(CLAVE_REFRESCO).apply();
            return null;
        }
    }

    public static boolean tieneToken(Context contexto, String usuarioId) {
        return secreto(contexto, usuarioId) != null;
    }

    /** Factor creado por una versión anterior, antes de guardar el propietario. */
    public static String factorLegacy(Context contexto) {
        SharedPreferences p = prefs(contexto);
        return p.getString(CLAVE_USUARIO, null) == null
                && p.contains(CLAVE_SECRETO) ? p.getString(CLAVE_FACTOR, null) : null;
    }

    /** Vincula un token antiguo solo después de comprobar en el servidor que pertenece a la cuenta. */
    public static void vincularTokenLegacy(Context contexto, String usuarioId, String factorId) {
        SharedPreferences p = prefs(contexto);
        if (p.getString(CLAVE_USUARIO, null) == null
                && factorId != null && factorId.equals(p.getString(CLAVE_FACTOR, null))) {
            p.edit().putString(CLAVE_USUARIO, usuarioId).apply();
        }
    }

    public static void borrar(Context contexto) {
        prefs(contexto).edit()
                .remove(CLAVE_SECRETO)
                .remove(CLAVE_FACTOR)
                .remove(CLAVE_USUARIO)
                .remove(CLAVE_VERIFICADO)
                .apply();
    }

    /** Al cerrar sesión se borra la llave de refresco; el token se conserva. */
    public static void borrarRefresco(Context contexto) {
        prefs(contexto).edit().remove(CLAVE_REFRESCO).apply();
    }

    private static SharedPreferences prefs(Context contexto) {
        return contexto.getApplicationContext()
                .getSharedPreferences(ARCHIVO, Context.MODE_PRIVATE);
    }

    private static boolean esDelUsuario(Context contexto, String usuarioId) {
        return usuarioId != null && usuarioId.equals(
                prefs(contexto).getString(CLAVE_USUARIO, null));
    }

    private static String cifrar(String texto) throws GeneralSecurityException, java.io.IOException {
        Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
        cifrador.init(Cipher.ENCRYPT_MODE, clave());
        byte[] iv = cifrador.getIV();
        byte[] datos = cifrador.doFinal(texto.getBytes("UTF-8"));
        byte[] todo = new byte[iv.length + datos.length];
        System.arraycopy(iv, 0, todo, 0, iv.length);
        System.arraycopy(datos, 0, todo, iv.length, datos.length);
        return Base64.encodeToString(todo, Base64.NO_WRAP);
    }

    private static String descifrar(String guardado)
            throws GeneralSecurityException, java.io.IOException {
        byte[] todo = Base64.decode(guardado, Base64.NO_WRAP);
        if (todo.length <= TAM_IV) {
            throw new IllegalArgumentException("Dato corrupto");
        }
        byte[] iv = new byte[TAM_IV];
        System.arraycopy(todo, 0, iv, 0, TAM_IV);
        byte[] datos = new byte[todo.length - TAM_IV];
        System.arraycopy(todo, TAM_IV, datos, 0, datos.length);
        Cipher cifrador = Cipher.getInstance(TRANSFORMACION);
        cifrador.init(Cipher.DECRYPT_MODE, clave(), new GCMParameterSpec(TAM_ETIQUETA, iv));
        return new String(cifrador.doFinal(datos), "UTF-8");
    }

    private static SecretKey clave() throws GeneralSecurityException, java.io.IOException {
        KeyStore almacen = KeyStore.getInstance("AndroidKeyStore");
        almacen.load(null);
        KeyStore.Entry entrada = almacen.getEntry(ALIAS, null);
        if (entrada instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) entrada).getSecretKey();
        }
        KeyGenerator generador = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generador.init(new KeyGenParameterSpec.Builder(ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generador.generateKey();
    }
}
