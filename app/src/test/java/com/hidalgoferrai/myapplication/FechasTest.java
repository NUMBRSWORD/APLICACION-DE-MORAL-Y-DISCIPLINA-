package com.hidalgoferrai.myapplication;

import org.junit.Test;
import java.util.TimeZone;
import static org.junit.Assert.assertEquals;

public class FechasTest {
    @Test public void instanteNoAdelantaDiaDeLima() {
        assertEquals("19/09/2026", Fechas.lima("2026-09-20T01:30:00Z"));
        assertEquals("20/09/2026", Fechas.lima("2026-09-20T05:00:00.123456+00:00"));
        assertEquals("19/09/2026", Fechas.lima("2026-09-19T23:59:59.1-05:00"));
        assertEquals("19/09/2026", Fechas.lima("2026-09-19T23:59:59.12-05:00"));
    }
    @Test public void fechaCivilNoSeConvierteDesdeUtc() {
        assertEquals("20/09/2026", Fechas.lima("2026-09-20"));
    }
    @Test public void independienteDelTelefono() {
        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tokyo"));
            assertEquals("19/09/2026", Fechas.lima("2026-09-20T01:30:00Z"));
        } finally { TimeZone.setDefault(original); }
    }
    @Test public void noInventaFechasInvalidas() {
        assertEquals("2026-02-30", Fechas.lima("2026-02-30"));
        assertEquals("sin registro", Fechas.lima("sin registro"));
        assertEquals("—", Fechas.lima(null));
    }
}
