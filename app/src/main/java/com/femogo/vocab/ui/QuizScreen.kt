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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.femogo.vocab.engine.Question
import com.femogo.vocab.ui.theme.EstiloPalabra
import com.femogo.vocab.ui.theme.coloresRespuesta

@Composable
fun QuizScreen(
    state: QuizUiState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Un solo punto de corte: por encima de 600dp de ancho hay sitio para dos
    // columnas de opciones y para una tipografía mayor. Debajo, una columna.
    BoxWithConstraints(modifier.fillMaxSize()) {
        val ancho = maxWidth
        val amplio = ancho >= 600.dp
        val medida = Medidas(
            palabra = if (amplio) 64.sp else 42.sp,
            opcion = if (amplio) 22.sp else 18.sp,
            alturaOpcion = if (amplio) 76.dp else 64.dp,
            margen = if (amplio) 32.dp else 20.dp,
            columnas = if (amplio) 2 else 1
        )
        val question = state.question

        when {
            state.loading -> Centrado { CircularProgressIndicator() }
            state.sinDiccionario -> Centrado { SinDiccionario() }
            question == null -> Centrado { CircularProgressIndicator() }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = medida.margen, vertical = 12.dp)
                    .widthIn(max = 760.dp)
                    .align(Alignment.TopCenter),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Marcador(state)
                Spacer(Modifier.height(if (amplio) 56.dp else 32.dp))
                Enunciado(question, medida)
                Spacer(Modifier.height(if (amplio) 56.dp else 36.dp))
                Opciones(state, question, medida, onAnswer)
                if (state.answered) {
                    Spacer(Modifier.height(20.dp))
                    Explicacion(question, state.wasCorrect)
                    Spacer(Modifier.height(20.dp))
                    BotonSiguiente(onNext, medida)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

private data class Medidas(
    val palabra: androidx.compose.ui.unit.TextUnit,
    val opcion: androidx.compose.ui.unit.TextUnit,
    val alturaOpcion: Dp,
    val margen: Dp,
    val columnas: Int
)

@Composable
private fun Marcador(state: QuizUiState) {
    val porcentaje = if (state.respondidas == 0) 0
    else state.aciertos * 100 / state.respondidas
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${state.respondidas} respondidas",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (state.respondidas > 0) {
            Text(
                "$porcentaje % acierto",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Sin encabezado que explique qué hay que hacer: la palabra sola y las opciones
 * debajo ya lo dicen, y repetirlo en cada pregunta solo roba espacio.
 */
@Composable
private fun Enunciado(question: Question, medida: Medidas) {
    Text(
        text = question.prompt.uppercase(),
        style = EstiloPalabra,
        fontSize = medida.palabra,
        color = MaterialTheme.colorScheme.onBackground,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    question.hint?.let {
        Spacer(Modifier.height(10.dp))
        Text(
            text = it,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun Opciones(
    state: QuizUiState,
    question: Question,
    medida: Medidas,
    onAnswer: (Int) -> Unit
) {
    val indexadas = question.options.withIndex().toList()
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        indexadas.chunked(medida.columnas).forEach { fila ->
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
    val colores = coloresRespuesta()
    val esquema = MaterialTheme.colorScheme

    val fondo by animateColorAsState(
        when (estado) {
            EstadoOpcion.CORRECTA -> colores.fondoAcierto
            EstadoOpcion.ELEGIDA_MAL -> colores.fondoFallo
            EstadoOpcion.APAGADA -> esquema.surfaceVariant.copy(alpha = 0.4f)
            EstadoOpcion.NEUTRA -> esquema.surface
        },
        label = "fondo"
    )
    val borde by animateColorAsState(
        when (estado) {
            EstadoOpcion.CORRECTA -> colores.acierto
            EstadoOpcion.ELEGIDA_MAL -> colores.fallo
            EstadoOpcion.APAGADA -> Color.Transparent
            EstadoOpcion.NEUTRA -> esquema.outline
        },
        label = "borde"
    )
    val escala by animateFloatAsState(
        if (estado == EstadoOpcion.CORRECTA) 1.02f else 1f,
        label = "escala"
    )
    val texto2 = when (estado) {
        EstadoOpcion.CORRECTA -> colores.acierto
        EstadoOpcion.ELEGIDA_MAL -> colores.fallo
        EstadoOpcion.APAGADA -> esquema.onSurfaceVariant.copy(alpha = 0.6f)
        EstadoOpcion.NEUTRA -> esquema.onSurface
    }

    Box(
        modifier = modifier
            .scale(escala)
            .heightIn(min = medida.alturaOpcion)
            .clip(RoundedCornerShape(20.dp))
            .background(fondo)
            .border(BorderStroke(if (estado == EstadoOpcion.NEUTRA) 1.dp else 2.dp, borde), RoundedCornerShape(20.dp))
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = texto,
            fontSize = medida.opcion,
            fontWeight = if (estado == EstadoOpcion.CORRECTA) FontWeight.SemiBold else FontWeight.Normal,
            color = texto2,
            textAlign = TextAlign.Center
        )
    }
}

/** Tras responder se muestran las demás traducciones válidas, que es donde se aprende. */
@Composable
private fun Explicacion(question: Question, acierto: Boolean) {
    val colores = coloresRespuesta()
    val palabra = question.word
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (acierto) "Correcto" else "Era: ${question.correctOption}",
            color = if (acierto) colores.acierto else colores.fallo,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "${palabra.en} · ${palabra.pos.name.lowercase()} · ${palabra.cefr}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (palabra.esAlt.isNotEmpty()) {
            Text(
                text = "También: ${palabra.esAlt.joinToString(", ")}",
                style = MaterialTheme.typography.bodySmall,
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
        shape = RoundedCornerShape(20.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
    ) {
        Text("Siguiente", fontSize = medida.opcion, fontWeight = FontWeight.SemiBold)
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
