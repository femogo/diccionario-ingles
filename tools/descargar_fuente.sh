#!/bin/bash
# Trae el volcado bilingue de Wiktionary que usa construir_diccionario.py.
set -e
destino="$(dirname "$0")/fuente"
mkdir -p "$destino"
curl -L --fail -o "$destino/en-es.xml" \
  "https://raw.githubusercontent.com/mananoreboton/en-es-en-Dic/master/src/main/resources/dic/en-es.xml"
echo "descargado en $destino/en-es.xml"
