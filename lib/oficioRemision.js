// Oficio con el que la comisaría remite al Jefe de la DIVOPUS el legajo de una
// infracción leve sancionada: Orden de Sanción, Notificación y entrega del acto
// administrativo, Acta de no recepción de descargos e Inicio de imputación.
//
// Se emite DESPUÉS de subido el expediente firmado (orden notificada): es el
// paso "falta el oficio". Usa docxtemplater sobre plantillas/plantilla_oficio_remision.docx.
//
// Quién recibe (Jefe de la DIVOPUS) y quién firma (Comisario) cambian con los
// relevos, así que no van fijos en la plantilla: el usuario confirma en pantalla
// si continúan o cambiaron, y lo confirmado queda como valor por defecto de
// ese navegador (localStorage), no en la base de datos.

import { hoyLima } from "./fechas.js";
import { cargarDocxDeps } from "./docxDeps.js";
import { conPnp, fechaLarga, buscarOficialConstato } from "./imputacion.js";
import { nombreCompletoVisible, nombreParaSello } from "./utils.js";
import { normalizarCodigoInfraccion } from "./anexoI.js";

const CLAVE_FIRMANTES = "oficio_remision_firmantes";

// Valores de partida. Sin nombres ni CIP reales: el repositorio es público. Quien
// usa la app los escribe una vez en la pantalla del oficio y quedan guardados en
// su navegador; en las pruebas se pasan explícitos.
export const FIRMANTES_INICIALES = {
  jefe_grado: "Coronel PNP.",
  jefe_nombre: "",
  jefe_cargo: "JEFE DE LA DIVOPUS 03 VENTANILLA",
  unidad: "DIVOPUS 03 VENTANILLA",
  firma_grado: "MAYOR PNP",
  firma_nombre: "",
  firma_oa: "",
  firma_cargo: "COMISARIO (E) PNP DE VENTANILLA",
  iniciales: "",
};

export function cargarFirmantes(almacen = globalThis.localStorage) {
  try {
    const guardado = JSON.parse(almacen?.getItem(CLAVE_FIRMANTES) || "null");
    if (guardado && typeof guardado === "object") return { ...FIRMANTES_INICIALES, ...guardado };
  } catch { /* sin almacenamiento o JSON dañado: se usan los iniciales */ }
  return { ...FIRMANTES_INICIALES };
}

export function guardarFirmantes(firmantes, almacen = globalThis.localStorage) {
  try { almacen?.setItem(CLAVE_FIRMANTES, JSON.stringify(firmantes)); } catch { /* opcional */ }
}

const UNIDADES = ["", "UN", "DOS", "TRES", "CUATRO", "CINCO", "SEIS", "SIETE", "OCHO", "NUEVE", "DIEZ",
  "ONCE", "DOCE", "TRECE", "CATORCE", "QUINCE", "DIECISÉIS", "DIECISIETE", "DIECIOCHO", "DIECINUEVE", "VEINTE",
  "VEINTIÚN", "VEINTIDÓS", "VEINTITRÉS", "VEINTICUATRO", "VEINTICINCO", "VEINTISÉIS", "VEINTISIETE", "VEINTIOCHO",
  "VEINTINUEVE", "TREINTA"];

export function diasEnLetras(n) {
  const dias = Number(n);
  if (!Number.isInteger(dias) || dias < 1 || dias > 30) return `${n} días`;
  return `${UNIDADES[dias]} (${String(dias).padStart(2, "0")}) ${dias === 1 ? "día" : "días"}`;
}

function splitApellidosNombres(full) {
  const txt = (full || "").replace(/\s*\([^)]*\)\s*$/, "").trim();
  if (txt.includes(",")) {
    const [ap, no] = txt.split(",");
    return { apellidos: ap.trim(), nombres: (no || "").trim() };
  }
  const words = txt.split(/\s+/).filter(Boolean);
  if (words.length <= 2) return { apellidos: txt, nombres: "" };
  return { apellidos: words.slice(0, 2).join(" "), nombres: words.slice(2).join(" ") };
}

// Solo tras subir el expediente firmado (orden notificada), y para leves con
// sanción. El resto de datos se completa en pantalla.
export function puedeGenerarOficioRemision(nota, efectivos) {
  if (!normalizarCodigoInfraccion(nota.codigo_infraccion)) return false;
  if (!nota.orden_sancion_generada_at || !nota.orden_notificada_at) return false;
  if (nota.sancion_tipo !== "amonestacion" && !nota.sancion_dias) return false;
  return !!buscarOficialConstato(nota.oficial_constato, efectivos);
}

// seleccion: { numeroOficio, firmantes }
export function construirDatosOficioRemision(nota, efectivos, seleccion) {
  const numero = String(seleccion?.numeroOficio || "").trim();
  if (!numero) throw new Error("Escriba el número del oficio.");
  const f = { ...FIRMANTES_INICIALES, ...(seleccion?.firmantes || {}) };
  for (const [campo, etiqueta] of [["jefe_nombre", "el nombre del Jefe de la DIVOPUS"], ["firma_nombre", "el nombre del Comisario"], ["firma_oa", "el OA del Comisario"]]) {
    if (!String(f[campo] || "").trim()) throw new Error(`Falta ${etiqueta}.`);
  }
  const superior = buscarOficialConstato(nota.oficial_constato, efectivos);
  if (!superior) {
    throw new Error(`No se pudo ubicar en Efectivos al oficial "${nota.oficial_constato || "(no registrado)"}" que impuso la sanción.`);
  }
  const codigo = normalizarCodigoInfraccion(nota.codigo_infraccion);
  const hoy = hoyLima();
  const sup = splitApellidosNombres(superior.apellidos_nombres);

  return {
    fecha: `Ventanilla, ${fechaLarga(hoy)}`,
    numero_oficio: numero,
    anio: hoy.slice(0, 4),
    jefe_grado: f.jefe_grado,
    jefe_nombre: f.jefe_nombre,
    jefe_cargo: f.jefe_cargo,
    unidad: f.unidad,
    sancion_dias_texto: nota.sancion_tipo === "amonestacion" ? "amonestación escrita" : diasEnLetras(nota.sancion_dias),
    codigo_infraccion: codigo,
    oficial_grado: conPnp(superior.grado).toUpperCase(),
    oficial_nombre: nombreParaSello(sup.apellidos, sup.nombres),
    investigado_grado: conPnp(nota.grado),
    investigado_nombre: nombreCompletoVisible(nota.apellidos, nota.nombres),
    firma_grado: f.firma_grado,
    firma_nombre: f.firma_nombre,
    firma_oa: f.firma_oa,
    firma_cargo: f.firma_cargo,
    iniciales: f.iniciales,
  };
}

export async function renderizarOficioRemisionDocx(nota, efectivos, seleccion) {
  const data = construirDatosOficioRemision(nota, efectivos, seleccion);
  const { PizZip, Docxtemplater } = await cargarDocxDeps();

  const response = await fetch(new URL("../plantillas/plantilla_oficio_remision.docx", import.meta.url));
  if (!response.ok) throw new Error("No se pudo cargar la plantilla del oficio.");
  const zip = new PizZip(await response.arrayBuffer());
  const doc = new Docxtemplater(zip, {
    paragraphLoop: true,
    linebreaks: true,
    delimiters: { start: "{", end: "}" },
  });
  doc.render(data);

  return doc.getZip().generate({
    type: "blob",
    mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
  });
}
