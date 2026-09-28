# Subir expedientes firmados en lote (pendiente de construir)

Acordado el 28/09/2026. Falta empezar: el responsable enviará modelos de
expediente firmado para ajustar la lectura antes de escribir la parte de IA.

## Qué se quiere

El caso ya terminó: el investigado firmó y la sanción está impuesta. El
administrador junta todos esos expedientes completos, los escanea o los tiene en
PDF, y **los sube todos de una vez**. La aplicación lee cada documento, reconoce
de quién es y a qué falta corresponde, y lo guarda en ese expediente.

Lo que hoy obliga a hacer y debe desaparecer: entrar caso por caso, buscar al
efectivo en la lista y subirle su PDF.

## Dónde encaja en la base

Ya existe `public.expedientes`, una fila por nota:

| Columna | Qué guarda |
|---|---|
| `nota_id` | La falta a la que pertenece |
| `archivo_expediente_path` / `archivo_expediente_nombre` | El PDF firmado |
| `numero_oficio`, `numero_ht` | Referencias del documento |
| `dias_sancion` | Días impuestos |

El archivo va al depósito `expedientes`, cuya política de subida ya exige ser
administrador.

## Las tres piezas

1. **La IA aprende a leer un expediente firmado.** Hoy `extraer-nota-informativa`
   distingue nota de falta y de reincorporación. Hace falta un tipo nuevo que
   devuelva: número de la nota de falta, persona (grado, apellidos, nombres),
   código de infracción y días de sanción. Los modelos que enviará el responsable
   sirven para afinar ese prompt.
2. **Carga en lote con revisión**, igual que las dos que ya funcionan en la web
   (`btnFaltasLote` y `btnReincorporacionLote`): lee todos los PDF, los cruza con
   los casos existentes y muestra **una sola lista** («este PDF va al expediente
   de tal persona, nota N.º tal»). Se confirma de un toque y se guardan todos.
   La revisión se mantiene a propósito: un expediente firmado archivado en el
   caso equivocado es un error grave que no se descubre hasta mucho después.
3. **El botón de la aplicación.** Hoy «Subir expediente completo» abre el módulo
   web de consulta, que lista todos los expedientes; por eso el responsable ve
   una pantalla que no quería. Debe abrir directamente el escáner nativo
   (`ExpedienteActivity`, que ya escanea y elige PDF) con selección múltiple, y
   entregar el lote a la carga del punto 2.

## Lo que ya existe y se reutiliza

- `extractPdfText()` en la web: PDF a texto, con reconocimiento óptico de respaldo.
- `extraer-nota-informativa`: la función de IA a la que se le agrega el tipo nuevo.
- Los dos lotes actuales, que ya resuelven leer varios archivos, cruzarlos con los
  casos y revisar antes de guardar.
- `ExpedienteActivity`: escáner y selección de PDF ya hechos y probados.

## Riesgo a tener presente

`app.js` no tiene pruebas automáticas (hallazgo M8 de la auditoría), y ahí viven
las cargas en lote que usa el comando a diario. La lógica nueva de cruce debe
escribirse en `lib/`, que sí tiene pruebas, y dejar `app.js` solo como conexión.
