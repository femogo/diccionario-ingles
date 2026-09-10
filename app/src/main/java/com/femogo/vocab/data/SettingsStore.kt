package com.femogo.vocab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ajustes")

/**
 * Lo único ajustable es cuántas opciones se ofrecen por pregunta. El resto
 * (cuándo vuelve cada palabra, en qué dirección se pregunta) lo decide el motor
 * y no gana nada expuesto como interruptor.
 */
data class Settings(
    val optionCount: Int = 4,
    /** Huella del diccionario instalado, para saber si hay novedades. */
    val dictionaryHash: String? = null
)

class SettingsStore(private val context: Context) {

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            optionCount = prefs[KEY_OPTIONS] ?: 4,
            dictionaryHash = prefs[KEY_DICT_HASH]
        )
    }

    suspend fun setOptionCount(value: Int) {
        context.dataStore.edit { it[KEY_OPTIONS] = value.coerceIn(2, 6) }
    }

    suspend fun setDictionaryHash(hash: String) {
        context.dataStore.edit { it[KEY_DICT_HASH] = hash }
    }

    private companion object {
        val KEY_OPTIONS = intPreferencesKey("options")
        val KEY_DICT_HASH = stringPreferencesKey("dict_hash")
    }
}
