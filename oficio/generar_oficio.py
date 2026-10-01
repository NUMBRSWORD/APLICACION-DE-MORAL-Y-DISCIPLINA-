"""Genera el oficio de remisión de sanción a partir de datos.json.

Uso:  python oficio/generar_oficio.py            (usa oficio/datos.json)
      python oficio/generar_oficio.py otro.json  (usa otro archivo)

Los datos fijos (jefe, comisaría/unidad) están en el bloque "fijos";
lo que cambia en cada caso, en "caso". Se puede editar solo lo que cambió.
"""
import json, re, sys, docx
from pathlib import Path

base = Path(__file__).parent
datos_path = Path(sys.argv[1]) if len(sys.argv) > 1 else base / 'datos.json'
datos = json.loads(datos_path.read_text(encoding='utf-8'))
valores = {**datos['fijos'], **datos['caso']}

d = docx.Document(base / 'plantilla_oficio_remision_sancion.docx')

def reemplazar(texto):
    return re.sub(r'\{\{(\w+)\}\}', lambda m: str(valores.get(m.group(1), m.group(0))), texto)

def recorrer(elemento):
    for t in elemento.iter('{http://schemas.openxmlformats.org/wordprocessingml/2006/main}t'):
        if t.text and '{{' in t.text:
            t.text = reemplazar(t.text)

recorrer(d.element.body)
faltan = set(re.findall(r'\{\{(\w+)\}\}', d.element.xml))
if faltan:
    sys.exit(f'Faltan datos: {sorted(faltan)}')
salida = base / f"OFICIO_{valores['numero_oficio']}-{valores['anio']}.docx"
d.save(salida)
print('Listo:', salida)
