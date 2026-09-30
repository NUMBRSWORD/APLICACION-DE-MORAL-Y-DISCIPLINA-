// Lectura de expedientes YA FIRMADOS que se suben en lote.
//
// El comando escanea un fajo de expedientes terminados y sube un solo PDF con
// todos seguidos. Cada expediente empieza por su Hoja de Trámite del SIGE y
// ocupa varias páginas (oficio, orden de sanción, acta y notificación).
//
// Aquí vive la parte que se puede probar sin red: partir el documento por
// expedientes, sacar sus datos del texto y decidir a qué falta pertenece cada
// uno. La IA se usa después, y solo para lo que estas reglas no logren leer.

/** Una página que abre expediente: la Hoja de Trámite del SIGE. */
export function esPaginaDeHojaDeTramite(texto = "") {
  const t = texto.replace(/\s+/g, " ");
  return /Hoja\s+de\s+Tr[aá]mite/i.test(t) && /Nro\s*Hoja\s*de\s*Tr[aá]mite/i.test(t);
}

/**
 * Agrupa las páginas en expedientes. Devuelve [{ desde, hasta, texto }] con los
 * números de página en base 1, ambos incluidos, tal como los necesita el recorte.
 *
 * Si el fajo no empieza por una Hoja de Trámite (por ejemplo, alguien escaneó
 * primero una carátula), esas páginas iniciales se quedan con el primer
 * expediente en vez de perderse.
 */
export function separarExpedientes(textosPorPagina = []) {
  const inicios = [];
  textosPorPagina.forEach((texto, i) => {
    if (esPaginaDeHojaDeTramite(texto)) inicios.push(i);
  });
  if (!textosPorPagina.length) return [];
  if (!inicios.length) {
    return [{ desde: 1, hasta: textosPorPagina.length, texto: textosPorPagina.join("\n") }];
  }
  return inicios.map((inicio, i) => {
    // Lo que venga antes de la primera Hoja de Trámite (una carátula, una hoja
    // suelta) se queda con el primer expediente en vez de perderse o formar uno
    // propio que no existe.
    const desde = i === 0 ? 0 : inicio;
    const fin = i + 1 < inicios.length ? inicios[i + 1] - 1 : textosPorPagina.length - 1;
    return {
      desde: desde + 1,
      hasta: fin + 1,
      texto: textosPorPagina.slice(desde, fin + 1).join("\n"),
    };
  });
}

const limpiar = (texto = "") => texto.replace(/\s+/g, " ").trim();

/** Número de Hoja de Trámite del SIGE (11 dígitos en los documentos reales). */
export function numeroHojaTramite(texto = "") {
  const m = limpiar(texto).match(/Nro\s*Hoja\s*de\s*Tr[aá]mite\s*[:\-]?\s*(\d{6,})/i);
  return m ? m[1] : null;
}

/** Número del oficio que remite la sanción. */
export function numeroOficio(texto = "") {
  const t = limpiar(texto);
  const enHoja = t.match(/Nro\s*de\s*documento\s*[:\-]?\s*([0-9]+-[0-9]{4}-[A-ZÁÉÍÓÚÑ0-9/.\-]+)/i);
  if (enHoja) return enHoja[1].replace(/[.,;]$/, "");
  const enOficio = t.match(/OFICIO\s*N[°º]?\s*([0-9]+-[0-9]{4}-[A-ZÁÉÍÓÚÑ0-9/."\-]+)/i);
  return enOficio ? enOficio[1].replace(/[.,;]$/, "") : null;
}

/**
 * Número de la Nota Informativa de la falta: es la llave para encontrar el caso.
 * La orden de sanción puede citar dos (la de la falta y la del reporte posterior);
 * se devuelven todas las encontradas, en el orden en que aparecen.
 */
export function numerosDeNotaInformativa(texto = "") {
  const t = limpiar(texto);
  const encontrados = [];
  const re = /NOTA\s+INFORMATIVA\s*N[°º]?\s*(\d{8,})/gi;
  let m;
  while ((m = re.exec(t)) !== null) {
    if (!encontrados.includes(m[1])) encontrados.push(m[1]);
  }
  return encontrados;
}

/** Código del Anexo I (L1 a L117). */
export function codigoInfraccion(texto = "") {
  const t = limpiar(texto);
  const explicito = t.match(/C[ÓO]DIGO\s+DE\s+LA\s+INFRACCI[ÓO]N\s*[:\-]?\s*\(?\s*(L\s?\d{1,3})/i);
  if (explicito) return explicito[1].replace(/\s+/g, "").toUpperCase();
  const entreParentesis = t.match(/infracci[oó]n\s+leve\s*\(\s*(L\s?\d{1,3})\s*\)/i);
  return entreParentesis ? entreParentesis[1].replace(/\s+/g, "").toUpperCase() : null;
}

const NUMEROS_EN_LETRA = {
  un: 1, uno: 1, una: 1, dos: 2, tres: 3, cuatro: 4, cinco: 5, seis: 6, siete: 7,
  ocho: 8, nueve: 9, diez: 10, once: 11, doce: 12, trece: 13, catorce: 14, quince: 15,
};

/**
 * Sanción impuesta. La amonestación no tiene días; el resto se expresa como
 * "DOS (02) DÍAS SIMPLES" o "05 días de sanción de rigor".
 */
export function sancionImpuesta(texto = "") {
  const t = limpiar(texto);
  if (/AMONESTACI[ÓO]N/i.test(t)) return { texto: "AMONESTACION", dias: 0, tipo: "amonestacion" };
  const conParentesis = t.match(/([A-Za-zÁÉÍÓÚÑáéíóúñ]+)\s*\(\s*(\d{1,3})\s*\)\s*D[ÍI]AS?\s+(SIMPLES?|DE\s+RIGOR|RIGOR)/i);
  if (conParentesis) {
    return {
      texto: limpiar(conParentesis[0]).toUpperCase(),
      dias: Number(conParentesis[2]),
      tipo: /RIGOR/i.test(conParentesis[3]) ? "rigor" : "simple",
    };
  }
  const soloNumero = t.match(/(\d{1,3})\s*D[ÍI]AS?\s+(?:DE\s+SANCI[ÓO]N\s+)?(SIMPLES?|DE\s+RIGOR|RIGOR)/i);
  if (soloNumero) {
    return {
      texto: limpiar(soloNumero[0]).toUpperCase(),
      dias: Number(soloNumero[1]),
      tipo: /RIGOR/i.test(soloNumero[2]) ? "rigor" : "simple",
    };
  }
  const enLetra = t.match(/\b(un|uno|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|trece|catorce|quince)\s+d[íi]as?\s+(simples?|de\s+rigor|rigor)/i);
  if (enLetra) {
    return {
      texto: limpiar(enLetra[0]).toUpperCase(),
      dias: NUMEROS_EN_LETRA[enLetra[1].toLowerCase()] ?? null,
      tipo: /rigor/i.test(enLetra[2]) ? "rigor" : "simple",
    };
  }
  return { texto: null, dias: null, tipo: null };
}

const GRADOS = "S1|S2|S3|SB|SS|ST1|ST2|ST3|SOB|SOS|TNTE|CAP|MAY|CMDTE|CRNL|ALF|SO";

/**
 * Grado, apellidos y nombres del sancionado. El documento lo dice en dos sitios
 * con la misma forma: en el asunto de la Hoja de Trámite ("...AL S2 PNP APELLIDOS
 * Nombres") y en la orden de sanción ("GRADO Y NOMBRE DEL INFRACTOR : ...").
 */
export function infractor(texto = "") {
  const t = limpiar(texto);
  const enOrden = t.match(
    new RegExp(`GRADO\\s+Y\\s+NOMBRE\\s+DEL\\s+INFRACTOR\\s*[:\\-]?\\s*(${GRADOS})\\s*\\.?\\s*PNP\\s+([^:]+?)\\s*(?:\\.|UNIDAD|$)`, "i"));
  if (enOrden) return normalizarPersona(enOrden[1], enOrden[2]);
  const enAsunto = t.match(
    new RegExp(`\\bAL\\s+(${GRADOS})\\s*\\.?\\s*PNP\\s+([A-ZÁÉÍÓÚÑ][^.]{3,80}?)\\s*(?:\\.|$)`, "i"));
  if (enAsunto) return normalizarPersona(enAsunto[1], enAsunto[2]);
  return null;
}

/**
 * Separa "QUIÑONEZ CAMPOS, Jhonatan Smith" en apellidos y nombres. Sin coma se
 * toman los dos primeros como apellidos, que es la forma en que los escriben.
 */
function normalizarPersona(grado, resto) {
  const completo = limpiar(resto).replace(/[.,;]$/, "");
  const conComa = completo.split(",");
  if (conComa.length >= 2) {
    return {
      grado: grado.toUpperCase(),
      apellidos: limpiar(conComa[0]).toUpperCase(),
      nombres: limpiar(conComa.slice(1).join(" ")),
    };
  }
  const partes = completo.split(" ").filter(Boolean);
  return {
    grado: grado.toUpperCase(),
    apellidos: partes.slice(0, 2).join(" ").toUpperCase(),
    nombres: partes.slice(2).join(" "),
  };
}

/**
 * Un expediente terminado lleva: la imputación y su notificación, el descargo o
 * el acta de no recepción, y la orden de sanción con su notificación —o, si no
 * hubo sanción, la resolución de archivo con la suya.
 *
 * Se reconoce qué piezas trae para poder avisar en la revisión cuando falta
 * alguna, en vez de archivar en silencio un expediente a medio hacer.
 */
export function piezasDelExpediente(texto = "") {
  const t = limpiar(texto);
  return {
    imputacion: /NOTIFICACI[ÓO]N\s+DE\s+IMPUTACI[ÓO]N|IMPUTACI[ÓO]N\s+DE\s+INFRACCI[ÓO]N\s+LEVE/i.test(t),
    acta_no_descargo: /ACTA\s+DE\s+NO\s+RECEPCI[ÓO]N\s+DE\s+DESCARGOS?/i.test(t),
    descargo: /ACTA\s+DE\s+RECEPCI[ÓO]N\s+DE\s+DESCARGOS?|PRESENT[ÓO]\s+SU\s+DESCARGO|ESCRITO\s+DE\s+DESCARGO/i.test(t),
    orden_sancion: /ORDEN\s+DE\s+SANCI[ÓO]N/i.test(t),
    archivo: /RESOLUCI[ÓO]N\s+DE\s+ARCHIVO|SE\s+RESUELVE\s*:?\s*ARCHIVAR|ARCHIVO\s+DEL\s+PROCEDIMIENTO/i.test(t),
    notificacion_firmada: /NOTIFICACI[ÓO]N\s+DEL\s+INVESTIGADO/i.test(t),
  };
}

/**
 * En qué terminó: con sanción impuesta o archivado. "incompleto" significa que
 * el documento no trae ninguna de las dos cosas y no debe darse por terminado.
 */
export function resultadoDelExpediente(texto = "") {
  const piezas = piezasDelExpediente(texto);
  if (piezas.archivo && !piezas.orden_sancion) return "archivo";
  if (piezas.orden_sancion) return "sancion";
  return "incompleto";
}

/** Piezas que deberían estar y no aparecen; se muestran en la revisión. */
export function piezasQueFaltan(texto = "") {
  const piezas = piezasDelExpediente(texto);
  const faltan = [];
  if (!piezas.imputacion) faltan.push("la notificación de imputación");
  if (!piezas.acta_no_descargo && !piezas.descargo) faltan.push("el descargo o el acta de no recepción");
  if (!piezas.orden_sancion && !piezas.archivo) faltan.push("la orden de sanción o la resolución de archivo");
  if (!piezas.notificacion_firmada) faltan.push("la notificación firmada por el investigado");
  return faltan;
}

/** Todo lo que se puede leer de un expediente sin preguntar a la IA. */
export function leerExpediente(texto = "") {
  const notas = numerosDeNotaInformativa(texto);
  const sancion = sancionImpuesta(texto);
  return {
    resultado: resultadoDelExpediente(texto),
    piezas: piezasDelExpediente(texto),
    faltan: piezasQueFaltan(texto),
    numero_ht: numeroHojaTramite(texto),
    numero_oficio: numeroOficio(texto),
    numeros_nota: notas,
    numero_nota_falta: notas[0] || null,
    codigo_infraccion: codigoInfraccion(texto),
    sancion_texto: sancion.texto,
    dias_sancion: sancion.dias,
    tipo_sancion: sancion.tipo,
    infractor: infractor(texto),
  };
}

const sinTildes = (texto = "") => texto.normalize("NFD").replace(/[̀-ͯ]/g, "").toUpperCase().trim();

/**
 * Busca a qué falta registrada pertenece el expediente. Primero por el número de
 * nota, que es inequívoco; si no cuadra, por apellidos y nombres.
 *
 * Devuelve { nota, motivo } o null. El motivo se muestra en la revisión para que
 * quien confirma sepa por qué se propuso ese caso.
 */
export function buscarFaltaDelExpediente(datos, notas = []) {
  if (!datos) return null;
  for (const numero of datos.numeros_nota || []) {
    const porNota = notas.find((n) => String(n.numero_nota_falta || "").trim() === numero);
    if (porNota) return { nota: porNota, motivo: "numero_nota", numero };
  }
  const persona = datos.infractor;
  if (!persona) return null;
  const apellidos = sinTildes(persona.apellidos);
  const nombres = sinTildes(persona.nombres);
  if (!apellidos) return null;
  const coinciden = notas.filter((n) => {
    if (sinTildes(n.apellidos) !== apellidos) return false;
    if (!nombres) return true;
    const suyos = sinTildes(n.nombres);
    return suyos.startsWith(nombres.split(" ")[0]) || nombres.startsWith(suyos.split(" ")[0]);
  });
  if (coinciden.length === 1) return { nota: coinciden[0], motivo: "nombre" };
  if (coinciden.length > 1) return { nota: null, motivo: "varios", candidatas: coinciden };
  return null;
}
