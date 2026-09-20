package com.hidalgoferrai.myapplication;

import org.json.JSONObject;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

final class ExpedientePendiente {
    enum Estado { COMPLETADO, SIGUIENTE, PENDIENTE, SIN_REGISTRO, POR_REVISAR }

    static final class Paso {
        final int titulo;
        final Estado estado;
        final String fecha;
        Paso(int titulo, Estado estado, String fecha) {
            this.titulo = titulo; this.estado = estado; this.fecha = fecha;
        }
    }

    /** Se marca solo lo respaldado por datos; un paso posterior no completa los anteriores. */
    static List<Paso> pasos(JSONObject nota) {
        List<Paso> pasos = new ArrayList<>();
        agregar(pasos, R.string.paso_falta, nota, "fecha_falta");
        if (tiene(nota, "archivo_leve_generada_at")) {
            agregar(pasos, R.string.paso_archivo, nota, "archivo_leve_generada_at");
            return pasos;
        }
        String codigo = tiene(nota,"codigo_infraccion") ? nota.optString("codigo_infraccion").trim() : "";
        if (codigo.isEmpty()) {
            agregar(pasos, R.string.paso_clasificar, nota, "codigo_infraccion");
            return pasos;
        }
        if (!codigo.toUpperCase(Locale.ROOT).startsWith("L")) {
            // La API actual no registra aquí la generación del informe: no inventar un ✓.
            pasos.add(new Paso(R.string.paso_informe, Estado.POR_REVISAR, ""));
            return pasos;
        }
        agregar(pasos, R.string.paso_reincorporacion, nota, "fecha_reincorporacion");
        agregar(pasos, R.string.paso_imputacion, nota, "imputacion_generada_at");
        if (!tiene(nota,"fecha_descargo") && tiene(nota,"orden_sancion_generada_at"))
            pasos.add(new Paso(R.string.paso_descargo, Estado.SIN_REGISTRO, ""));
        else agregar(pasos, R.string.paso_descargo, nota, "fecha_descargo");
        agregar(pasos, R.string.paso_orden, nota, "orden_sancion_generada_at");
        agregar(pasos, R.string.paso_notificacion, nota, "orden_notificada_at");
        return pasos;
    }

    private static void agregar(List<Paso> pasos, int titulo, JSONObject nota, String campo) {
        boolean hecho = tiene(nota, campo);
        boolean siguiente = !hecho;
        for (Paso p : pasos) if (p.estado == Estado.SIGUIENTE) siguiente = false;
        pasos.add(new Paso(titulo, hecho ? Estado.COMPLETADO : siguiente ? Estado.SIGUIENTE : Estado.PENDIENTE,
                hecho ? nota.optString(campo) : ""));
    }
    static boolean tiene(JSONObject nota, String clave) {
        return !nota.isNull(clave) && !nota.optString(clave).trim().isEmpty();
    }
    static boolean pendiente(JSONObject nota) {
        return !tiene(nota, "orden_notificada_at") && !tiene(nota, "archivo_leve_generada_at");
    }
    static int siguiente(JSONObject n) {
        String codigo = tiene(n,"codigo_infraccion") ? n.optString("codigo_infraccion").trim().toUpperCase(Locale.ROOT) : "";
        if (codigo.isEmpty()) return R.string.pendiente_datos;
        if (!codigo.startsWith("L")) return R.string.pendiente_informe;
        if (!tiene(n, "fecha_reincorporacion")) return R.string.pendiente_reincorporacion;
        if (!tiene(n, "imputacion_generada_at")) return R.string.pendiente_imputacion;
        if (tiene(n, "orden_sancion_generada_at")) return R.string.pendiente_notificar;
        if (!tiene(n, "fecha_descargo")) return R.string.pendiente_descargo;
        return R.string.pendiente_orden;
    }
    static String nombre(JSONObject n) {
        return (n.optString("grado", "") + " " + n.optString("apellidos", "")
                + " " + n.optString("nombres", "")).trim();
    }
}
