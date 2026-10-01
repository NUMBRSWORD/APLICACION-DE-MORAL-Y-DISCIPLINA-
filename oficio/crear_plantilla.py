import docx, re
from lxml import etree

d = docx.Document('OFICIO_PLANTILLA_CASO_HIPOTETICO_V4.docx')

def setp(p, t):
    p.runs[0].text = t
    for r in p.runs[1:]:
        r.text = ''

for p in d.paragraphs:
    t = p.text
    if t.startswith('Ventanilla, '):
        setp(p, '{{fecha}}')
    elif t.startswith('OFICIO N'):
        setp(p, 'OFICIO N° {{numero_oficio}}-{{anio}}-COMOPPOL-PNP/DIRNOS/REGPOL-CALL/DIVOPUS-VENTANILLA-COM VENTANILLA "A".MYD')
    elif 'Coronel PNP' in t:
        setp(p, 'SEÑOR\t:\t{{jefe_grado}}')
    elif 'REZ RAMOS' in t:
        setp(p, '\t\t{{jefe_nombre}}')
    elif t.startswith('\t\tJEFE'):
        setp(p, '\t\t{{jefe_cargo}}')
    elif t.startswith('Tengo el honor'):
        setp(p, 'Tengo el honor de dirigirme a Ud., con la finalidad de remitirle adjunto al presente, en ejemplar triplicado (03), la orden de sanción con sanción impuesta de {{sancion_dias_texto}}, la notificación y entrega de acto administrativo, el acta de no recepción de descargos y el inicio de imputación de infracción leve ({{codigo_infraccion}}), impuesta por el {{oficial_grado}} {{oficial_nombre}} al {{investigado_grado}} {{investigado_nombre}}; por los motivos que se especifican en la misma, cursada el presente para las acciones correspondientes.')
    elif t.startswith('MASG'):
        setp(p, '{{iniciales}}')

s = etree.tostring(d.element.body, encoding='unicode')
i = s.index('OA-00000000')
s = s[:i] + s[i:].replace('Luis EJEMPLO DIAZ', '{{firma_nombre}}')
for a, b in {
    'OA-00000000': '{{firma_oa}}',
    'OFICIAL DE PERMANENCIA': '{{firma_cargo}}',
    'TENIENTE PNP</w:t>': '{{firma_grado}}</w:t>',
    '>DIVOPUS 03 VENTANILLA<': '>{{unidad}}<',
}.items():
    s = s.replace(a, b)
print(sorted(set(re.findall(r'\{\{\w+\}\}', s))))
body = d.element.body
body.getparent().replace(body, etree.fromstring(s))
d.save('oficio/plantilla_oficio_remision_sancion.docx')
