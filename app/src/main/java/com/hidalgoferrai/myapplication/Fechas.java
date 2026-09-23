package com.hidalgoferrai.myapplication;

import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Fechas civiles sin desplazamiento; instantes del servidor en America/Lima. */
final class Fechas {
    private Fechas() { }

    static String lima(String valor) {
        if (valor == null || valor.isEmpty()) return "—";
        boolean civil = valor.matches("\\d{4}-\\d{2}-\\d{2}");
        String normal = valor;
        String patron = "yyyy-MM-dd";
        if (!civil) {
            // PostgreSQL puede enviar microsegundos: para mostrar el día bastan milisegundos.
            if (!valor.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?(Z|[+-]\\d{2}:\\d{2})")) return valor;
            normal = valor.replaceFirst("(\\.\\d{3})\\d+", "$1");
            normal = normal.replaceFirst("\\.(\\d)(Z|[+-])", ".$100$2")
                    .replaceFirst("\\.(\\d{2})(Z|[+-])", ".$10$2");
            patron = normal.contains(".") ? "yyyy-MM-dd'T'HH:mm:ss.SSSXXX" : "yyyy-MM-dd'T'HH:mm:ssXXX";
        }
        SimpleDateFormat entrada = new SimpleDateFormat(patron, Locale.ROOT);
        entrada.setLenient(false);
        entrada.setTimeZone(TimeZone.getTimeZone("America/Lima"));
        ParsePosition posicion = new ParsePosition(0);
        Date fecha = entrada.parse(normal, posicion);
        if (fecha == null || posicion.getIndex() != normal.length()) return valor;
        SimpleDateFormat salida = new SimpleDateFormat("dd/MM/yyyy", Locale.ROOT);
        salida.setTimeZone(TimeZone.getTimeZone("America/Lima"));
        return salida.format(fecha);
    }
}
