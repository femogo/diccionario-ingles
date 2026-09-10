package com.femogo.vocab.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta propia en vez del color dinámico del sistema: en un juego donde el
// verde y el rojo significan acierto y error, dejar que el fondo lo elija el
// móvil arruina el contraste justo donde más importa.
private val Indigo = Color(0xFF5B5BD6)
private val IndigoClaro = Color(0xFF9B9BF0)
private val Cielo = Color(0xFF0EA5E9)

val Acierto = Color(0xFF15803D)
val Fallo = Color(0xFFB91C1C)
val AciertoFondo = Color(0xFFDCFCE7)
val FalloFondo = Color(0xFFFEE2E2)
val AciertoFondoOscuro = Color(0xFF14532D)
val FalloFondoOscuro = Color(0xFF7F1D1D)

private val Claro = lightColorScheme(
    primary = Indigo,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0E0FB),
    onPrimaryContainer = Color(0xFF1E1B4B),
    secondary = Cielo,
    background = Color(0xFFFAFAFC),
    onBackground = Color(0xFF16161D),
    surface = Color.White,
    onSurface = Color(0xFF16161D),
    surfaceVariant = Color(0xFFEFEFF5),
    onSurfaceVariant = Color(0xFF5A5A6B),
    outline = Color(0xFFD4D4E0)
)

private val Oscuro = darkColorScheme(
    primary = IndigoClaro,
    onPrimary = Color(0xFF1E1B4B),
    primaryContainer = Color(0xFF2E2E6B),
    onPrimaryContainer = Color(0xFFE0E0FB),
    secondary = Cielo,
    background = Color(0xFF0F1015),
    onBackground = Color(0xFFECECF2),
    surface = Color(0xFF17181F),
    onSurface = Color(0xFFECECF2),
    surfaceVariant = Color(0xFF23252F),
    onSurfaceVariant = Color(0xFFA5A5B8),
    outline = Color(0xFF3A3C48)
)

/** Esquinas amplias: las opciones son superficies para tocar, no botones de barra. */
private val Formas = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

private val Letra = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.3.sp)
    )
}

/** Colores de acierto y error, resueltos según el tema en curso. */
data class ColoresRespuesta(val acierto: Color, val fallo: Color, val fondoAcierto: Color, val fondoFallo: Color)

@Composable
fun coloresRespuesta(oscuro: Boolean = isSystemInDarkTheme()) = ColoresRespuesta(
    acierto = if (oscuro) Color(0xFF4ADE80) else Acierto,
    fallo = if (oscuro) Color(0xFFF87171) else Fallo,
    fondoAcierto = if (oscuro) AciertoFondoOscuro else AciertoFondo,
    fondoFallo = if (oscuro) FalloFondoOscuro else FalloFondo
)

@Composable
fun VocabTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) Oscuro else Claro,
        shapes = Formas,
        typography = Letra,
        content = content
    )
}

/** Estilo de la palabra preguntada. Grande, en mayúsculas y sin adornos. */
val EstiloPalabra = TextStyle(
    fontWeight = FontWeight.Bold,
    letterSpacing = 1.sp
)
