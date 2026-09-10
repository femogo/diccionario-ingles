package com.femogo.vocab

import android.app.Application
import com.femogo.vocab.data.SettingsStore
import com.femogo.vocab.data.VocabRepository

/**
 * La aplicación es pequeña, así que las dependencias se construyen aquí en vez
 * de arrastrar un contenedor de inyección.
 */
class VocabApplication : Application() {
    val repository: VocabRepository by lazy { VocabRepository(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
