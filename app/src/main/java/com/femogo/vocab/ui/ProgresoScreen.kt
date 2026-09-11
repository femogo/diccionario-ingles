package com.femogo.vocab.ui

import android.app.Application
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
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
import com.femogo.vocab.engine.ProgresoNivel
import com.femogo.vocab.ui.theme.colorDeDominio
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Lo que lleva hecho un módulo. */
data class ProgresoModulo(
    val id: String,
    val nombre: String,
    val catalogo: Int,
    val empezadas: Int,
    val dominadas: Int,
    val aciertos: Int,
    val respuestas: Int,
    val porCaja: List<Int>,
    val porNivel: List<Pair<String, Float>>
) {
    val precision: Int get() = if (respuestas == 0) 0 else (aciertos * 100.0 / respuestas).roundToInt()
}

class ProgresoViewModel(app: Application) : AndroidViewModel(app) {
    private val biblioteca = (app as VocabApplication).biblioteca
    private val leitner = Leitner()
    private val progresoNivel = ProgresoNivel(leitner)

    private val _state = MutableStateFlow<List<ProgresoModulo>>(emptyList())
    val state: StateFlow<List<ProgresoModulo>> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            biblioteca.prepararSiHaceFalta()
            _state.value = biblioteca.modulos().map { modulo ->
                val catalogo = biblioteca.catalogo(modulo.id)
                val todas = biblioteca.cards(modulo.id)
                val vistas = todas.values.filter { !it.isNew }
                ProgresoModulo(
                    id = modulo.id,
                    nombre = modulo.nombre,
                    catalogo = catalogo.size,
                    empezadas = vistas.size,
                    dominadas = vistas.count { leitner.isMastered(it) },
                    aciertos = vistas.sumOf { it.correct },
                    respuestas = vistas.sumOf { it.seen },
                    porCaja = (1..leitner.boxCount).map { caja -> vistas.count { it.box == caja } },
                    porNivel = progresoNivel.porNivel(catalogo, todas)
                        .filter { it.total > 0 }
                        .map { it.cefr.name to it.dominio }
                )
            }
        }
    }
}

@Composable
fun ProgresoScreen(modulos: List<ProgresoModulo>, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val amplio = maxWidth >= 600.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (amplio) 32.dp else 20.dp, vertical = 20.dp)
                .widthIn(max = 720.dp)
                .align(Alignment.TopCenter)
        ) {
            Text("Progreso", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(20.dp))

            modulos.forEachIndexed { i, modulo ->
                if (i > 0) {
                    Spacer(Modifier.height(28.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(28.dp))
                }
                BloqueModulo(modulo)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BloqueModulo(modulo: ProgresoModulo) {
    Text(modulo.nombre, style = MaterialTheme.typography.titleLarge)
    Spacer(Modifier.height(4.dp))
    Text(
        "${modulo.catalogo} palabras",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Dato("Empezadas", "${modulo.empezadas}", Modifier.weight(1f))
        Dato("Dominadas", "${modulo.dominadas}", Modifier.weight(1f))
        Dato("Precisión", "${modulo.precision} %", Modifier.weight(1f))
    }

    if (modulo.porNivel.isNotEmpty()) {
        Spacer(Modifier.height(18.dp))
        Text("Nivel", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        modulo.porNivel.forEach { (nombre, dominio) ->
            Barra(nombre, dominio, colorPorDominio = true)
            Spacer(Modifier.height(6.dp))
        }
    }

    Spacer(Modifier.height(18.dp))
    Text("Reparto por caja", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))
    Text(
        "La caja 1 son las palabras que se acaban de fallar o empezar. Cada " +
            "acierto sube una caja y aleja la siguiente revisión.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(10.dp))
    val maximo = (modulo.porCaja.maxOrNull() ?: 0).coerceAtLeast(1)
    modulo.porCaja.forEachIndexed { i, cantidad ->
        Barra("Caja ${i + 1}", cantidad.toFloat() / maximo, etiquetaDerecha = "$cantidad")
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun Dato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(14.dp)
    ) {
        Text(valor, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text(
            etiqueta,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun Barra(
    etiqueta: String,
    proporcion: Float,
    etiquetaDerecha: String? = null,
    colorPorDominio: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(64.dp)
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(20.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (proporcion > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(proporcion.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(
                            if (colorPorDominio) colorDeDominio(proporcion)
                            else MaterialTheme.colorScheme.primary
                        )
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            etiquetaDerecha ?: "${(proporcion * 100).roundToInt()} %",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(48.dp)
        )
    }
}
