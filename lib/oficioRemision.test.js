// Pruebas del oficio de remisión (lib/oficioRemision.js). Corre con: node --test lib/

import { test, describe } from "node:test";
import assert from "node:assert/strict";
import {
  construirDatosOficioRemision, puedeGenerarOficioRemision, diasEnLetras,
  cargarFirmantes, guardarFirmantes, FIRMANTES_INICIALES,
} from "./oficioRemision.js";

const EFECTIVOS = [
  { cip: "400001", apellidos_nombres: "RIOS PAREDES, DIEGO MARTIN", grado: "TENIENTE PNP", dni: "11111111" },
];

const NOTA = {
  grado: "ST3",
  apellidos: "MENDOZA RAMOS",
  nombres: "CARLOS",
  codigo_infraccion: "L21",
  oficial_constato: "TNTE RIOS PAREDES Diego",
  orden_sancion_generada_at: "2026-09-20T12:00:00.000Z",
  orden_notificada_at: "2026-09-22T12:00:00.000Z",
  sancion_tipo: "dias",
  sancion_dias: 2,
};

const FIRMANTES = {
  ...FIRMANTES_INICIALES,
  jefe_nombre: "Nombre APELLIDO JEFE",
  firma_nombre: "Nombre APELLIDO COMISARIO",
  firma_oa: "OA-000000",
  iniciales: "AAA/bbb",
};

function almacenFalso() {
  const m = new Map();
  return { getItem: (k) => (m.has(k) ? m.get(k) : null), setItem: (k, v) => m.set(k, v) };
}

describe("oficio de remisión", () => {
  test("solo se puede generar con la orden ya notificada (expediente subido)", () => {
    assert.equal(puedeGenerarOficioRemision(NOTA, EFECTIVOS), true);
    assert.equal(puedeGenerarOficioRemision({ ...NOTA, orden_notificada_at: null }, EFECTIVOS), false);
    assert.equal(puedeGenerarOficioRemision({ ...NOTA, orden_sancion_generada_at: null }, EFECTIVOS), false);
  });

  test("los días de sanción salen en letras y número", () => {
    assert.equal(diasEnLetras(1), "UN (01) día");
    assert.equal(diasEnLetras(2), "DOS (02) días");
    assert.equal(diasEnLetras(10), "DIEZ (10) días");
  });

  test("arma los datos con el jefe y el comisario indicados", () => {
    const d = construirDatosOficioRemision(NOTA, EFECTIVOS, { numeroOficio: "176", firmantes: FIRMANTES });
    assert.equal(d.numero_oficio, "176");
    assert.equal(d.jefe_nombre, "Nombre APELLIDO JEFE");
    assert.equal(d.firma_cargo, "COMISARIO (E) PNP DE VENTANILLA");
    assert.equal(d.iniciales, "AAA/bbb");
    assert.equal(d.sancion_dias_texto, "DOS (02) días");
    assert.equal(d.codigo_infraccion, "L21");
    assert.equal(d.oficial_grado, "TENIENTE PNP");
    assert.equal(d.investigado_grado, "ST3 PNP");
    assert.match(d.fecha, /^Ventanilla, \d{1,2} de \w+ del \d{4}$/);
  });

  test("si cambiaron el jefe o el comisario, se usan los nuevos", () => {
    const d = construirDatosOficioRemision(NOTA, EFECTIVOS, {
      numeroOficio: "5",
      firmantes: { ...FIRMANTES, jefe_nombre: "Otro JEFE NUEVO", firma_nombre: "Nuevo COMISARIO X", firma_oa: "OA-111111" },
    });
    assert.equal(d.jefe_nombre, "Otro JEFE NUEVO");
    assert.equal(d.firma_nombre, "Nuevo COMISARIO X");
    assert.equal(d.firma_oa, "OA-111111");
  });

  test("amonestación no inventa días", () => {
    const d = construirDatosOficioRemision({ ...NOTA, sancion_tipo: "amonestacion", sancion_dias: null }, EFECTIVOS, { numeroOficio: "1", firmantes: FIRMANTES });
    assert.equal(d.sancion_dias_texto, "amonestación escrita");
  });

  test("sin número de oficio o sin nombre del comisario, avisa en vez de imprimir un vacío", () => {
    assert.throws(() => construirDatosOficioRemision(NOTA, EFECTIVOS, { numeroOficio: " ", firmantes: FIRMANTES }), /número del oficio/);
    assert.throws(() => construirDatosOficioRemision(NOTA, EFECTIVOS, {
      numeroOficio: "1", firmantes: { ...FIRMANTES, firma_nombre: "" },
    }), /Comisario/);
  });

  test("lo confirmado queda guardado como valor por defecto", () => {
    const almacen = almacenFalso();
    assert.equal(cargarFirmantes(almacen).jefe_nombre, "");
    guardarFirmantes({ ...FIRMANTES, jefe_nombre: "Nuevo JEFE" }, almacen);
    assert.equal(cargarFirmantes(almacen).jefe_nombre, "Nuevo JEFE");
    assert.equal(cargarFirmantes(almacen).firma_nombre, FIRMANTES.firma_nombre);
  });
});
