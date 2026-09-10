# Vocabulario

Juego de vocabulario inglés para Android. Sale una palabra, eliges entre varias
opciones, y un motor de repetición espaciada decide cuándo vuelve a salir.

Sin red, sin cuentas, sin anuncios. Todo vive en el dispositivo.

## Cómo funciona el motor

Cada palabra está en una caja del 1 al 6. Acertar la sube una caja y aleja la
siguiente revisión; fallar la devuelve a la caja 1.

| Caja | Vuelve a preguntarse en |
|------|-------------------------|
| 1    | 10 minutos              |
| 2    | 1 día                   |
| 3    | 3 días                  |
| 4    | 1 semana                |
| 5    | 3 semanas               |
| 6    | 2 meses                 |

Es lo contrario de repetir más lo que ya sabes: lo dominado se espacia para
dejar sitio a lo que todavía falla.

En modo mixto la dirección de la pregunta cambia con el dominio. Las cajas 1 y 2
preguntan inglés a español, que es reconocer. A partir de la 3 se invierte a
español a inglés, que es producir y cuesta bastante más.

### Los distractores

Es la parte que decide si el juego mide algo. Con opciones al azar se aprueba
por eliminación sin saber la palabra, y el motor acaba registrando dominio donde
no lo hay. Aquí los distractores comparten categoría gramatical con la respuesta
correcta, salen de una banda de frecuencia parecida, y nunca son también válidos
como traducción.

## Estructura

```
engine/   Kotlin puro: cajas, calendario de repaso, generación de preguntas.
          No depende de Android, así que se prueba en cualquier máquina.
app/      Interfaz en Jetpack Compose y persistencia con Room.
tools/    Lo necesario para generar y validar el diccionario.
```

La separación no es decorativa: toda la lógica que importa está en `engine` y
tiene pruebas. La capa de Android solo pinta y guarda.

## Compilar

```bash
./gradlew :engine:test        # pruebas del motor, sin SDK de Android
./gradlew :app:assembleDebug  # APK en app/build/outputs/apk/debug/
```

Cada push a `main` compila el APK en GitHub Actions y lo deja como artefacto
descargable en la pestaña Actions. Va firmado con la clave de depuración, así
que se instala en el móvil sin más trámite.

## El diccionario

La app trae de serie las palabras más frecuentes del inglés, suficientes para
empezar. Para ampliarlo:

1. Abre un chat nuevo en ChatGPT y pega `tools/prompt_chatgpt.txt`.
2. Pégale los lotes de `tools/lotes/` uno a uno. Cada uno son 200 palabras
   ordenadas por frecuencia real: `lote_000` son las 200 más usadas del inglés.
3. Guarda cada respuesta en un archivo dentro de `respuestas/`.
4. Valida y arma el archivo final:

   ```bash
   python3 tools/validar.py respuestas/*.txt
   ```

   Escribe `words.txt` y avisa de líneas rotas, ids que faltan y traducciones
   ambiguas. Por defecto se queda con **una sola forma por lema**: la lista de
   frecuencia cuenta formas, no lemas, así que sin ese paso `is`, `was`, `are`,
   `were`, `been` y `am` entran las seis traducidas por "ser" y el modo
   español-inglés pasa a tener seis respuestas válidas. Con `--con-flexiones` se
   conservan todas.
5. Pasa `words.txt` al móvil e impórtalo desde Ajustes.

Importar sustituye el diccionario pero **no** borra el progreso: viven en tablas
distintas y se enlazan por posición en la lista de frecuencia.

Hay 70 lotes, 14000 palabras. Se descarta bastante por el camino: en el primer
lote, de 200 palabras quedaron 159 tras quitar nombres propios, interjecciones,
restos de la tokenización de subtítulos (`don`, `didn`, `isn`) y plegar las
formas flexionadas en su lema. No hace falta hacerlos todos de golpe: con 10
lotes hay vocabulario para meses.

`tools/respuestas/lote_000.txt` es la respuesta ya revisada del primer lote,
como referencia de lo que debe salir.

### Formato

```
rank|en|lemma|pos|es|es_alt|cefr|hint
88|bank|bank|noun|banco|entidad bancaria|A2|financial institution
```

`rank` es la posición en la lista de frecuencia y el motor la usa para elegir
distractores, así que no es solo un identificador. `hint` desambigua las
palabras con varios significados y se muestra bajo la pregunta.

Se usa tubería en vez de coma porque las traducciones llevan comas a menudo y
las comillas de CSV se pierden al copiar y pegar.

## Origen de las palabras

Lista de frecuencia de OpenSubtitles 2018, de
[hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords),
filtrada a palabras alfabéticas. Refleja inglés hablado, que es lo que interesa
para aprender, en vez de frecuencia de corpus web.

Las traducciones se generan con un modelo de lenguaje y **no están revisadas una
a una**. Para uso personal va sobrado; antes de publicar la app habría que
repasarlas y comprobar la licencia de la lista de origen.
