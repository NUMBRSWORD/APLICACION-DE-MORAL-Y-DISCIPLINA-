// La etapa que viene DESPUÉS de que el caso se resuelve: remitir el expediente.
//
// El orden real del trámite, y por qué el oficio y la HT llegan tarde:
//
//   1. El investigado firma y devuelve el expediente completo.
//   2. Se sube el expediente firmado. Eso cierra el caso.
//   3. Recién entonces se hace el OFICIO de remisión.
//   4. Y se adjunta la HOJA DE TRÁMITE, la que devuelve DIVOPUS ya recepcionada.
//
// Ninguno de los dos existe cuando se sube el legajo, así que no se pueden
// pedir ahí. Lo que hace falta es saber, sin abrir caso por caso, cuáles ya
// tienen el expediente en mano y están esperando su oficio: esa es la lista con
// la que uno se sienta a hacerlos.

/** El expediente firmado ya volvió y está registrado en el caso. */
export function expedienteFirmadoRecibido(nota) {
  return !!nota?.archivo_orden_notificacion_path;
}

/** La remisión a mesa de partes de ese caso, si ya se registró. */
export function remisionDelCaso(nota, remitidos = []) {
  if (!nota?.id) return null;
  return remitidos.find((r) => r.nota_id === nota.id) || null;
}

/**
 * En qué punto de la remisión está el caso:
 *
 * - "sin_expediente": el legajo firmado todavía no volvió.
 * - "falta_oficio": ya está en mano; toca hacer el oficio de remisión.
 * - "falta_ht": el oficio ya está; falta la HT que devuelve DIVOPUS recepcionada.
 * - "remitido": no queda nada por adjuntar.
 */
export function estadoDeRemision(nota, remitidos = []) {
  if (!expedienteFirmadoRecibido(nota)) return "sin_expediente";
  const remision = remisionDelCaso(nota, remitidos);
  if (!remision?.archivo_oficio_path) return "falta_oficio";
  if (!remision.archivo_ht_path) return "falta_ht";
  return "remitido";
}

/** Tiene el expediente en mano y le falta el oficio: es lo accionable. */
export function esperaOficio(nota, remitidos = []) {
  return estadoDeRemision(nota, remitidos) === "falta_oficio";
}

/** Solo falta adjuntar la HT que DIVOPUS devolvió recepcionada. */
export function esperaHojaDeTramite(nota, remitidos = []) {
  return estadoDeRemision(nota, remitidos) === "falta_ht";
}

/** Los casos que esperan oficio: la lista con la que se hacen de una sentada. */
export function casosQueEsperanOficio(notas = [], remitidos = []) {
  return notas.filter((n) => esperaOficio(n, remitidos));
}

/**
 * Los días que dice el expediente firmado contra los que registró la web al
 * generar la orden. Si no cuadran, alguien se equivocó: o la orden salió con
 * otros días de los que se guardaron, o la lectura del escaneo falló. En
 * cualquiera de los dos casos hay que mirarlo antes de archivar, no después.
 */
export function discrepanciaDeDias(leidos, nota) {
  const registrados = nota?.sancion_dias;
  if (leidos === null || leidos === undefined) return null;
  if (registrados === null || registrados === undefined) return null;
  if (Number(leidos) === Number(registrados)) return null;
  return { leidos: Number(leidos), registrados: Number(registrados) };
}
