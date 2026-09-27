import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "jsr:@supabase/supabase-js@2";

// Avisos nativos de la aplicación Android (FCM HTTP v1).
//
// Qué viaja al teléfono: únicamente `user_id` y `tipo`. Nunca nombres, CIP, códigos de
// infracción, sanciones, fechas ni texto libre: el texto lo arma el propio teléfono a
// partir del tipo, y descarta el mensaje si no es para la cuenta que tiene abierta.
//
// Sobre los plazos: el cálculo de días hábiles de aquí solo salta sábados y domingos, no
// conoce feriados. Por eso jamás se anuncia un plazo como vencido; el único aviso es
// «revise el plazo», que es una invitación a comprobarlo en el expediente.

const URL_SUPABASE = Deno.env.get("SUPABASE_URL") || "";
const SERVICIO = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY") || "";
const CUENTA_FCM = Deno.env.get("FCM_CUENTA_SERVICIO") || "";
const SECRETO_CRON = Deno.env.get("AVISOS_CRON_SECRET") || "";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type, x-avisos-cron-secret",
};
const json = (cuerpo: unknown, status = 200) =>
  new Response(JSON.stringify(cuerpo), { status, headers: { ...cors, "Content-Type": "application/json" } });

// ---------- Fechas (zona de Lima) ----------

const hoyLima = () => {
  const partes = new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Lima", year: "numeric", month: "2-digit", day: "2-digit",
  }).formatToParts(new Date());
  const v = (t: string) => partes.find((p) => p.type === t)?.value || "";
  return `${v("year")}-${v("month")}-${v("day")}`;
};

/** Solo salta fin de semana: sirve para avisar, nunca para declarar un plazo vencido. */
const siguienteDiaHabil = (fecha: string) => {
  const d = new Date(`${fecha}T12:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  while ([0, 6].includes(d.getUTCDay())) d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
};

const diasDesde = (instante: string, hoy: string) =>
  Math.floor((Date.parse(`${hoy}T12:00:00Z`) - Date.parse(instante)) / 86400000);

// ---------- Credencial de Google para FCM ----------

const base64url = (datos: ArrayBuffer | string) => {
  const bytes = typeof datos === "string" ? new TextEncoder().encode(datos) : new Uint8Array(datos);
  let binario = "";
  for (const b of bytes) binario += String.fromCharCode(b);
  return btoa(binario).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

const clavePrivada = async (pem: string) => {
  const cuerpo = pem.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const binario = atob(cuerpo);
  const bytes = new Uint8Array(binario.length);
  for (let i = 0; i < binario.length; i++) bytes[i] = binario.charCodeAt(i);
  return await crypto.subtle.importKey("pkcs8", bytes.buffer,
    { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
};

/** Token de acceso de la cuenta de servicio, con el permiso mínimo de mensajería. */
async function tokenDeAcceso(cuenta: { client_email: string; private_key: string }) {
  const ahora = Math.floor(Date.now() / 1000);
  const cabecera = base64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const cuerpo = base64url(JSON.stringify({
    iss: cuenta.client_email,
    scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token",
    iat: ahora,
    exp: ahora + 3600,
  }));
  const firma = await crypto.subtle.sign("RSASSA-PKCS1-v1_5",
    await clavePrivada(cuenta.private_key), new TextEncoder().encode(`${cabecera}.${cuerpo}`));
  const respuesta = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST",
    headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({
      grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
      assertion: `${cabecera}.${cuerpo}.${base64url(firma)}`,
    }),
  });
  if (!respuesta.ok) throw new Error(`No se pudo obtener el token de FCM (${respuesta.status})`);
  const datos = await respuesta.json();
  return datos.access_token as string;
}

// ---------- Qué corresponde avisar de cada expediente ----------

type Nota = Record<string, string | null>;

/** Devuelve el tipo que entiende el teléfono y una clave de estado para no repetir. */
function avisoDeNota(nota: Nota, hoy: string): { tipo: string; clave: string } | null {
  if (!nota.oficial_constato_cip) return null;
  if (nota.archivo_leve_generada_at) return null; // Expediente cerrado.

  if (nota.created_at && diasDesde(nota.created_at, hoy) <= 1
      && !nota.imputacion_generada_at && !nota.fecha_descargo) {
    return { tipo: "caso_nuevo", clave: nota.created_at.slice(0, 10) };
  }
  if (nota.imputacion_generada_at && !nota.fecha_descargo && !nota.orden_sancion_generada_at) {
    const limite = siguienteDiaHabil(nota.imputacion_generada_at.slice(0, 10));
    // Se avisa una sola vez por plazo: la clave es la fecha límite, no el día de hoy.
    if (hoy >= limite || siguienteDiaHabil(hoy) === limite) {
      return { tipo: "plazo_descargo", clave: limite };
    }
  }
  if (nota.fecha_descargo && !nota.orden_sancion_generada_at) {
    return { tipo: "documento_recibido", clave: nota.fecha_descargo.slice(0, 10) };
  }
  if ((nota.orden_sancion_generada_at && !nota.orden_notificada_at)
      || (nota.fecha_reincorporacion && !nota.imputacion_generada_at)) {
    return { tipo: "pasos_pendientes", clave: hoy };
  }
  return null;
}

// ---------- Envío ----------

Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (!URL_SUPABASE || !SERVICIO || !SECRETO_CRON) {
    return json({ error: "Faltan secretos de Supabase para los avisos." }, 503);
  }
  if (!CUENTA_FCM) {
    return json({ error: "Falta el secreto FCM_CUENTA_SERVICIO con la cuenta de servicio." }, 503);
  }
  if (req.headers.get("x-avisos-cron-secret") !== SECRETO_CRON) {
    return json({ error: "No autorizado." }, 403);
  }

  try {
    const cuenta = JSON.parse(CUENTA_FCM) as
      { client_email: string; private_key: string; project_id: string };
    const admin = createClient(URL_SUPABASE, SERVICIO);
    const hoy = hoyLima();

    const { data: notas, error: errorNotas } = await admin
      .from("notas_informativas")
      .select("id, oficial_constato_cip, created_at, fecha_reincorporacion, imputacion_generada_at,"
        + " fecha_descargo, orden_sancion_generada_at, orden_notificada_at, archivo_leve_generada_at");
    if (errorNotas) throw errorNotas;

    // Solo cuentas aprobadas reciben avisos; el CIP es lo que enlaza expediente y persona.
    const { data: perfiles, error: errorPerfiles } = await admin
      .from("profiles").select("id, cip").eq("estado", "aprobado").not("cip", "is", null);
    if (errorPerfiles) throw errorPerfiles;
    const usuarioPorCip = new Map<string, string>();
    for (const p of perfiles || []) usuarioPorCip.set(String(p.cip), String(p.id));

    const { data: dispositivos, error: errorDisp } = await admin
      .from("dispositivos_android").select("token, user_id");
    if (errorDisp) throw errorDisp;
    const tokensPorUsuario = new Map<string, string[]>();
    for (const d of dispositivos || []) {
      const lista = tokensPorUsuario.get(String(d.user_id)) || [];
      lista.push(String(d.token));
      tokensPorUsuario.set(String(d.user_id), lista);
    }

    let acceso: string | null = null;
    const pasosAvisadosHoy = new Set<string>();
    let enviados = 0, omitidos = 0, tokensRetirados = 0;

    for (const fila of notas || []) {
      const nota = fila as unknown as Nota;
      const aviso = avisoDeNota(nota, hoy);
      if (!aviso) { omitidos++; continue; }

      const usuario = usuarioPorCip.get(String(nota.oficial_constato_cip));
      if (!usuario) { omitidos++; continue; }
      const tokens = tokensPorUsuario.get(usuario) || [];
      if (tokens.length === 0) { omitidos++; continue; }

      // Un solo aviso de pasos pendientes por persona y día, aunque tenga varios casos.
      if (aviso.tipo === "pasos_pendientes") {
        if (pasosAvisadosHoy.has(usuario)) { omitidos++; continue; }
      }

      const marca = `android:${aviso.tipo}`;
      const { data: anterior } = await admin.from("alertas_movil_enviadas")
        .select("id").eq("nota_id", nota.id).eq("tipo", marca)
        .eq("clave_estado", aviso.clave).maybeSingle();
      if (anterior) {
        if (aviso.tipo === "pasos_pendientes") pasosAvisadosHoy.add(usuario);
        omitidos++;
        continue;
      }

      if (!acceso) acceso = await tokenDeAcceso(cuenta);

      let entregas = 0;
      for (const token of tokens) {
        const respuesta = await fetch(
          `https://fcm.googleapis.com/v1/projects/${cuenta.project_id}/messages:send`,
          {
            method: "POST",
            headers: { Authorization: `Bearer ${acceso}`, "Content-Type": "application/json" },
            body: JSON.stringify({
              message: {
                token,
                // Solo datos: el teléfono decide si lo muestra y con qué texto.
                data: { user_id: usuario, tipo: aviso.tipo },
                android: { priority: "HIGH" },
              },
            }),
          },
        );
        if (respuesta.ok) { entregas++; continue; }

        const detalle = await respuesta.text();
        // El teléfono desinstaló la app o el token caducó: se retira del registro.
        if (respuesta.status === 404 || detalle.includes("UNREGISTERED")
            || detalle.includes("INVALID_ARGUMENT")) {
          await admin.from("dispositivos_android").delete().eq("token", token);
          tokensRetirados++;
        } else {
          // Nunca se registra el token ni el destinatario.
          console.error("Envío rechazado por FCM:", respuesta.status);
        }
      }

      if (entregas > 0) {
        await admin.from("alertas_movil_enviadas")
          .insert({ nota_id: nota.id, tipo: marca, clave_estado: aviso.clave });
        if (aviso.tipo === "pasos_pendientes") pasosAvisadosHoy.add(usuario);
        enviados += entregas;
      } else omitidos++;
    }

    return json({ fecha: hoy, enviados, omitidos, tokensRetirados });
  } catch (error) {
    console.error("Fallo al procesar los avisos:", error instanceof Error ? error.message : error);
    return json({ error: "No se pudieron procesar los avisos de Android." }, 500);
  }
});
