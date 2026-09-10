package com.femogo.vocab.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.femogo.vocab.engine.DirectionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ajustes")

data class Settings(
    val directionMode: DirectionMode = DirectionMode.PROGRESSIVE,
    val newPerDay: Int = 20,
    val sessionSize: Int = 20,
    val optionCount: Int = 4
)

class SettingsStore(private val context: Context) {

    val flow: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            directionMode = prefs[KEY_DIRECTION]
                ?.let { runCatching { DirectionMode.valueOf(it) }.getOrNull() }
                ?: DirectionMode.PROGRESSIVE,
            newPerDay = prefs[KEY_NEW_PER_DAY] ?: 20,
            sessionSize = prefs[KEY_SESSION_SIZE] ?: 20,
            optionCount = prefs[KEY_OPTIONS] ?: 4
        )
    }

    suspend fun setDirection(mode: DirectionMode) =
        context.dataStore.edit { it[KEY_DIRECTION] = mode.name }.let { }

    suspend fun setNewPerDay(value: Int) =
        context.dataStore.edit { it[KEY_NEW_PER_DAY] = value.coerceIn(0, 200) }.let { }

    suspend fun setSessionSize(value: Int) =
        context.dataStore.edit { it[KEY_SESSION_SIZE] = value.coerceIn(5, 100) }.let { }

    suspend fun setOptionCount(value: Int) =
        context.dataStore.edit { it[KEY_OPTIONS] = value.coerceIn(2, 6) }.let { }

    private companion object {
        val KEY_DIRECTION = stringPreferencesKey("direction")
        val KEY_NEW_PER_DAY = intPreferencesKey("new_per_day")
        val KEY_SESSION_SIZE = intPreferencesKey("session_size")
        val KEY_OPTIONS = intPreferencesKey("options")
    }
}
