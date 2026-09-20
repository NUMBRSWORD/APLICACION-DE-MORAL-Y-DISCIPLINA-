package com.hidalgoferrai.myapplication;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/** Vectores oficiales de RFC 6238 adaptados a los seis dígitos usados por Supabase. */
public class TotpTest {

    private static final String SECRETO_RFC =
            "GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ";

    @Test
    public void generaCodigosRfc6238DeSeisDigitos() {
        assertEquals("287082", Totp.codigo(SECRETO_RFC, 59L));
        assertEquals("081804", Totp.codigo(SECRETO_RFC, 1_111_111_109L));
        assertEquals("050471", Totp.codigo(SECRETO_RFC, 1_111_111_111L));
        assertEquals("005924", Totp.codigo(SECRETO_RFC, 1_234_567_890L));
        assertEquals("279037", Totp.codigo(SECRETO_RFC, 2_000_000_000L));
        assertEquals("353130", Totp.codigo(SECRETO_RFC, 20_000_000_000L));
    }

    @Test
    public void decodificaBase32SinImportarFormato() {
        assertArrayEquals("foo".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                Totp.decodificarBase32(" MZXW6=== "));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaCaracteresFueraDeBase32() {
        Totp.decodificarBase32("NO-VALIDO!");
    }
}
