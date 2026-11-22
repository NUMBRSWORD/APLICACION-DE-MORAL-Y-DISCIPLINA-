type Bloque = { type: string; source?: { data: string }; text?: string };
type Entrada = {
  system: string; messages: {role: string; content: string | Bloque[]}[];
  max_tokens: number; contexto?: number; tools?: {name:string; input_schema?:object}[];
  output_config?: {format:{schema:object}};
};
async function generar(model: string, messages: object[], maxTokens: number, formato?: object, contexto?: number) {
  const respuesta = await fetch("http://127.0.0.1:11434/api/chat", {
    method:"POST", redirect:"error", headers:{"Content-Type":"application/json"},
    signal:AbortSignal.timeout(240000),
    body:JSON.stringify({model, messages, stream:false, ...(model.startsWith("qwen") ? {think:false} : {}),
      ...(formato ? {format:formato} : {}), options:{temperature:0,num_predict:maxTokens,...(contexto ? {num_ctx:contexto} : {})}})
  });
  if (!respuesta.ok) throw new Error("Ollama no pudo procesar el documento.");
  return await respuesta.json();
}

export async function inferirLocal(entrada: Entrada, visionJson: boolean = false) {
  const herramienta = entrada.tools?.[0];
  const contenido = entrada.messages[0].content;
  let texto: string;
  let limitado = false;
  if (Array.isArray(contenido)) {
    const paginas: string[] = [];
    for (const bloque of contenido.filter(b => b.type === "image")) {
      if (!bloque.source?.data) throw new Error("Imagen sin contenido.");
      const pagina = await generar("glm-ocr", [{role:"user",content:"Text Recognition:",images:[bloque.source.data]}],4096);
      if (pagina.done_reason === "length") throw new Error("OCR incompleto. Divida la pÃ¡gina.");
      paginas.push(pagina.message.content.trim());
    }
    const separador = entrada.system.includes("<<<FIN DE PAGINA>>>") ? "\n<<<FIN DE PAGINA>>>\n" : "\n\n";
    texto = visionJson ? JSON.stringify({texto:paginas.join(separador)}) : paginas.join(separador);
  } else {
    const respuesta = await generar("qwen3:4b", [{role:"system",content:entrada.system}, ...entrada.messages],
      entrada.max_tokens, herramienta?.input_schema ?? entrada.output_config?.format.schema, entrada.contexto);
    texto = respuesta.message.content.trim();
    limitado = respuesta.done_reason === "length";
  }
  if (!texto) throw new Error("El modelo local devolviÃ³ una respuesta vacÃ­a.");
  if (herramienta && limitado) throw new Error("Respuesta estructurada incompleta.");
  const bloque = herramienta ? {type:"tool_use",name:herramienta.name,input:JSON.parse(texto)} : {type:"text",text:texto};
  return Response.json({content:[bloque],stop_reason:limitado ? "max_tokens" : "end_turn"});
}
