package com.tanonb.scandoc.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/*
 * Charte inspirée des applications de numérisation mobiles : fond blanc et gris très clair,
 * accent bleu vif (bouton central, liens, sélection), bandeau en dégradé rouge → violet → bleu,
 * typographie aérée de poids normal, cartes à bord fin.
 */
val Accent = Color(0xFF3B63F5)
val AccentLight = Color(0xFF7E9BFF)
val Ink = Color(0xFF1B1B1B)
val PageGray = Color(0xFFF3F3F3)
val Hairline = Color(0xFFE1E1E1)

val BannerGradient = Brush.horizontalGradient(listOf(Color(0xFFB4243F), Color(0xFF7A3AB8), Color(0xFF3F55D6)))

private val Light = lightColorScheme(
    primary = Accent, onPrimary = Color.White,
    primaryContainer = Color(0xFFE3E9FF), onPrimaryContainer = Color(0xFF0B2580),
    secondary = Color(0xFF6E6E6E), onSurface = Ink, onBackground = Ink,
    background = PageGray, surface = Color.White,
    surfaceVariant = Color(0xFFEAEAEA), onSurfaceVariant = Color(0xFF5E5E5E), outlineVariant = Hairline,
    surfaceContainer = Color.White, surfaceContainerLow = Color.White, surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color(0xFFF0F0F0),
)
private val Dark = darkColorScheme(
    primary = AccentLight, onPrimary = Color(0xFF00217A),
    primaryContainer = Color(0xFF213FB0), onPrimaryContainer = Color(0xFFE3E9FF),
    background = Color(0xFF121212), surface = Color(0xFF1E1E1E), surfaceVariant = Color(0xFF2C2C2C),
    surfaceContainer = Color(0xFF1E1E1E), surfaceContainerLow = Color(0xFF1E1E1E), surfaceContainerHigh = Color(0xFF262626),
    outlineVariant = Color(0xFF3A3A3A),
)

private val base = Typography()
private val Type = base.copy(
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyLarge = base.bodyLarge.copy(fontSize = 17.sp),
)

@Composable
fun ScanDocTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, typography = Type, content = content)
}
