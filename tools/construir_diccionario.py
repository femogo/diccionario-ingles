#!/usr/bin/env python3
"""Arma el diccionario cruzando frecuencia de uso con Wiktionary EN-ES.

    python3 tools/construir_diccionario.py

Lee la lista de frecuencia de tools/lotes/, el volcado bilingue de
tools/fuente/en-es.xml, y escribe app/src/main/assets/words.txt.

Los lotes ya revisados a mano en tools/respuestas/ tienen prioridad: donde
exista una entrada revisada, esa gana sobre la automatica.

El nivel MCER no viene de ninguna fuente: se deduce del puesto en la lista de
frecuencia, que es una aproximacion razonable y no una medida.
"""
import re, sys, collections, pathlib, glob

RAIZ = pathlib.Path(__file__).resolve().parent.parent
FUENTE = RAIZ / "tools" / "fuente" / "en-es.xml"
DESTINO = RAIZ / "app" / "src" / "main" / "assets" / "words.txt"

POS = {
    "n": "noun", "v": "verb", "adj": "adj", "adv": "adv", "prep": "prep",
    "conj": "conj", "pron": "pron", "num": "num", "interj": "interj",
    "art": "det", "article": "det", "determiner": "det", "det": "det",
}
# Nombres propios, afijos y frases hechas no son vocabulario de opcion multiple.
DESCARTAR = {"prop", "suffix", "prefix", "phrase", "proverb", "abbr", "initialism",
             "acronym", "particle", "contraction", "punct", "symbol", "letter"}

def nivel(rank):
    for tope, etiqueta in ((500, "A1"), (1500, "A2"), (3000, "B1"),
                           (6000, "B2"), (10000, "C1")):
        if rank <= tope:
            return etiqueta
    return "C2"

def limpiar(traduccion):
    """Deja una traduccion utilizable a partir del campo de Wiktionary."""
    t = re.sub(r"\{[^}]*\}", "", traduccion)      # marcas de genero: {m}, {f}, {mf}
    t = re.sub(r"\[[^\]]*\]", "", t)              # anotaciones entre corchetes
    t = re.sub(r"\([^)]*\)", "", t)               # aclaraciones entre parentesis
    partes = []
    for p in t.split(","):
        p = " ".join(p.split()).strip(" ;·-").lower()
        # Las de mas de tres palabras son perifrasis, no traducciones jugables.
        if p and "|" not in p and len(p.split()) <= 3 and p not in partes:
            partes.append(p)
    return partes

# Glosas que marcan un uso especializado. Un sentido de informatica o de
# botanica no es lo que alguien quiere aprender de "plain" o de "buck".
DOMINIOS = re.compile(
    r"\b(computing|military|botany|zoology|nautical|law|legal|medicine|anatomy|"
    r"chemistry|physics|mathematics|grammar|music|sports|slang|archaic|obsolete|"
    r"dialect|poetic|heraldry|typography|printing)\b", re.I)

def pista(meta):
    """La glosa entre parentesis de Wiktionary sirve de desambiguador."""
    m = re.search(r"\(([^)]{3,60})\)", meta)
    if not m:
        return "-"
    g = " ".join(m.group(1).split()).replace("|", " ")
    # Un parentesis abierto dentro de la glosa significa que se corto a medias.
    if "(" in g or len(g.split()) > 8:
        return "-"
    return g

# --- frecuencia -------------------------------------------------------------
frecuencia = {}
for f in sorted(glob.glob(str(RAIZ / "tools" / "lotes" / "lote_*.txt"))):
    for linea in pathlib.Path(f).read_text(encoding="utf-8").splitlines():
        if "|" in linea:
            r, w = linea.strip().split("|", 1)
            frecuencia[w] = int(r)
if not frecuencia:
    sys.exit("no encuentro tools/lotes/")

# --- fuente bilingue --------------------------------------------------------
if not FUENTE.exists():
    sys.exit(f"falta {FUENTE}\nDescargalo con tools/descargar_fuente.sh")
xml = FUENTE.read_text(encoding="utf-8")
entradas = re.findall(r"<w>\s*<c>(.*?)</c>\s*<d>(.*?)</d>\s*<t>(.*?)</t>\s*</w>", xml, re.S)

por_palabra = collections.defaultdict(list)
for palabra, trad, meta in entradas:
    palabra = palabra.strip().lower()
    if palabra not in frecuencia or not trad.strip():
        continue
    m = re.match(r"\s*\{(\w+)\}", meta)
    if not m:
        continue
    etiqueta = m.group(1)
    if etiqueta in DESCARTAR or etiqueta not in POS:
        continue
    partes = limpiar(trad)
    if partes:
        especializado = bool(DOMINIOS.search(meta))
        por_palabra[palabra].append((POS[etiqueta], partes, pista(meta), especializado))

# --- eleccion de sentido ----------------------------------------------------
filas = {}
for palabra, opciones in por_palabra.items():
    # Se queda la categoria gramatical con mas sentidos registrados: cuantas mas
    # acepciones tiene una palabra en esa categoria, mas central es ese uso.
    cuenta = collections.Counter(pos for pos, _, _, _ in opciones)
    mejor_pos = cuenta.most_common(1)[0][0]
    candidatas = [o for o in opciones if o[0] == mejor_pos]
    # Entre sentidos de la misma categoria gana el mas general: primero los que
    # no son de un dominio tecnico, luego los de traduccion mas corta. Ordenar
    # por "tener pista" hacia lo contrario, porque en Wiktionary son justo los
    # sentidos especializados los que llevan glosa.
    candidatas.sort(key=lambda o: (o[3], len(o[1][0].split()), opciones.index(o)))
    pos, partes, hint, _ = candidatas[0]
    rank = frecuencia[palabra]
    # Una pista que repite la palabra preguntada no desambigua nada.
    if hint.lower().strip(".") == palabra or palabra in hint.lower().split():
        hint = "-"
    filas[rank] = (rank, palabra, palabra, pos, partes[0],
                   ";".join(partes[1:4]) or "-", nivel(rank), hint)

automaticas = len(filas)

# --- lo revisado a mano manda ----------------------------------------------
revisadas = 0
for f in sorted(glob.glob(str(RAIZ / "tools" / "respuestas" / "lote_*.txt"))):
    for linea in pathlib.Path(f).read_text(encoding="utf-8").splitlines():
        campos = linea.strip().split("|")
        if len(campos) != 8 or not campos[0].isdigit() or campos[3] == "drop":
            continue
        filas[int(campos[0])] = tuple(campos[:1] and [int(campos[0])] + campos[1:])
        revisadas += 1

# --- una forma por lema -----------------------------------------------------
def preferida(a, b):
    for f in (a, b):
        if f[1] == f[2]:
            return f
    return a if a[0] < b[0] else b

por_lema = {}
for f in filas.values():
    clave = (f[2], f[3])
    por_lema[clave] = preferida(por_lema[clave], f) if clave in por_lema else f

EXCLUIR = RAIZ / "tools" / "excluir.txt"
excluidas = set()
if EXCLUIR.exists():
    excluidas = {l.strip().lower() for l in EXCLUIR.read_text(encoding="utf-8").splitlines()
                 if l.strip() and not l.startswith("#")}
final = sorted(f for f in por_lema.values() if f[1] not in excluidas and f[2] not in excluidas)

DESTINO.parent.mkdir(parents=True, exist_ok=True)
with DESTINO.open("w", encoding="utf-8") as fh:
    fh.write("# Diccionario ingles-espanol ordenado por frecuencia de uso.\n")
    fh.write("# Formato: rank|en|lemma|pos|es|es_alt|cefr|hint\n")
    fh.write("# Frecuencia: OpenSubtitles 2018. Traducciones: Wiktionary (CC BY-SA).\n")
    for f in final:
        fh.write("|".join(str(c) for c in f) + "\n")

print(f"de la fuente bilingue  {automaticas}")
print(f"revisadas a mano       {revisadas}")
print(f"tras plegar por lema   {len(final)}")
print(f"escrito {DESTINO.relative_to(RAIZ)}")
niveles = collections.Counter(f[6] for f in final)
print("por nivel:", dict(sorted(niveles.items())))
