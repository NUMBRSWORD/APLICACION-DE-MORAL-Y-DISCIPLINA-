// Carga en lote de expedientes firmados: de los PDF leídos a las filas que se
// muestran para revisar antes de guardar.
//
// Aquí no se toca la red ni el navegador. Se recibe el texto ya extraído de
// cada PDF (una entrada por página) y se devuelve, por cada expediente
// encontrado, a qué caso se propone archivarlo, con qué páginas hay que
// recortarlo y qué hay que mirar antes de confirmar.
//
// La regla de fondo: nada se guarda solo. Un expediente firmado archivado en
// el caso equivocado no se descubre hasta mucho después, así que ante la duda
// la fila se detiene y la resuelve quien revisa.

import {
  esPaginaDeInicioDeExpediente,
  separarExpedientes,
  leerExpediente,
  buscarFaltaDelExpediente,
} from "./expedienteFirmado.js";

/** Quita de un nombre de archivo lo que el depósito no admite. */
export function nombreSeguro(texto, respaldo = "expediente") {
  const limpio = String(texto || "")
    .normalize("NFD").replace(/[̀-ͯ]/g, "")
    .replace(/[\\/:*?"<>|]+/g, "-")
    .replace(/\s+/g, " ")
    .trim();
  return (limpio || respaldo).slice(0, 90);
}

/** Nombre con el que se guarda el PDF ya recortado. */
export function nombreDelRecorte(datos, nota) {
  const persona = nota
    ? `${nota.apellidos || ""} ${nota.nombres || ""}`.trim()
    : (datos?.infractor?.completo || "");
  const numero = datos?.numero_nota_falta || nota?.numero_nota_falta || "";
  const partes = ["Expediente firmado", persona, numero && `N ${numero}`].filter(Boolean);
  return `${nombreSeguro(partes.join(" - "))}.pdf`;
}

// Por qué se propuso ese caso. Se muestra siempre, para que quien confirma
// sepa de dónde sale la propuesta; solo los que no son el número de nota
// cuentan además como aviso, porque son los que hay que comprobar.
const MOTIVOS = {
  numero_nota: "Encontrado por el número de nota informativa.",
  cip: "Encontrado por el CIP del investigado, no por el número de nota.",
  cip_y_codigo: "Encontrado por el CIP y el código de infracción, no por el número de nota.",
  nombre: "Encontrado solo por el nombre: compruébelo antes de guardar.",
  nombre_y_codigo: "Encontrado por el nombre y el código: compruébelo antes de guardar.",
};

/**
 * Revisa un expediente ya leído y decide en qué estado queda su fila.
 *
 * - "listo": se encontró el caso por el número de nota y no falta ninguna pieza.
 * - "revisar": se puede guardar, pero hay algo que mirar (se encontró por CIP o
 *   por nombre, falta una pieza, o el caso ya tenía un expediente).
 * - "detenido": no se puede guardar tal cual (sin caso, varios posibles, o el
 *   expediente no está terminado).
 */
export function revisarFila(fila, { yaConExpediente = [] } = {}) {
  const avisos = [];
  const { datos, nota, motivo } = fila;

  if (fila.sinAperturaReconocida) {
    avisos.push("No se reconoció dónde empieza el expediente: se tomó el archivo entero.");
  }

  if (datos.resultado === "incompleto") {
    avisos.push("El expediente no trae ni orden de sanción ni resolución de archivo: no está terminado.");
  }
  for (const pieza of datos.faltan) avisos.push(`Falta ${pieza}.`);

  if (datos.resultado === "sancion" && datos.dias_sancion === null) {
    avisos.push("No se pudo leer la sanción impuesta: escríbala a mano.");
  }

  if (!nota) {
    avisos.push(motivo === "varios"
      ? "Coinciden varios casos: elija cuál antes de guardar."
      : "No se encontró el caso al que pertenece.");
    return { ...fila, estado: "detenido", motivoTexto: null, avisos };
  }

  const motivoTexto = MOTIVOS[motivo] || null;
  if (motivoTexto && motivo !== "numero_nota") avisos.push(motivoTexto);
  if (yaConExpediente.includes(nota.id)) {
    avisos.push("Ese caso ya tiene un expediente guardado: al confirmar se reemplaza.");
  }

  if (datos.resultado === "incompleto") return { ...fila, estado: "detenido", avisos };

  const limpio = motivo === "numero_nota"
    && !datos.faltan.length
    && !yaConExpediente.includes(nota.id)
    && !(datos.resultado === "sancion" && datos.dias_sancion === null)
    && !fila.sinAperturaReconocida;
  return { ...fila, estado: limpio ? "listo" : "revisar", motivoTexto, avisos };
}

/**
 * De los PDF leídos a las filas de la revisión.
 *
 * `archivos` es [{ nombre, textosPorPagina }]: una entrada por PDF, con el
 * texto de cada una de sus páginas en orden. `yaConExpediente` son los id de
 * caso que ya tienen un expediente guardado.
 */
export function prepararLote(archivos = [], notas = [], efectivos = [], yaConExpediente = []) {
  const filas = [];

  archivos.forEach((archivo, indiceArchivo) => {
    const paginas = archivo.textosPorPagina || [];
    const sinAperturaReconocida = !paginas.some((t) => esPaginaDeInicioDeExpediente(t));
    for (const bloque of separarExpedientes(paginas)) {
      const datos = leerExpediente(bloque.texto);
      const hallado = buscarFaltaDelExpediente(datos, notas, efectivos);
      filas.push(revisarFila({
        archivo: archivo.nombre || `documento ${indiceArchivo + 1}`,
        indiceArchivo,
        desde: bloque.desde,
        hasta: bloque.hasta,
        paginas: bloque.hasta - bloque.desde + 1,
        // Se conserva para poder pedirle a la IA lo que las reglas no leyeron.
        texto: bloque.texto,
        sinAperturaReconocida,
        datos,
        nota: hallado?.nota || null,
        motivo: hallado?.motivo || null,
        candidatas: hallado?.candidatas || [],
        nombreSugerido: nombreDelRecorte(datos, hallado?.nota || null),
      }, { yaConExpediente }));
    }
  });

  return marcarRepetidos(filas);
}

/**
 * Dos expedientes del mismo lote que apuntan al mismo caso siempre son un
 * error: o se escaneó dos veces, o el cruce se equivocó en uno. Se detienen los
 * dos, porque no hay forma de saber cuál es el bueno sin mirarlos.
 */
export function marcarRepetidos(filas = []) {
  const cuenta = new Map();
  for (const f of filas) {
    if (f.nota) cuenta.set(f.nota.id, (cuenta.get(f.nota.id) || 0) + 1);
  }
  return filas.map((f) => {
    if (!f.nota || cuenta.get(f.nota.id) < 2) return f;
    return {
      ...f,
      estado: "detenido",
      avisos: [...f.avisos, "Otro expediente de este mismo lote apunta al mismo caso."],
    };
  });
}

/**
 * ¿Hace falta preguntarle a la IA por este expediente?
 *
 * Solo cuando las reglas dejaron sin leer algo que impide archivarlo: ninguna
 * de las dos llaves (número de nota y CIP), o la sanción de un expediente que
 * sí llegó a orden. Un escaneo torcido o muy sucio deja el texto a medias y es
 * justo lo que la IA sí puede rescatar.
 */
export function necesitaAyudaDeIA(fila) {
  if (!fila?.datos) return false;
  const { datos } = fila;
  if (!datos.numero_nota_falta && !datos.cip_investigado) return true;
  return datos.resultado === "sancion" && datos.dias_sancion === null;
}

/** Campos que la IA puede completar; el resto nunca se pisa. */
const CAMPOS_DE_IA = ["numero_nota_falta", "cip_investigado", "codigo_infraccion", "dias_sancion", "tipo_sancion"];

/**
 * Mete lo que leyó la IA en una fila y la vuelve a cruzar.
 *
 * La IA solo rellena huecos: si las reglas ya leyeron un dato, ese manda,
 * porque salió del documento con una regla comprobada y no de una
 * interpretación. Y lo que la IA aporte queda anotado, para que quien revisa
 * sepa que esa fila no se leyó del todo sola.
 */
export function aplicarLecturaDeIA(fila, deIA, notas = [], efectivos = [], yaConExpediente = []) {
  if (!deIA) return fila;
  const datos = { ...fila.datos };
  const aportados = [];
  for (const campo of CAMPOS_DE_IA) {
    const valor = deIA[campo];
    if (datos[campo] === null || datos[campo] === undefined) {
      if (valor !== null && valor !== undefined && valor !== "") {
        datos[campo] = valor;
        aportados.push(campo);
      }
    }
  }
  if (datos.numeros_nota && datos.numero_nota_falta && !datos.numeros_nota.includes(datos.numero_nota_falta)) {
    datos.numeros_nota = [datos.numero_nota_falta, ...datos.numeros_nota];
  }
  if (!aportados.length) return fila;

  const hallado = buscarFaltaDelExpediente(datos, notas, efectivos);
  const revisada = revisarFila({
    ...fila,
    datos,
    nota: hallado?.nota || null,
    motivo: hallado?.motivo || null,
    candidatas: hallado?.candidatas || [],
    nombreSugerido: nombreDelRecorte(datos, hallado?.nota || null),
  }, { yaConExpediente });

  return {
    ...revisada,
    leidoPorIA: aportados,
    estado: revisada.estado === "detenido" ? "detenido" : "revisar",
    avisos: [...revisada.avisos, "El escaneo no se dejaba leer del todo: la IA completó parte de los datos. Compruébelos."],
  };
}

/** Las filas que se pueden guardar: tienen caso y no están detenidas. */
export function filasGuardables(filas = []) {
  return filas.filter((f) => f.nota && f.estado !== "detenido");
}

/** Conteo para el encabezado de la pantalla de revisión. */
export function resumenDelLote(filas = []) {
  return {
    total: filas.length,
    listos: filas.filter((f) => f.estado === "listo").length,
    revisar: filas.filter((f) => f.estado === "revisar").length,
    detenidos: filas.filter((f) => f.estado === "detenido").length,
  };
}

/** Lo que se graba en public.expedientes por cada fila confirmada. */
export function filaAExpediente(fila, { path, nombre } = {}) {
  return {
    nota_id: fila.nota.id,
    numero_oficio: fila.datos.numero_oficio || null,
    numero_ht: fila.datos.numero_ht || null,
    dias_sancion: fila.datos.dias_sancion,
    archivo_expediente_path: path || null,
    archivo_expediente_nombre: nombre || null,
  };
}
