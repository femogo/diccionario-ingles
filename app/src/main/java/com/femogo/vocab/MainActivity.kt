package com.femogo.vocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.femogo.vocab.ui.AjustesScreen
import com.femogo.vocab.ui.AjustesViewModel
import com.femogo.vocab.ui.ProgresoScreen
import com.femogo.vocab.ui.ProgresoViewModel
import com.femogo.vocab.ui.QuizScreen
import com.femogo.vocab.ui.QuizViewModel
import com.femogo.vocab.ui.theme.VocabTheme

class MainActivity : ComponentActivity() {

    private val quizVm: QuizViewModel by viewModels()
    private val progresoVm: ProgresoViewModel by viewModels()
    private val ajustesVm: AjustesViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VocabTheme {
                App(quizVm, progresoVm, ajustesVm)
            }
        }
    }
}

private enum class Seccion(val etiqueta: String, val icono: ImageVector) {
    JUGAR("Jugar", Icons.Filled.School),
    PROGRESO("Progreso", Icons.Filled.BarChart),
    AJUSTES("Ajustes", Icons.Filled.Settings)
}

@Composable
private fun App(
    quizVm: QuizViewModel,
    progresoVm: ProgresoViewModel,
    ajustesVm: AjustesViewModel
) {
    // Tres pantallas fijas no justifican un grafo de navegación.
    var seccion by remember { mutableStateOf(Seccion.JUGAR) }
    val ajustes by ajustesVm.settings.collectAsState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                Seccion.entries.forEach { destino ->
                    NavigationBarItem(
                        selected = seccion == destino,
                        onClick = { seccion = destino },
                        icon = { Icon(destino.icono, contentDescription = destino.etiqueta) },
                        label = { Text(destino.etiqueta) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
            }
        }
    ) { padding ->
        val quizState by quizVm.state.collectAsState()
        val progresoState by progresoVm.state.collectAsState()

        // El progreso se recalcula al entrar en la pestaña, no en cada respuesta.
        LaunchedEffect(seccion) {
            if (seccion == Seccion.PROGRESO) progresoVm.refresh()
        }
        // Cambiar el número de opciones se nota en la siguiente pregunta.
        LaunchedEffect(ajustes.optionCount) {
            quizVm.aplicarNumeroDeOpciones(ajustes.optionCount)
        }

        when (seccion) {
            Seccion.JUGAR -> QuizScreen(
                state = quizState,
                onAnswer = quizVm::answer,
                onNext = quizVm::next,
                onModulo = quizVm::cambiarModulo,
                modifier = Modifier.padding(padding)
            )
            Seccion.PROGRESO -> ProgresoScreen(progresoState, Modifier.padding(padding))
            Seccion.AJUSTES -> AjustesScreen(
                vm = ajustesVm,
                onDiccionarioActualizado = quizVm::recargar,
                modifier = Modifier.padding(padding)
            )
        }
    }
}
