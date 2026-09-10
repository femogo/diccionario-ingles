#!/usr/bin/env python3
"""Valida las respuestas de ChatGPT y arma el diccionario de la aplicacion.

Uso:
    python3 tools/validar.py respuestas/*.txt
    python3 tools/validar.py --con-flexiones respuestas/*.txt

Comprueba formato, ids y categorias, avisa de traducciones ambiguas, y escribe
words.txt en el mismo formato que espera la pantalla de Ajustes de la app.

Por defecto se queda con una sola forma por lema. La lista de frecuencia de
origen cuenta formas, no lemas, asi que sin este paso "is", "was", "are",
"were", "been" y "am" entran las seis traducidas por "ser": en el modo
espanol-ingles la pregunta pasa a tener seis respuestas validas y el juego deja
de medir nada. Con --con-flexiones se conservan todas.
"""
import sys, re, collections, pathlib, signal

# Sin esto, canalizar la salida a `head` revienta con BrokenPipeError.
if hasattr(signal, "SIGPIPE"):
    signal.signal(signal.SIGPIPE, signal.SIG_DFL)

POS = {"noun","verb","adj","adv","pron","prep","conj","det","num","interj","phrasal","drop"}
CEFR = {"A1","A2","B1","B2","C1","C2"}
NOISE = re.compile(r"^\s*(```|LISTO|aqu[ií]|here|nota|note|lote)", re.I)

argumentos = [a for a in sys.argv[1:] if not a.startswith("--")]
dedup = "--con-flexiones" not in sys.argv

# Lista opcional de palabras a excluir, una por linea. La lista de frecuencia
# viene de subtitulos, asi que trae malsonantes de uso muy real; que entren o no
# es decision de quien monta el diccionario, no del script.
# Lo que se pidio en cada lote, para comprobar que la respuesta corresponde.
LOTES = pathlib.Path(__file__).with_name("lotes")
pedido = {}
if LOTES.is_dir():
    for lote in LOTES.glob("lote_*.txt"):
        for linea in lote.read_text(encoding="utf-8").splitlines():
            if "|" in linea:
                rank, palabra = linea.strip().split("|", 1)
                pedido[int(rank)] = palabra

EXCLUIR = pathlib.Path(__file__).with_name("excluir.txt")
excluidas = set()
if EXCLUIR.exists():
    excluidas = {
        l.strip().lower()
        for l in EXCLUIR.read_text(encoding="utf-8").splitlines()
        if l.strip() and not l.startswith("#")
    }

filas, errores, desajustes = {}, [], []

for ruta in argumentos:
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
        # La comprobacion que importa: que la respuesta sea de la palabra que se
        # pidio. Un modelo que pierde el hilo genera "las siguientes palabras
        # frecuentes" de memoria, con el formato perfecto y el contenido ajeno.
        esperada = pedido.get(int(wid))
        if esperada is not None and esperada != en.strip().lower():
            desajustes.append(f"{sitio}  id {wid}: se pidio {esperada!r}, devolvio {en.strip().lower()!r}")
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

antes = len(utiles)
utiles = [f for f in utiles if f[1] not in excluidas and f[2] not in excluidas]
apartadas = antes - len(utiles)

def preferida(a, b):
    """Entre dos formas del mismo lema gana la que ya es forma de diccionario."""
    for f in (a, b):
        if f[1] == f[2]:
            return f
    return a if a[0] < b[0] else b

flexiones = 0
if dedup:
    por_lema = {}
    for f in utiles:
        clave = (f[2], f[3])
        por_lema[clave] = preferida(por_lema[clave], f) if clave in por_lema else f
    flexiones = len(utiles) - len(por_lema)
    utiles = list(por_lema.values())

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
if dedup:
    print(f"flexiones    {flexiones} plegadas en su lema")
if apartadas:
    print(f"excluidas    {apartadas} por excluir.txt")
print(f"errores      {len(errores)}")
if desajustes:
    print(f"DESAJUSTES   {len(desajustes)} respuestas no corresponden a la palabra pedida")
    for d in desajustes[:10]:
        print("   ", d)
    if len(desajustes) > 10:
        print(f"    ... y {len(desajustes)-10} mas")
    print("    El modelo perdio el hilo y genero palabras por su cuenta.")
    print("    Vuelve a pegar el prompt maestro y repite ese lote.")
for e in errores[:40]:
    print("   ", e)
if len(errores) > 40:
    print(f"    ... y {len(errores)-40} mas")

if dup_lema:
    print(f"\navisos: {len(dup_lema)} lemas repetidos, p.ej. {dup_lema[:8]}")
if colisiones:
    print(f"avisos: {len(colisiones)} traducciones ambiguas sin hint, p.ej.:")
    for (es, pos), v in colisiones[:8]:
        print(f"    '{es}' ({pos}) <- {[x[1] for x in v]}")

if filas:
    huecos = sorted(set(range(min(filas), max(filas)+1)) - set(filas))
    if huecos:
        print(f"\nFALTAN {len(huecos)} ids: {huecos[:20]}{' ...' if len(huecos)>20 else ''}")

if desajustes:
    # No se sobrescribe un words.txt bueno con el resultado de un lote que el
    # modelo se invento.
    print("\nno se escribe words.txt: corrige los desajustes primero")
    sys.exit(1)

with open("words.txt", "w", encoding="utf-8") as fh:
    fh.write("# Formato: rank|en|lemma|pos|es|es_alt|cefr|hint\n")
    for fila in sorted(utiles):
        fh.write("|".join(str(c) for c in fila) + "\n")
print(f"\nescrito words.txt con {len(utiles)} palabras")
print("Pasalo al movil e importalo desde Ajustes > Importar diccionario.")
sys.exit(1 if errores or desajustes else 0)
