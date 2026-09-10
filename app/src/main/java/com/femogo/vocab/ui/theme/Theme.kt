package com.femogo.vocab.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// Paleta oscura fija, no la del sistema. En un juego donde el verde y el rojo
// significan acierto y error, dejar el fondo a elección del móvil arruina el
// contraste justo donde más importa.
private val Fondo = Color(0xFF0A0A10)
private val Superficie = Color(0xFF15151F)
private val SuperficieAlta = Color(0xFF1F1F2D)
private val Borde = Color(0xFF32334A)
private val Indigo = Color(0xFF8B8BF5)
private val IndigoFondo = Color(0xFF272750)
private val Tinta = Color(0xFFF2F2F8)
private val TintaSuave = Color(0xFF9A9AB4)

val Acierto = Color(0xFF4ADE80)
val Fallo = Color(0xFFF87171)
val AciertoFondo = Color(0xFF10391F)
val FalloFondo = Color(0xFF45161A)

private val Esquema = darkColorScheme(
    primary = Indigo,
    onPrimary = Color(0xFF12122A),
    primaryContainer = IndigoFondo,
    onPrimaryContainer = Tinta,
    secondary = Color(0xFF38BDF8),
    background = Fondo,
    onBackground = Tinta,
    surface = Superficie,
    onSurface = Tinta,
    surfaceVariant = SuperficieAlta,
    onSurfaceVariant = TintaSuave,
    outline = Borde
)

/** Esquinas amplias: las opciones son superficies para tocar, no botones de barra. */
private val Formas = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(34.dp)
)

// Todo un peso por encima de lo habitual: sobre fondo oscuro la tipografía fina
// pierde cuerpo, y a distancia de tablet se lee peor.
private val Letra = Typography().run {
    copy(
        headlineSmall = headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.Bold),
        bodyLarge = bodyLarge.copy(fontWeight = FontWeight.Medium),
        bodyMedium = bodyMedium.copy(fontWeight = FontWeight.Medium),
        bodySmall = bodySmall.copy(fontWeight = FontWeight.Medium),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp),
        labelMedium = labelMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
    )
}

@Composable
fun VocabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Esquema, shapes = Formas, typography = Letra, content = content)
}

/** Estilo de la palabra preguntada. Grande, en mayúsculas y sin adornos. */
val EstiloPalabra = TextStyle(
    fontWeight = FontWeight.Black,
    letterSpacing = 1.5.sp
)

/**
 * Color de un tramo de la barra de nivel: rojo cuando está sin tocar, ámbar a
 * medias, verde cuando está asentado.
 */
fun colorDeDominio(dominio: Float): Color {
    val d = dominio.coerceIn(0f, 1f)
    val rojo = Color(0xFFDC2626)
    val ambar = Color(0xFFF59E0B)
    val verde = Color(0xFF22C55E)
    return if (d < 0.5f) mezcla(rojo, ambar, d * 2f)
    else mezcla(ambar, verde, (d - 0.5f) * 2f)
}

private fun mezcla(a: Color, b: Color, t: Float) = Color(
    red = a.red + (b.red - a.red) * t,
    green = a.green + (b.green - a.green) * t,
    blue = a.blue + (b.blue - a.blue) * t
)
