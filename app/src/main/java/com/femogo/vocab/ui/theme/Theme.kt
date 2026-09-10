package com.femogo.vocab.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Azul = Color(0xFF1B4965)
private val AzulClaro = Color(0xFF5FA8D3)
private val Arena = Color(0xFFF6F4EF)

/** Verde y rojo del acierto y el fallo, legibles sobre ambos fondos. */
val Acierto = Color(0xFF2E7D32)
val Fallo = Color(0xFFC62828)
val AciertoSuave = Color(0xFFC8E6C9)
val FalloSuave = Color(0xFFFFCDD2)

private val LightColors = lightColorScheme(
    primary = Azul,
    secondary = AzulClaro,
    background = Arena,
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = AzulClaro,
    secondary = Azul
)

@Composable
fun VocabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // A partir de Android 12 se respeta el color del sistema; antes se usa la
    // paleta propia.
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
