package com.femogo.vocab.ui

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.femogo.vocab.VocabApplication
import com.femogo.vocab.engine.Leitner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class ProgresoUiState(
    val catalogo: Int = 0,
    val empezadas: Int = 0,
    val dominadas: Int = 0,
    val vencidas: Int = 0,
    val aciertos: Int = 0,
    val respuestas: Int = 0,
    /** Cuántas palabras hay en cada caja, de la 1 a la última. */
    val porCaja: List<Int> = emptyList()
) {
    val precision: Int get() = if (respuestas == 0) 0 else (aciertos * 100.0 / respuestas).roundToInt()
}

class ProgresoViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as VocabApplication).repository
    private val leitner = Leitner()

    private val _state = MutableStateFlow(ProgresoUiState())
    val state: StateFlow<ProgresoUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            repo.ensureSeeded()
            val catalogo = repo.catalog()
            val cards = repo.cards().values.filter { !it.isNew }
            val now = System.currentTimeMillis()

            _state.value = ProgresoUiState(
                catalogo = catalogo.size,
                empezadas = cards.size,
                dominadas = cards.count { leitner.isMastered(it) },
                vencidas = cards.count { it.dueAt <= now },
                aciertos = cards.sumOf { it.correct },
                respuestas = cards.sumOf { it.seen },
                porCaja = (1..leitner.boxCount).map { caja -> cards.count { it.box == caja } }
            )
        }
    }
}

@Composable
fun ProgresoScreen(state: ProgresoUiState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Progreso", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dato("Empezadas", "${state.empezadas}", Modifier.weight(1f))
            Dato("Dominadas", "${state.dominadas}", Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Dato("Pendientes", "${state.vencidas}", Modifier.weight(1f))
            Dato("Precisión", "${state.precision}%", Modifier.weight(1f))
        }

        Spacer(Modifier.height(28.dp))
        Text("Reparto por caja", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "La caja 1 son las palabras que se acaban de fallar o empezar. " +
                "Cada acierto sube una caja y aleja la siguiente revisión.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        val maximo = (state.porCaja.maxOrNull() ?: 0).coerceAtLeast(1)
        state.porCaja.forEachIndexed { i, cantidad ->
            Barra(etiqueta = "Caja ${i + 1}", valor = cantidad, maximo = maximo)
            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "${state.catalogo} palabras en el diccionario",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp)
    ) {
        Text(valor, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Barra(etiqueta: String, valor: Int, maximo: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(etiqueta, style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(64.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (valor > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(valor.toFloat() / maximo)
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text("$valor", style = MaterialTheme.typography.bodySmall, modifier = Modifier.width(44.dp))
    }
}
