package com.femogo.vocab

import android.app.Application
import com.femogo.vocab.data.Biblioteca
import com.femogo.vocab.data.SettingsStore

/**
 * La aplicación es pequeña, así que las dependencias se construyen aquí en vez
 * de arrastrar un contenedor de inyección.
 */
class VocabApplication : Application() {
    val biblioteca: Biblioteca by lazy { Biblioteca(this) }
    val settings: SettingsStore by lazy { SettingsStore(this) }
}
