const permitidas = new Set(["analizar-descargo-sancion", "asistente-md", "asistente-normativa", "extraer-nota-informativa", "extraer-texto-vision", "generar-resumen-casos", "redactar-analisis", "redactar-hecho-imputacion", "revisar-documento-ia", "sugerir-codigo-infraccion"]);
// Tras la publicación, el administrador configura el ingreso HTTPS privado de su PC.
Deno.serve(async req => {
  const cors = {"Access-Control-Allow-Origin":"https://numbrsword.github.io","Access-Control-Allow-Headers":"authorization, apikey, content-type, x-client-info"};
  const error = (status, mensaje) => Response.json({error:mensaje},{status,headers:cors});
  if (req.method === "OPTIONS") return new Response(null,{headers:cors});
  const nombre = new URL(req.url).pathname.split("/").pop();
  if (req.method !== "POST" || !permitidas.has(nombre)) return error(404,"Operación desconocida.");
  const auth = req.headers.get("Authorization");
  if (!auth?.startsWith("Bearer ")) return error(401,"Inicie sesión.");
  const base = Deno.env.get("IA_OFICINA_HTTPS");
  const llave = Deno.env.get("IA_OFICINA_SECRETO");
  if (!base || !llave || new URL(base).protocol !== "https:") return error(503,"El acceso remoto al lector de la oficina está pendiente de configuración.");
  try {
    const r = await fetch(new URL("/ia/"+nombre,base), {method:"POST",redirect:"error",
      headers:{"Authorization":auth,"Content-Type":"application/json","X-Oficina":llave},
      body:req.body,signal:req.signal});
    return new Response(r.body,{status:r.status,headers:{...cors,"Content-Type":"application/json"}});
  } catch { return error(503,"El lector de la oficina no está disponible."); }
});
