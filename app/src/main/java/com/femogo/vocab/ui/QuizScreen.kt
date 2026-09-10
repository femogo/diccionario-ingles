package com.femogo.vocab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.femogo.vocab.engine.Direction
import com.femogo.vocab.engine.Question
import com.femogo.vocab.ui.theme.Acierto
import com.femogo.vocab.ui.theme.AciertoSuave
import com.femogo.vocab.ui.theme.Fallo
import com.femogo.vocab.ui.theme.FalloSuave

@Composable
fun QuizScreen(
    state: QuizUiState,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier.fillMaxSize()) {
        when {
            state.loading -> Centrado { CircularProgressIndicator() }
            state.finished -> ResumenSesion(state, onRestart)
            state.empty -> SinNadaQueRepasar(state, onRestart)
            state.question != null -> Pregunta(state, state.question, onAnswer, onNext)
        }
    }
}

@Composable
private fun Pregunta(
    state: QuizUiState,
    question: Question,
    onAnswer: (Int) -> Unit,
    onNext: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        LinearProgressIndicator(
            progress = { if (state.total == 0) 0f else state.position.toFloat() / state.total },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "${state.position} de ${state.total}   ·   ${state.correctSoFar} aciertos",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(40.dp))

        Text(
            text = if (question.direction == Direction.EN_TO_ES) "¿Qué significa?" else "¿Cómo se dice en inglés?",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = question.prompt,
            fontSize = 40.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
        // La pista solo aparece cuando la palabra es ambigua; sirve para que
        // "bank" no se pregunte a ciegas entre dos significados distintos.
        question.hint?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(40.dp))

        question.options.forEachIndexed { i, texto ->
            Opcion(
                texto = texto,
                estado = estadoDe(i, state, question),
                habilitada = !state.answered,
                onClick = { onAnswer(i) }
            )
            Spacer(Modifier.height(12.dp))
        }

        if (state.answered) {
            Spacer(Modifier.height(8.dp))
            Explicacion(question, state.wasCorrect)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) {
                Text(if (state.position >= state.total) "Terminar" else "Siguiente")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

private enum class EstadoOpcion { NEUTRA, CORRECTA, ELEGIDA_MAL }

private fun estadoDe(i: Int, state: QuizUiState, question: Question): EstadoOpcion = when {
    !state.answered -> EstadoOpcion.NEUTRA
    i == question.correctIndex -> EstadoOpcion.CORRECTA
    i == state.chosenIndex -> EstadoOpcion.ELEGIDA_MAL
    else -> EstadoOpcion.NEUTRA
}

@Composable
private fun Opcion(
    texto: String,
    estado: EstadoOpcion,
    habilitada: Boolean,
    onClick: () -> Unit
) {
    val fondo = when (estado) {
        EstadoOpcion.CORRECTA -> AciertoSuave
        EstadoOpcion.ELEGIDA_MAL -> FalloSuave
        EstadoOpcion.NEUTRA -> MaterialTheme.colorScheme.surfaceVariant
    }
    val borde = when (estado) {
        EstadoOpcion.CORRECTA -> Acierto
        EstadoOpcion.ELEGIDA_MAL -> Fallo
        EstadoOpcion.NEUTRA -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(fondo)
            .border(2.dp, borde, RoundedCornerShape(14.dp))
            .clickable(enabled = habilitada, onClick = onClick)
            .padding(vertical = 18.dp, horizontal = 16.dp)
    ) {
        Text(
            text = texto,
            fontSize = 18.sp,
            color = if (estado == EstadoOpcion.NEUTRA) MaterialTheme.colorScheme.onSurface else Color.Black,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

/** Tras responder se muestran las demás traducciones válidas, que es donde se aprende. */
@Composable
private fun Explicacion(question: Question, acierto: Boolean) {
    val palabra = question.word
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = if (acierto) "Correcto" else "Era: ${question.correctOption}",
            color = if (acierto) Acierto else Fallo,
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
private fun ResumenSesion(state: QuizUiState, onRestart: () -> Unit) {
    Centrado {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Sesión terminada", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))
            Text(
                "${state.correctSoFar} de ${state.total} aciertos",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRestart) { Text("Otra ronda") }
        }
    }
}

@Composable
private fun SinNadaQueRepasar(state: QuizUiState, onRestart: () -> Unit) {
    Centrado {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Nada pendiente por ahora", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.catalogSize == 0)
                    "El diccionario está vacío. Impórtalo desde Ajustes."
                else
                    "Has dado todas las palabras nuevas de hoy. Vuelve más tarde o sube el tope en Ajustes.",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRestart) { Text("Comprobar de nuevo") }
        }
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
