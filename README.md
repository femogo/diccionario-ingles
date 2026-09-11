# Vocabulario

Juego de vocabulario inglés para Android. Sale una palabra, eliges entre varias
opciones, y un motor de repetición espaciada decide cuándo vuelve a salir.

Sin cuentas, sin anuncios, sin tandas: el juego no tiene final. Lo único que
sale a la red es la descarga del diccionario, y solo cuando se pulsa el botón.
El progreso nunca abandona el dispositivo.

## Cómo funciona el motor

Cada palabra está en una caja del 1 al 6. Acertar la sube una caja y aleja la
siguiente revisión; fallar la devuelve a la caja 1.

**El espaciado se mide en preguntas respondidas, no en tiempo.** El motor no
sabe qué día es. Una tarde de tres horas y una semana sin abrir la aplicación
son exactamente lo mismo para él: lo único que avanza es el contador de
preguntas.

| Caja | Vuelve a preguntarse dentro de |
|------|--------------------------------|
| 1    | 12 preguntas                   |
| 2    | 50 preguntas                   |
| 3    | 180 preguntas                  |
| 4    | 600 preguntas                  |
| 5    | 2000 preguntas                 |
| 6    | 6000 preguntas                 |

Los números salen de comparar cuatro escalas sobre partidas de cinco mil
preguntas. Más cortos amontonan repasos y dejan al jugador viendo siempre las
mismas palabras: 599 distintas en lugar de 710. Más largos enseñan más
vocabulario pero no llegan a dominar ninguna palabra en toda la partida, y
entonces la barra de nivel no llega nunca a verde.

Es lo contrario de repetir más lo que ya sabes: lo dominado se espacia para
dejar sitio a lo que todavía falla.

El precio de contar en turnos está asumido a conciencia: repasar algo a los
treinta días funciona porque han pasado treinta días, y midiendo en preguntas el
olvido real de una temporada sin jugar no se refleja. A cambio, el juego va al
ritmo de quien juega y no al del calendario.

En modo mixto la dirección de la pregunta cambia con el dominio. Las cajas 1 y 2
preguntan inglés a español, que es reconocer. A partir de la 3 se invierte a
español a inglés, que es producir y cuesta bastante más.

### Los distractores

Es la parte que decide si el juego mide algo. Con opciones al azar se aprueba
por eliminación sin saber la palabra, y el motor acaba registrando dominio donde
no lo hay. Aquí los distractores comparten categoría gramatical con la respuesta
correcta, salen de una banda de frecuencia parecida, y nunca son también válidos
como traducción.

## Qué se pregunta y de dónde sale

No hay sesiones ni cortes por día. El planificador no conoce más fecha que el
`dueAt` de cada palabra, que es cuándo toca repasarla. La cola interna de 40
preguntas se rellena antes de agotarse y nunca se ve terminar; cerrar la
aplicación tampoco corta nada, porque la ventana de aciertos que ajusta la
dificultad se guarda con el resto del progreso.

(La simulación de `RecorridoTest` sí agrupa respuestas por días, pero eso es el
banco de pruebas modelando uso realista, no el comportamiento de la aplicación.)

Cada vez que la cola se rellena, se arma en tres capas: una reserva de palabras
nuevas, los repasos ya vencidos empezando por las cajas bajas, y si sobra sitio,
lo que vence más pronto. Las novedades se intercalan con los repasos en vez de
salir en bloque: diez palabras desconocidas seguidas es donde se abandona.

**El reparto entre niveles no es una tabla fija.** El peso de cada nivel es lo
que le falta por dominar, atenuado por lo abierto que esté —un nivel se abre del
todo cuando el anterior tiene una base—. Así A1 acapara al principio y cede sitio
a A2 y B1 por sí solo, sin números escritos a mano que recalibrar cada vez que
cambia el diccionario. La tasa de acierto de las últimas 50 respuestas mueve el
centro de gravedad: yendo sobrado se mezcla más material difícil, atascándose se
concentra abajo.

**La reserva de novedades cede cuando hay atasco**, y esto costó dos intentos.
Con reserva fija del 35 %, medido en simulación a tres meses: 2053 palabras
vistas en lugar de 670, y **ninguna** en la última caja, frente a 367. Entraban
palabras nuevas más deprisa de lo que se podían repasar y la deuda crecía sin
límite. Ahora la reserva se divide por lo desbordada que esté la capacidad de
repaso y solo conserva un mínimo, para que descubrir nunca se pare del todo.

Con eso, a tres meses: 681 palabras vistas, 317 en la última caja, y cinco
niveles tocados en lugar de dos. Cuesta que A1 tarde más en asentarse —69 % en
vez de 95 %— porque el esfuerzo se reparte. Es el intercambio que se eligió.

## El reintento inmediato

Además del espaciado, lo recién fallado vuelve a la cola entre ocho y dieciséis
preguntas más adelante. Es más corto que el intervalo de la primera caja y sirve
para que fallar tenga consecuencia inmediata sin repetir la palabra a
continuación, que sería contestar de memoria.

Antes de medir el espaciado en turnos esto era imprescindible: con intervalos de
reloj, jugando a tres segundos por palabra, de sesenta palabras falladas solo
diecisiete volvían a aparecer en trescientas preguntas, y la primera en la
pregunta doscientos treinta y dos. Ahora que todo va por turnos sigue siendo
útil, pero ya no tapa un agujero.

## La barra de nivel

Sobre la pregunta hay una escala del marco europeo, un tramo por nivel, que se
colorea de rojo a verde según lo asentado que esté cada uno.

No mide con palabras dominadas o sin dominar, sino con el avance dentro de la
escala de cajas: una palabra en la caja 3 de 6 aporta la mitad. Con el criterio
de todo o nada, alguien con cientos de palabras a medias vería todo en cero
durante semanas, que es justo cuando más falta hace ver que se avanza.

Las palabras del diccionario que no se han visto cuentan como cero: el nivel no
es lo que sabes de lo que has tocado, sino de todo lo que hay. Y no se salta un
nivel por saber palabras sueltas de los de arriba; hace falta la base.

El nivel es una estimación a partir del reparto por frecuencia, no una
certificación.

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
descargable en la pestaña Actions. Se instala en el móvil sin más trámite.

La clave de depuración (`app/debug.keystore`) está en el repositorio a
propósito. Sin ella cada ejecución del CI genera la suya, los APK salen firmados
distinto, y Android rechaza instalar uno encima de otro con "aplicación no
instalada". No es un secreto: la clave de depuración usa la contraseña "android"
por diseño y no da acceso a nada. Publicar en Play Store sí exigiría una clave
de firma propia, esa fuera del repositorio.

## El diccionario

6780 palabras, ordenadas por frecuencia real de uso. Se arman cruzando dos
fuentes con `tools/construir_diccionario.py`:

```bash
./tools/descargar_fuente.sh          # volcado bilingüe, 5,7 MB, no va en el repo
python3 tools/construir_diccionario.py
```

Escribe `app/src/main/assets/words.txt`. Los lotes revisados a mano en
`tools/respuestas/` tienen prioridad sobre lo automático.

El nivel MCER no sale de ninguna fuente: se deduce del puesto en la lista de
frecuencia. Es una aproximación, no una medida.

### Actualizar desde la aplicación

El botón de Ajustes busca las dos cosas que pueden cambiar.

**Las palabras**: descarga `words.txt` del repositorio y lo instala si ha
cambiado, comparando una huella del contenido para no reinstalar lo mismo. El
progreso no se toca: vive en otra tabla y se reenlaza por posición en la lista
de frecuencia.

**La propia aplicación**: consulta la última publicación, y si su etiqueta es
mayor que el `versionCode` instalado, descarga el APK y abre el instalador.

Lo que no puede hacer es instalarse sola. Android reserva la instalación
silenciosa a las aplicaciones del sistema firmadas con la clave de plataforma,
al administrador de una instalación gestionada, o a un móvil con root. No es un
permiso que se pueda pedir, y existe para que una aplicación no instale cosas a
espaldas de quien la usa. Lo que sí hace es dejarlo todo descargado para que
baste un toque, y la primera vez hay que concederle "Instalar apps
desconocidas".

Cada compilación de `main` publica una versión etiquetada con el número de
ejecución, que es también el `versionCode` del APK. Esa es la comparación.

**Requiere que el repositorio sea público.** GitHub sirve los archivos en crudo
y los de las publicaciones de repositorios privados solo con credenciales, y un
token dentro del APK está publicado de hecho: cualquiera puede extraerlo del
archivo instalado.

### Formato

```
rank|en|lemma|pos|es|es_alt|cefr|hint
88|bank|bank|noun|banco|entidad bancaria|A2|financial institution
```

`rank` es la posición en la lista de frecuencia y el motor la usa para elegir
distractores, así que no es solo un identificador. `hint` desambigua las
palabras con varios significados y se muestra bajo la pregunta.

## Origen de las palabras

Frecuencia: OpenSubtitles 2018, de
[hermitdave/FrequencyWords](https://github.com/hermitdave/FrequencyWords),
filtrada a palabras alfabéticas. Refleja inglés hablado, que es lo que interesa
para aprender, en vez de frecuencia de corpus web.

Traducciones: volcado de Wiktionary EN-ES, **CC BY-SA**. Eso obliga a atribuir y
a mantener la misma licencia si el diccionario se redistribuye.

Las traducciones **no están revisadas una a una**. Para uso personal va sobrado;
antes de publicar la app habría que repasarlas.
