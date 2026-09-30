// Pruebas de la etapa de remisión: oficio y Hoja de Trámite, que se hacen
// después de subir el expediente firmado.

import { test, describe } from "node:test";
import assert from "node:assert/strict";
import {
  expedienteFirmadoRecibido,
  remisionDelCaso,
  estadoDeRemision,
  esperaOficio,
  esperaHojaDeTramite,
  casosQueEsperanOficio,
  discrepanciaDeDias,
} from "./remision.js";

const enTramite = { id: "c1" };
const conExpediente = { id: "c2", archivo_orden_notificacion_path: "c2/expediente_firmado_1.pdf" };
const otroConExpediente = { id: "c3", archivo_orden_notificacion_path: "c3/expediente_firmado_1.pdf" };

describe("en qué punto de la remisión está el caso", () => {
  test("sin el legajo firmado no hay nada que remitir", () => {
    assert.equal(estadoDeRemision(enTramite, []), "sin_expediente");
    assert.equal(expedienteFirmadoRecibido(enTramite), false);
    assert.equal(esperaOficio(enTramite, []), false);
  });

  test("con el legajo en mano y sin remisión registrada, falta el oficio", () => {
    assert.equal(estadoDeRemision(conExpediente, []), "falta_oficio");
    assert.equal(esperaOficio(conExpediente, []), true);
  });

  test("remitido pero sin el oficio adjunto, sigue faltando el oficio", () => {
    const remitidos = [{ nota_id: "c2", archivo_path: "x.pdf" }];
    assert.equal(estadoDeRemision(conExpediente, remitidos), "falta_oficio");
  });

  test("con el oficio y sin la HT, falta la HT de DIVOPUS", () => {
    const remitidos = [{ nota_id: "c2", archivo_oficio_path: "of.pdf" }];
    assert.equal(estadoDeRemision(conExpediente, remitidos), "falta_ht");
    assert.equal(esperaHojaDeTramite(conExpediente, remitidos), true);
    assert.equal(esperaOficio(conExpediente, remitidos), false);
  });

  test("con los dos adjuntos, no queda nada", () => {
    const remitidos = [{ nota_id: "c2", archivo_oficio_path: "of.pdf", archivo_ht_path: "ht.pdf" }];
    assert.equal(estadoDeRemision(conExpediente, remitidos), "remitido");
  });

  test("la remisión de otro caso no cuenta como la propia", () => {
    const remitidos = [{ nota_id: "c3", archivo_oficio_path: "of.pdf", archivo_ht_path: "ht.pdf" }];
    assert.equal(remisionDelCaso(conExpediente, remitidos), null);
    assert.equal(estadoDeRemision(conExpediente, remitidos), "falta_oficio");
  });

  test("sin datos no revienta", () => {
    assert.equal(estadoDeRemision(null, []), "sin_expediente");
    assert.equal(remisionDelCaso(null, []), null);
    assert.equal(estadoDeRemision(conExpediente, undefined), "falta_oficio");
  });
});

describe("la lista con la que se hacen los oficios", () => {
  test("solo los que tienen el legajo y no tienen oficio", () => {
    const remitidos = [{ nota_id: "c3", archivo_oficio_path: "of.pdf" }];
    const lista = casosQueEsperanOficio([enTramite, conExpediente, otroConExpediente], remitidos);
    assert.deepEqual(lista.map((n) => n.id), ["c2"]);
  });

  test("sin casos, lista vacía", () => {
    assert.deepEqual(casosQueEsperanOficio([], []), []);
  });
});

describe("los días del papel contra los del sistema", () => {
  test("avisa cuando no cuadran", () => {
    assert.deepEqual(discrepanciaDeDias(8, { sancion_dias: 4 }), { leidos: 8, registrados: 4 });
  });

  test("callado cuando cuadran", () => {
    assert.equal(discrepanciaDeDias(4, { sancion_dias: 4 }), null);
  });

  test("no inventa comparación si falta alguno de los dos", () => {
    assert.equal(discrepanciaDeDias(null, { sancion_dias: 4 }), null);
    assert.equal(discrepanciaDeDias(4, { sancion_dias: null }), null);
    assert.equal(discrepanciaDeDias(4, null), null);
  });

  test("la amonestación son cero días, no 'sin dato'", () => {
    assert.deepEqual(discrepanciaDeDias(0, { sancion_dias: 3 }), { leidos: 0, registrados: 3 });
    assert.equal(discrepanciaDeDias(0, { sancion_dias: 0 }), null);
  });
});
