// Pruebas de la lectura de expedientes firmados que se suben en lote.
// Los textos imitan la forma exacta de los documentos reales (Hoja de Trámite del
// SIGE, oficio y orden de sanción), pero con personas y números inventados: este
// repositorio es público y no debe contener datos de nadie.

import { test, describe } from "node:test";
import assert from "node:assert/strict";
import {
  esPaginaDeHojaDeTramite,
  separarExpedientes,
  numeroHojaTramite,
  numeroOficio,
  numerosDeNotaInformativa,
  codigoInfraccion,
  sancionImpuesta,
  infractor,
  leerExpediente,
  buscarFaltaDelExpediente,
} from "./expedienteFirmado.js";

const hojaDeTramite = (ht, oficio, sancion, grado, persona) => `
Hoja de Trámite
Sistema de Gestión de Expedientes - SIGE MIN
Ministerio del Interior
Nro Hoja de Trámite : ${ht}
Procedencia : INTERNO
Nro de documento : ${oficio}
Tipo de Documento : OFICIO
Oficina Registro : REGION POLICIAL CALLAO - DIVISION DE ORDEN PUBLICO Y SEGURIDAD
Asunto
REMITIRLE ADJUNTO AL PRESENTE, EN EJEMPLAR TRIPLICADO (03), LA DE ORDEN DE SANCIÓN CON SANCIÓN
IMPUESTA DE ${sancion}, ACTA DE RECEPCIÓN DE DESCARGO, DESCARGO Y NOTIFICACIÓN DE
IMPUTACIÓN DE INFRACCIÓN LEVE (L21), IMPUESTA POR EL MAYOR PNP ALVARO SOTO MENDEZ AL ${grado}
PNP ${persona}.`;

const ordenDeSancion = (nota, codigo, sancion, grado, persona) => `
CONFIDENCIAL
MINISTERIO DEL INTERIOR
POLICÍA NACIONAL DEL PERÚ
ORDEN DE SANCIÓN
GRADO Y NOMBRE DEL INFRACTOR : ${grado} PNP ${persona}.
UNIDAD/SUB-UNIDAD : DIVOPUS 3 - Comisaría PNP Ventanilla.
DESCRIPCION DE LA INFRACCIÓN Y MOTIVACION DE LA SANCION : "Llegar con retraso a su unidad".
Dicho accionar fue constatado dando cuenta mediante NOTA INFORMATIVA N° ${nota}-COMOPPOL-PNP/DIRNOS.
CÓDIGO DE LA INFRACCIÓN : ${codigo}
SANCIÓN IMPUESTA : ${sancion}
NOTIFICACION DEL INVESTIGADO`;

describe("separar un fajo escaneado en expedientes", () => {
  test("cada Hoja de Trámite abre un expediente y se queda con sus páginas", () => {
    const paginas = [
      hojaDeTramite("20260359833", "137-2026-REGPOLCALLAO-COMVA", "AMONESTACION", "S2", "PEREZ QUISPE Juan Carlos"),
      "OFICIO N°137-2026 ... Dios guarde a Ud.",
      ordenDeSancion("202600520525", "L21", "AMONESTACION", "S2", "PEREZ QUISPE, Juan Carlos"),
      "ACTA DE NO RECEPCIÓN DE DESCARGOS ...",
      "NOTIFICACIÓN DE IMPUTACIÓN DE INFRACCIÓN LEVE ...",
      hojaDeTramite("20260357678", "135-2026-REGPOLCALLAO-COMVA", "DOS (02) DÍAS SIMPLES", "S3", "RAMOS LEON Nixon Ivan"),
      "OFICIO N°135-2026 ...",
      ordenDeSancion("202600411111", "L21", "DOS (02) DÍAS SIMPLES", "S3", "RAMOS LEON, Nixon Ivan"),
    ];
    const bloques = separarExpedientes(paginas);
    assert.equal(bloques.length, 2);
    assert.deepEqual([bloques[0].desde, bloques[0].hasta], [1, 5]);
    assert.deepEqual([bloques[1].desde, bloques[1].hasta], [6, 8]);
  });

  test("una carátula suelta al inicio no se pierde: va con el primer expediente", () => {
    const bloques = separarExpedientes([
      "SANCIONES JULIO 2026",
      hojaDeTramite("20260359833", "137-2026-X", "AMONESTACION", "S2", "PEREZ QUISPE Juan Carlos"),
      "OFICIO ...",
    ]);
    assert.equal(bloques.length, 1);
    assert.deepEqual([bloques[0].desde, bloques[0].hasta], [1, 3]);
  });

  test("un PDF sin Hoja de Trámite se trata como un solo expediente", () => {
    const bloques = separarExpedientes(["algo", "otra cosa"]);
    assert.equal(bloques.length, 1);
    assert.deepEqual([bloques[0].desde, bloques[0].hasta], [1, 2]);
  });

  test("un PDF vacío no produce expedientes", () => {
    assert.deepEqual(separarExpedientes([]), []);
  });

  test("reconoce la página de Hoja de Trámite y descarta las demás", () => {
    assert.ok(esPaginaDeHojaDeTramite(hojaDeTramite("2026035", "1-2026-X", "AMONESTACION", "S2", "PEREZ QUISPE Juan")));
    assert.ok(!esPaginaDeHojaDeTramite("ORDEN DE SANCIÓN ... CÓDIGO DE LA INFRACCIÓN : L21"));
  });
});

describe("datos de cada expediente", () => {
  const texto = hojaDeTramite("20260359833", "137-2026-REGPOLCALLAO-DIVOPSVENTANILLA-COMVA",
    "AMONESTACION", "S2", "PEREZ QUISPE Juan Carlos")
    + ordenDeSancion("202600520525", "L21", "AMONESTACION", "S2", "PEREZ QUISPE, Juan Carlos");

  test("número de Hoja de Trámite", () => {
    assert.equal(numeroHojaTramite(texto), "20260359833");
    assert.equal(numeroHojaTramite("sin nada"), null);
  });

  test("número de oficio", () => {
    assert.equal(numeroOficio(texto), "137-2026-REGPOLCALLAO-DIVOPSVENTANILLA-COMVA");
  });

  test("lo saca también del propio oficio si no hay Hoja de Trámite", () => {
    assert.equal(numeroOficio("OFICIO N°137-2026-COMOPPOL-PNP/DIRNOS/REGPOL-CALL"),
      "137-2026-COMOPPOL-PNP/DIRNOS/REGPOL-CALL");
  });

  test("números de nota informativa, sin repetir y en orden", () => {
    const conDos = ordenDeSancion("202600520525", "L21", "AMONESTACION", "S2", "PEREZ QUISPE, Juan")
      + " reportado conforme a NOTA INFORMATIVA N° 202600525836-COMOPPOL-PNP.";
    assert.deepEqual(numerosDeNotaInformativa(conDos), ["202600520525", "202600525836"]);
  });

  test("código de infracción, del campo o del paréntesis del asunto", () => {
    assert.equal(codigoInfraccion(texto), "L21");
    assert.equal(codigoInfraccion("NOTIFICACIÓN DE IMPUTACIÓN DE INFRACCIÓN LEVE (L21)"), "L21");
    assert.equal(codigoInfraccion("no dice nada"), null);
  });

  test("amonestación: sin días", () => {
    const s = sancionImpuesta("SANCIÓN IMPUESTA : AMONESTACION");
    assert.equal(s.dias, 0);
    assert.equal(s.tipo, "amonestacion");
  });

  test("días simples escritos como DOS (02) DÍAS SIMPLES", () => {
    const s = sancionImpuesta("SANCIÓN IMPUESTA : DOS (02) DÍAS SIMPLES");
    assert.equal(s.dias, 2);
    assert.equal(s.tipo, "simple");
  });

  test("días de rigor escritos con cifra", () => {
    const s = sancionImpuesta("SANCIÓN IMPUESTA : 05 días de sanción de rigor");
    assert.equal(s.dias, 5);
    assert.equal(s.tipo, "rigor");
  });

  test("días escritos solo en letras", () => {
    const s = sancionImpuesta("se le impone tres días simples");
    assert.equal(s.dias, 3);
    assert.equal(s.tipo, "simple");
  });

  test("sin sanción legible no inventa un número", () => {
    const s = sancionImpuesta("ORDEN DE SANCIÓN");
    assert.equal(s.dias, null);
    assert.equal(s.texto, null);
  });

  test("infractor con coma, como lo escribe la orden de sanción", () => {
    assert.deepEqual(infractor("GRADO Y NOMBRE DEL INFRACTOR : S2 PNP PEREZ QUISPE, Juan Carlos. UNIDAD"),
      { grado: "S2", apellidos: "PEREZ QUISPE", nombres: "Juan Carlos" });
  });

  test("infractor sin coma, como lo escribe el asunto", () => {
    assert.deepEqual(infractor("IMPUESTA POR EL MAYOR PNP ALVARO SOTO MENDEZ AL S3 PNP RAMOS LEON Nixon Ivan."),
      { grado: "S3", apellidos: "RAMOS LEON", nombres: "Nixon Ivan" });
  });

  test("todo junto", () => {
    const datos = leerExpediente(texto);
    assert.equal(datos.numero_ht, "20260359833");
    assert.equal(datos.numero_nota_falta, "202600520525");
    assert.equal(datos.codigo_infraccion, "L21");
    assert.equal(datos.dias_sancion, 0);
    assert.equal(datos.infractor.apellidos, "PEREZ QUISPE");
  });
});

describe("a qué falta pertenece cada expediente", () => {
  const notas = [
    { id: "n1", numero_nota_falta: "202600520525", grado: "S2", apellidos: "PEREZ QUISPE", nombres: "Juan Carlos" },
    { id: "n2", numero_nota_falta: "202600411111", grado: "S3", apellidos: "RAMOS LEON", nombres: "Nixon Ivan" },
    { id: "n3", numero_nota_falta: "202600422222", grado: "S1", apellidos: "RAMOS LEON", nombres: "Otro Distinto" },
  ];

  test("por número de nota: es inequívoco", () => {
    const datos = leerExpediente(ordenDeSancion("202600520525", "L21", "AMONESTACION", "S2", "PEREZ QUISPE, Juan Carlos"));
    const hallado = buscarFaltaDelExpediente(datos, notas);
    assert.equal(hallado.nota.id, "n1");
    assert.equal(hallado.motivo, "numero_nota");
  });

  test("sin número legible, por apellidos y nombres", () => {
    const datos = leerExpediente("GRADO Y NOMBRE DEL INFRACTOR : S3 PNP RAMOS LEON, Nixon Ivan. UNIDAD");
    const hallado = buscarFaltaDelExpediente(datos, notas);
    assert.equal(hallado.nota.id, "n2");
    assert.equal(hallado.motivo, "nombre");
  });

  test("dos personas con los mismos apellidos y nombre ilegible: no elige, avisa", () => {
    const hallado = buscarFaltaDelExpediente(
      { numeros_nota: [], infractor: { grado: "S3", apellidos: "RAMOS LEON", nombres: "" } }, notas);
    assert.equal(hallado.nota, null);
    assert.equal(hallado.motivo, "varios");
    assert.equal(hallado.candidatas.length, 2);
  });

  test("si no encuentra nada, devuelve nulo en vez de adivinar", () => {
    const datos = leerExpediente(ordenDeSancion("209900000000", "L21", "AMONESTACION", "S2", "AJENO LEJANO, Persona"));
    assert.equal(buscarFaltaDelExpediente(datos, notas), null);
  });

  test("sin datos no revienta", () => {
    assert.equal(buscarFaltaDelExpediente(null, notas), null);
  });
});
