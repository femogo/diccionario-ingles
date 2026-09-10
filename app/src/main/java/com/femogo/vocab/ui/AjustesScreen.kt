package com.femogo.vocab.ui

import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.femogo.vocab.VocabApplication
import com.femogo.vocab.data.Settings
import com.femogo.vocab.data.VocabRepository
import com.femogo.vocab.engine.DirectionMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as VocabApplication).settings
    private val repo = (app as VocabApplication).repository

    val settings: StateFlow<Settings> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    fun setDirection(mode: DirectionMode) = viewModelScope.launch { store.setDirection(mode) }
    fun setNewPerDay(v: Int) = viewModelScope.launch { store.setNewPerDay(v) }
    fun setSessionSize(v: Int) = viewModelScope.launch { store.setSessionSize(v) }
    fun setOptionCount(v: Int) = viewModelScope.launch { store.setOptionCount(v) }

    fun importar(uri: Uri) {
        viewModelScope.launch {
            val resolver = getApplication<Application>().contentResolver
            val reporte = runCatching {
                resolver.openInputStream(uri)?.use { repo.importInto(it) }
            }.getOrNull()

            _mensaje.value = when {
                reporte == null -> "No se ha podido leer el archivo"
                reporte.imported == 0 -> "Ninguna línea válida. ¿Es el formato rank|en|lemma|pos|es|es_alt|cefr|hint?"
                else -> "Importadas ${reporte.imported} palabras" +
                    if (reporte.skipped > 0) ", ${reporte.skipped} líneas descartadas" else ""
            }
        }
    }

    fun reiniciarProgreso() {
        viewModelScope.launch {
            repo.resetProgress()
            _mensaje.value = "Progreso borrado"
        }
    }

    fun limpiarMensaje() { _mensaje.value = null }
}

@Composable
fun AjustesScreen(vm: AjustesViewModel, modifier: Modifier = Modifier) {
    val settings by vm.settings.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    var confirmarReinicio by remember { mutableStateOf(false) }

    val abrirArchivo = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let(vm::importar) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))

        Text("Cómo se pregunta", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModoChip("Mixto", DirectionMode.PROGRESSIVE, settings.directionMode, vm::setDirection)
            ModoChip("EN→ES", DirectionMode.EN_TO_ES, settings.directionMode, vm::setDirection)
            ModoChip("ES→EN", DirectionMode.ES_TO_EN, settings.directionMode, vm::setDirection)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "En modo mixto las palabras nuevas se preguntan de inglés a español, " +
                "y al asentarse pasan a preguntarse al revés, que cuesta más.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))
        Ajuste(
            titulo = "Palabras nuevas al día",
            valor = settings.newPerDay,
            rango = 0f..60f,
            pasos = 11,
            onChange = vm::setNewPerDay
        )
        Ajuste(
            titulo = "Preguntas por sesión",
            valor = settings.sessionSize,
            rango = 5f..60f,
            pasos = 10,
            onChange = vm::setSessionSize
        )
        Ajuste(
            titulo = "Opciones por pregunta",
            valor = settings.optionCount,
            rango = 2f..6f,
            pasos = 3,
            onChange = vm::setOptionCount
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(24.dp))

        Text("Diccionario", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Importa un archivo de texto con una palabra por línea en el formato " +
                "rank|en|lemma|pos|es|es_alt|cefr|hint. Sustituye el diccionario " +
                "actual pero no borra lo que ya has aprendido.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { abrirArchivo.launch("*/*") },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Importar diccionario") }

        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = { confirmarReinicio = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Borrar mi progreso") }

        Spacer(Modifier.height(32.dp))
    }

    mensaje?.let {
        AlertDialog(
            onDismissRequest = vm::limpiarMensaje,
            confirmButton = { TextButton(onClick = vm::limpiarMensaje) { Text("Vale") } },
            text = { Text(it) }
        )
    }

    if (confirmarReinicio) {
        AlertDialog(
            onDismissRequest = { confirmarReinicio = false },
            title = { Text("¿Borrar el progreso?") },
            text = { Text("Se pierden las cajas y las estadísticas de todas las palabras. No se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmarReinicio = false
                    vm.reiniciarProgreso()
                }) { Text("Borrar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmarReinicio = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun ModoChip(
    etiqueta: String,
    modo: DirectionMode,
    actual: DirectionMode,
    onSelect: (DirectionMode) -> Unit
) {
    FilterChip(
        selected = modo == actual,
        onClick = { onSelect(modo) },
        label = { Text(etiqueta) }
    )
}

@Composable
private fun Ajuste(
    titulo: String,
    valor: Int,
    rango: ClosedFloatingPointRange<Float>,
    pasos: Int,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(titulo, style = MaterialTheme.typography.bodyLarge)
            Text("$valor", style = MaterialTheme.typography.bodyLarge)
        }
        Slider(
            value = valor.toFloat().coerceIn(rango),
            onValueChange = { onChange(it.toInt()) },
            valueRange = rango,
            steps = pasos
        )
        Spacer(Modifier.height(8.dp))
    }
}
