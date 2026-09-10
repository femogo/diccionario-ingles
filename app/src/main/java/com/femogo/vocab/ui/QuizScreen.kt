package com.femogo.vocab.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.femogo.vocab.engine.NivelProgreso
import com.femogo.vocab.engine.Question
import com.femogo.vocab.ui.theme.Acierto
import com.femogo.vocab.ui.theme.AciertoFondo
import com.femogo.vocab.ui.theme.EstiloPalabra
import com.femogo.vocab.ui.theme.Fallo
import com.femogo.vocab.ui.theme.FalloFondo
import com.femogo.vocab.ui.theme.colorDeDominio

@Composable
fun QuizScreen(
    state: QuizUiState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val medida = medidasPara(maxWidth, maxHeight)
        val question = state.question

        when {
            state.loading -> Centrado { CircularProgressIndicator() }
            state.sinDiccionario -> Centrado { SinDiccionario() }
            question == null -> Centrado { CircularProgressIndicator() }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 900.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = medida.margen, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                BarraNivel(state.niveles, state.nivelAlcanzado.name, medida)

                // La palabra se queda con todo el alto sobrante en vez de
                // amontonarse arriba: en una tablet en vertical eso era media
                // pantalla vacía.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Enunciado(question, medida)
                }

                Opciones(state, question, medida, onAnswer)

                if (state.answered) {
                    Spacer(Modifier.height(16.dp))
                    Explicacion(question, state.wasCorrect, medida)
                    Spacer(Modifier.height(14.dp))
                    BotonSiguiente(onNext, medida)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private data class Medidas(
    val palabra: TextUnit,
    val opcion: TextUnit,
    val pista: TextUnit,
    val explicacion: TextUnit,
    val alturaOpcion: Dp,
    val margen: Dp,
    val columnas: Int
)

/**
 * El tamaño sale de la altura disponible, no solo del ancho. Un único punto de
 * corte por ancho dejaba la tablet en vertical con la mitad de la pantalla en
 * blanco y la letra de un móvil.
 */
private fun medidasPara(ancho: Dp, alto: Dp): Medidas {
    val columnas = if (ancho >= 600.dp) 2 else 1
    return when {
        alto >= 1000.dp -> Medidas(96.sp, 26.sp, 20.sp, 17.sp, 92.dp, 36.dp, columnas)
        alto >= 800.dp -> Medidas(76.sp, 24.sp, 18.sp, 16.sp, 80.dp, 30.dp, columnas)
        alto >= 620.dp -> Medidas(56.sp, 20.sp, 16.sp, 14.sp, 68.dp, 22.dp, columnas)
        else -> Medidas(40.sp, 17.sp, 14.sp, 13.sp, 54.dp, 18.dp, columnas)
    }
}

/**
 * Escala del marco europeo, un tramo por nivel, coloreada según lo asentado que
 * esté cada uno: rojo sin tocar, ámbar a medias, verde dominado.
 */
@Composable
private fun BarraNivel(niveles: List<NivelProgreso>, alcanzado: String, medida: Medidas) {
    if (niveles.isEmpty()) {
        Spacer(Modifier.height(medida.alturaOpcion))
        return
    }
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = "NIVEL $alcanzado",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (medida.columnas == 2) 14.dp else 11.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            niveles.forEach { nivel ->
                val color by animateColorAsState(colorDeDominio(nivel.dominio), label = "tramo")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(nivel.dominio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .background(color)
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            niveles.forEach { nivel ->
                val esActual = nivel.cefr.name == alcanzado
                Text(
                    text = nivel.cefr.name,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 11.sp,
                    fontWeight = if (esActual) FontWeight.Black else FontWeight.Bold,
                    color = if (esActual) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun Enunciado(question: Question, medida: Medidas) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = question.prompt.uppercase(),
            style = EstiloPalabra,
            fontSize = medida.palabra,
            lineHeight = medida.palabra,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        question.hint?.let {
            Spacer(Modifier.height(14.dp))
            Text(
                text = it,
                fontSize = medida.pista,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun Opciones(
    state: QuizUiState,
    question: Question,
    medida: Medidas,
    onAnswer: (Int) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        question.options.withIndex().toList().chunked(medida.columnas).forEach { fila ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                fila.forEach { (i, texto) ->
                    Opcion(
                        texto = texto,
                        estado = estadoDe(i, state, question),
                        habilitada = !state.answered,
                        medida = medida,
                        onClick = { onAnswer(i) },
                        modifier = Modifier.weight(1f)
                    )
                }
                // Rellena el hueco cuando el número de opciones es impar.
                repeat(medida.columnas - fila.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private enum class EstadoOpcion { NEUTRA, CORRECTA, ELEGIDA_MAL, APAGADA }

private fun estadoDe(i: Int, state: QuizUiState, question: Question): EstadoOpcion = when {
    !state.answered -> EstadoOpcion.NEUTRA
    i == question.correctIndex -> EstadoOpcion.CORRECTA
    i == state.chosenIndex -> EstadoOpcion.ELEGIDA_MAL
    else -> EstadoOpcion.APAGADA
}

@Composable
private fun Opcion(
    texto: String,
    estado: EstadoOpcion,
    habilitada: Boolean,
    medida: Medidas,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val esquema = MaterialTheme.colorScheme
    val fondo by animateColorAsState(
        when (estado) {
            EstadoOpcion.CORRECTA -> AciertoFondo
            EstadoOpcion.ELEGIDA_MAL -> FalloFondo
            EstadoOpcion.APAGADA -> esquema.surface.copy(alpha = 0.5f)
            EstadoOpcion.NEUTRA -> esquema.surface
        },
        label = "fondo"
    )
    val borde by animateColorAsState(
        when (estado) {
            EstadoOpcion.CORRECTA -> Acierto
            EstadoOpcion.ELEGIDA_MAL -> Fallo
            EstadoOpcion.APAGADA -> Color.Transparent
            EstadoOpcion.NEUTRA -> esquema.outline
        },
        label = "borde"
    )
    val escala by animateFloatAsState(
        if (estado == EstadoOpcion.CORRECTA) 1.03f else 1f,
        label = "escala"
    )
    val color = when (estado) {
        EstadoOpcion.CORRECTA -> Acierto
        EstadoOpcion.ELEGIDA_MAL -> Fallo
        EstadoOpcion.APAGADA -> esquema.onSurfaceVariant.copy(alpha = 0.45f)
        EstadoOpcion.NEUTRA -> esquema.onSurface
    }
    val forma = RoundedCornerShape(22.dp)

    Box(
        modifier = modifier
            .scale(escala)
            .heightIn(min = medida.alturaOpcion)
            .clip(forma)
            .background(fondo)
            .border(BorderStroke(if (estado == EstadoOpcion.NEUTRA) 1.5.dp else 2.5.dp, borde), forma)
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            fontSize = medida.opcion,
            fontWeight = FontWeight.Bold,
            color = color,
            textAlign = TextAlign.Center
        )
    }
}

/** Tras responder se muestran las demás traducciones válidas, que es donde se aprende. */
@Composable
private fun Explicacion(question: Question, acierto: Boolean, medida: Medidas) {
    val palabra = question.word
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (acierto) "Correcto" else "Era: ${question.correctOption}",
            color = if (acierto) Acierto else Fallo,
            fontSize = medida.explicacion,
            fontWeight = FontWeight.ExtraBold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${palabra.en} · ${palabra.pos.name.lowercase()} · ${palabra.cefr}",
            fontSize = medida.explicacion,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (palabra.esAlt.isNotEmpty()) {
            Text(
                text = "También: ${palabra.esAlt.joinToString(", ")}",
                fontSize = medida.explicacion,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BotonSiguiente(onNext: () -> Unit, medida: Medidas) {
    Button(
        onClick = onNext,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = medida.alturaOpcion),
        shape = RoundedCornerShape(22.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text("Siguiente", fontSize = medida.opcion, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun SinDiccionario() {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("No hay diccionario", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Ve a Ajustes y actualiza el diccionario.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun Centrado(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}
