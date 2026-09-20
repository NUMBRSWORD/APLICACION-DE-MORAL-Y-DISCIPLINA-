package com.hidalgoferrai.myapplication;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Código de un solo uso, el mismo sistema de los tokens bancarios (TOTP, RFC 6238):
 * 6 dígitos que cambian cada 30 segundos, calculados a partir de un secreto compartido
 * con el servidor. No viaja nada por la red para generarlo.
 */
public final class Totp {

    public static final int PERIODO_SEGUNDOS = 30;
    private static final int DIGITOS = 6;
    private static final String BASE32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private Totp() {
    }

    public static String codigo(String secretoBase32) {
        return codigo(secretoBase32, System.currentTimeMillis() / 1000L);
    }

    public static String codigo(String secretoBase32, long epochSegundos) {
        byte[] clave = decodificarBase32(secretoBase32);
        long contador = epochSegundos / PERIODO_SEGUNDOS;
        byte[] mensaje = new byte[8];
        for (int i = 7; i >= 0; i--) {
            mensaje[i] = (byte) (contador & 0xFF);
            contador >>>= 8;
        }
        byte[] resumen = hmacSha1(clave, mensaje);
        int desplazamiento = resumen[resumen.length - 1] & 0x0F;
        int binario = ((resumen[desplazamiento] & 0x7F) << 24)
                | ((resumen[desplazamiento + 1] & 0xFF) << 16)
                | ((resumen[desplazamiento + 2] & 0xFF) << 8)
                | (resumen[desplazamiento + 3] & 0xFF);
        int valor = binario % 1000000;
        return String.format(java.util.Locale.ROOT, "%0" + DIGITOS + "d", valor);
    }

    /** Segundos que le quedan de vida al código que se está mostrando. */
    public static int segundosRestantes() {
        return PERIODO_SEGUNDOS - (int) ((System.currentTimeMillis() / 1000L) % PERIODO_SEGUNDOS);
    }

    private static byte[] hmacSha1(byte[] clave, byte[] mensaje) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(clave, "HmacSHA1"));
            return mac.doFinal(mensaje);
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    static byte[] decodificarBase32(String texto) {
        String limpio = texto.trim().replace("=", "").replace(" ", "").toUpperCase(java.util.Locale.ROOT);
        int bits = 0;
        int acumulado = 0;
        java.io.ByteArrayOutputStream salida = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < limpio.length(); i++) {
            int valor = BASE32.indexOf(limpio.charAt(i));
            if (valor < 0) {
                throw new IllegalArgumentException("Secreto inválido");
            }
            acumulado = (acumulado << 5) | valor;
            bits += 5;
            if (bits >= 8) {
                bits -= 8;
                salida.write((acumulado >> bits) & 0xFF);
            }
        }
        return salida.toByteArray();
    }
}
