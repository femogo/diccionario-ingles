package com.femogo.vocab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ajustes")

/**
 * Lo único ajustable es cuántas opciones se ofrecen por pregunta. El resto
 * (cuándo vuelve cada palabra, en qué dirección se pregunta) lo decide el motor
 * y no gana nada expuesto como interruptor.
 */
data class Settings(
    val optionCount: Int = 4,
    /** Módulo que se está jugando. */
    val moduloActivo: String = AppDatabase.MODULO_INICIAL
)

/**
 * El avance de cada módulo se guarda por separado: cada juego lleva su propia
 * cuenta de preguntas respondidas y su propia racha reciente, porque su
 * espaciado es independiente.
 */
data class AvanceModulo(val turno: Int = 0, val ultimasRespuestas: String = "")

class SettingsStore(private val context: Context) {

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            optionCount = prefs[KEY_OPTIONS] ?: 4,
            moduloActivo = prefs[KEY_MODULO] ?: AppDatabase.MODULO_INICIAL
        )
    }

    suspend fun setOptionCount(value: Int) {
        context.dataStore.edit { it[KEY_OPTIONS] = value.coerceIn(2, 6) }
    }

    suspend fun setModuloActivo(id: String) {
        context.dataStore.edit { it[KEY_MODULO] = id }
    }

    suspend fun avanceDe(modulo: String): AvanceModulo {
        val prefs = context.dataStore.data.first()
        return AvanceModulo(
            turno = prefs[turnoDe(modulo)] ?: 0,
            ultimasRespuestas = prefs[ultimasDe(modulo)] ?: ""
        )
    }

    suspend fun guardarAvance(modulo: String, turno: Int, ultimasRespuestas: String) {
        context.dataStore.edit {
            it[turnoDe(modulo)] = turno
            it[ultimasDe(modulo)] = ultimasRespuestas
        }
    }

    private companion object {
        val KEY_OPTIONS = intPreferencesKey("options")
        val KEY_MODULO = stringPreferencesKey("modulo_activo")
        fun turnoDe(modulo: String) = intPreferencesKey("turno_$modulo")
        fun ultimasDe(modulo: String) = stringPreferencesKey("ultimas_$modulo")
    }
}
