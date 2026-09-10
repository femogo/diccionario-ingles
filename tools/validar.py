#!/usr/bin/env python3
"""Valida las respuestas de ChatGPT y arma el diccionario de la aplicacion.

Uso:
    python3 tools/validar.py respuestas/*.txt

Comprueba formato, ids y categorias, avisa de traducciones ambiguas, y escribe
words.txt en el mismo formato que espera la pantalla de Ajustes de la app.
"""
import sys, re, collections, pathlib

POS = {"noun","verb","adj","adv","pron","prep","conj","det","num","interj","phrasal","drop"}
CEFR = {"A1","A2","B1","B2","C1","C2"}
NOISE = re.compile(r"^\s*(```|LISTO|aqu[ií]|here|nota|note|lote)", re.I)

filas, errores = {}, []

for ruta in sys.argv[1:]:
    for n, linea in enumerate(pathlib.Path(ruta).read_text(encoding="utf-8").splitlines(), 1):
        linea = linea.strip()
        if not linea or NOISE.match(linea):
            continue
        campos = linea.split("|")
        sitio = f"{pathlib.Path(ruta).name}:{n}"
        if len(campos) != 8:
            errores.append(f"{sitio}  {len(campos)} campos en vez de 8 -> {linea[:70]}")
            continue
        wid, en, lemma, pos, es, es_alt, cefr, hint = [c.strip() for c in campos]
        if not wid.isdigit():
            errores.append(f"{sitio}  id no numerico: {wid!r}")
            continue
        if pos not in POS:
            errores.append(f"{sitio}  pos invalida: {pos!r}")
            continue
        if pos != "drop":
            if cefr not in CEFR:
                errores.append(f"{sitio}  cefr invalido: {cefr!r} ({en})")
                continue
            if not es or es == "-":
                errores.append(f"{sitio}  sin traduccion: {en}")
                continue
        wid = int(wid)
        if wid in filas and filas[wid][1] != en:
            errores.append(f"{sitio}  id {wid} repetido con palabra distinta")
            continue
        filas[wid] = (wid, en, lemma, pos, es, es_alt, cefr, hint)

utiles = [f for f in filas.values() if f[3] != "drop"]
descartadas = len(filas) - len(utiles)

# lemas duplicados: mismas dos palabras compitiendo por la misma entrada
por_lema = collections.Counter(f[2] for f in utiles)
dup_lema = [l for l, c in por_lema.items() if c > 1]

# colisiones: dos palabras inglesas distintas con la misma traduccion y sin hint
por_es = collections.defaultdict(list)
for f in utiles:
    por_es[(f[4], f[3])].append(f)
colisiones = [(k, v) for k, v in por_es.items() if len(v) > 1 and any(x[7] == "-" for x in v)]

print(f"leidas       {len(filas)}")
print(f"utilizables  {len(utiles)}")
print(f"descartadas  {descartadas} (pos=drop)")
print(f"errores      {len(errores)}")
for e in errores[:40]:
    print("   ", e)
if len(errores) > 40:
    print(f"    ... y {len(errores)-40} mas")

if dup_lema:
    print(f"\navisos: {len(dup_lema)} lemas repetidos (formas flexionadas), p.ej. {dup_lema[:8]}")
if colisiones:
    print(f"avisos: {len(colisiones)} traducciones ambiguas sin hint, p.ej.:")
    for (es, pos), v in colisiones[:8]:
        print(f"    '{es}' ({pos}) <- {[x[1] for x in v]}")

if filas:
    huecos = sorted(set(range(min(filas), max(filas)+1)) - set(filas))
    if huecos:
        print(f"\nFALTAN {len(huecos)} ids: {huecos[:20]}{' ...' if len(huecos)>20 else ''}")

with open("words.txt", "w", encoding="utf-8") as fh:
    fh.write("# Formato: rank|en|lemma|pos|es|es_alt|cefr|hint\n")
    for fila in sorted(utiles):
        fh.write("|".join(str(c) for c in fila) + "\n")
print(f"\nescrito words.txt con {len(utiles)} palabras")
print("Pasalo al movil e importalo desde Ajustes > Importar diccionario.")
sys.exit(1 if errores else 0)
