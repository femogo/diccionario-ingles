package com.femogo.vocab.ui

import android.app.Application
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.femogo.vocab.VocabApplication
import com.femogo.vocab.data.AppUpdater
import com.femogo.vocab.data.DictionaryUpdater
import com.femogo.vocab.data.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AjustesViewModel(app: Application) : AndroidViewModel(app) {
    private val store = (app as VocabApplication).settings
    private val repo = (app as VocabApplication).repository
    private val updater = DictionaryUpdater(app, repo)
    private val appUpdater = AppUpdater(app)

    val settings: StateFlow<Settings> =
        store.flow.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

    private val _mensaje = MutableStateFlow<String?>(null)
    val mensaje: StateFlow<String?> = _mensaje.asStateFlow()

    private val _actualizando = MutableStateFlow(false)
    val actualizando: StateFlow<Boolean> = _actualizando.asStateFlow()

    private val _paso = MutableStateFlow("")
    val paso: StateFlow<String> = _paso.asStateFlow()

    private val _permisoPendiente = MutableStateFlow(false)
    val permisoPendiente: StateFlow<Boolean> = _permisoPendiente.asStateFlow()

    private val _palabras = MutableStateFlow(0)
    val palabras: StateFlow<Int> = _palabras.asStateFlow()

    init { contarPalabras() }

    fun setOptionCount(v: Int) = viewModelScope.launch { store.setOptionCount(v) }

    private fun contarPalabras() {
        viewModelScope.launch { _palabras.value = repo.catalog().size }
    }

    /**
     * Busca novedades de las dos cosas que pueden cambiar: las palabras y la
     * propia aplicación. [alTerminar] avisa a la pantalla de juego para que deje
     * de preguntar por el catálogo viejo.
     *
     * El último paso no puede ser automático. Android solo permite instalar sin
     * confirmación a aplicaciones del sistema, así que aquí se descarga todo y
     * se abre el instalador para que baste un toque.
     */
    fun actualizar(alTerminar: () -> Unit) {
        if (_actualizando.value) return
        viewModelScope.launch {
            _actualizando.value = true
            val resumen = StringBuilder()

            _paso.value = "Buscando palabras nuevas…"
            val huella = store.flow.first().dictionaryHash
            val (resultado, nueva) = updater.actualizar(huella)
            when (resultado) {
                is DictionaryUpdater.Resultado.AlDia ->
                    resumen.append("El diccionario ya está al día.")
                is DictionaryUpdater.Resultado.Instalado -> {
                    nueva?.let { store.setDictionaryHash(it) }
                    contarPalabras()
                    alTerminar()
                    resumen.append("Instaladas ${resultado.palabras} palabras.")
                }
                is DictionaryUpdater.Resultado.Fallo ->
                    resumen.append(resultado.motivo)
            }

            _paso.value = "Buscando versiones de la aplicación…"
            val disponible = appUpdater.comprobar()
            if (disponible == null) {
                resumen.append("\nLa aplicación está en su última versión.")
            } else {
                val (version, url) = disponible
                if (!appUpdater.puedeInstalar()) {
                    _permisoPendiente.value = true
                    resumen.append("\nHay una versión nueva (0.$version), pero falta " +
                        "darle permiso para instalar aplicaciones.")
                } else {
                    _paso.value = "Descargando la versión 0.$version…"
                    appUpdater.descargar(url)
                        .onSuccess {
                            resumen.append("\nVersión 0.$version descargada. " +
                                "Confirma la instalación cuando te lo pida.")
                            appUpdater.instalar(it)
                        }
                        .onFailure {
                            resumen.append("\nNo se ha podido descargar la versión 0.$version: " +
                                (it.message ?: "fallo de red"))
                        }
                }
            }

            _paso.value = ""
            _mensaje.value = resumen.toString()
            _actualizando.value = false
        }
    }

    fun abrirPermisoDeInstalacion() {
        _permisoPendiente.value = false
        appUpdater.abrirAjustesDePermiso()
    }

    fun descartarPermiso() { _permisoPendiente.value = false }

    fun reiniciarProgreso() {
        viewModelScope.launch {
            repo.resetProgress()
            _mensaje.value = "Progreso borrado"
        }
    }

    fun limpiarMensaje() { _mensaje.value = null }
}

@Composable
fun AjustesScreen(
    vm: AjustesViewModel,
    onDiccionarioActualizado: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by vm.settings.collectAsState()
    val mensaje by vm.mensaje.collectAsState()
    val actualizando by vm.actualizando.collectAsState()
    val paso by vm.paso.collectAsState()
    val permisoPendiente by vm.permisoPendiente.collectAsState()
    val palabras by vm.palabras.collectAsState()
    var confirmarReinicio by remember { mutableStateOf(false) }

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
            Text("Ajustes", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(28.dp))

            Text("Opciones por pregunta", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "Cuantas más opciones, menos se acierta por eliminación.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            SelectorNumero(
                valores = (2..6).toList(),
                seleccionado = settings.optionCount,
                onSelect = vm::setOptionCount
            )

            Spacer(Modifier.height(36.dp))
            Text("Actualizaciones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "$palabras palabras instaladas. Busca palabras nuevas y versiones " +
                    "nuevas de la aplicación. Tu progreso no se toca. La instalación " +
                    "la confirmas tú: Android no deja que una aplicación se instale sola.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { vm.actualizar(onDiccionarioActualizado) },
                enabled = !actualizando,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 60.dp),
                shape = RoundedCornerShape(20.dp)
            ) {
                if (actualizando) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        paso.ifEmpty { "Buscando novedades…" },
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Buscar actualizaciones", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(Modifier.height(36.dp))
            OutlinedButton(
                onClick = { confirmarReinicio = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            ) { Text("Borrar mi progreso") }

            Spacer(Modifier.height(32.dp))
        }
    }

    mensaje?.let {
        AlertDialog(
            onDismissRequest = vm::limpiarMensaje,
            confirmButton = { TextButton(onClick = vm::limpiarMensaje) { Text("Vale") } },
            text = { Text(it) },
            shape = RoundedCornerShape(24.dp)
        )
    }

    if (permisoPendiente) {
        AlertDialog(
            onDismissRequest = vm::descartarPermiso,
            title = { Text("Permiso para instalar") },
            text = {
                Text(
                    "Android exige que autorices a esta aplicación a instalar " +
                        "actualizaciones. Se abrirán los ajustes del sistema; activa " +
                        "el permiso y vuelve a pulsar Buscar actualizaciones."
                )
            },
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                TextButton(onClick = vm::abrirPermisoDeInstalacion) { Text("Abrir ajustes") }
            },
            dismissButton = {
                TextButton(onClick = vm::descartarPermiso) { Text("Ahora no") }
            }
        )
    }

    if (confirmarReinicio) {
        AlertDialog(
            onDismissRequest = { confirmarReinicio = false },
            title = { Text("¿Borrar el progreso?") },
            text = { Text("Se pierden las cajas y las estadísticas de todas las palabras. No se puede deshacer.") },
            shape = RoundedCornerShape(24.dp),
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

/** Fila de pastillas para elegir un número. Más directo que un deslizador. */
@Composable
private fun SelectorNumero(
    valores: List<Int>,
    seleccionado: Int,
    onSelect: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        valores.forEach { valor ->
            val activo = valor == seleccionado
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(
                        if (activo) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.surface
                    )
                    .border(
                        width = if (activo) 0.dp else 1.dp,
                        color = if (activo) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.outline,
                        shape = RoundedCornerShape(18.dp)
                    )
                    .clickable { onSelect(valor) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "$valor",
                    fontSize = 19.sp,
                    fontWeight = if (activo) FontWeight.Bold else FontWeight.Normal,
                    color = if (activo) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
