package com.cardiosuivi.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object CardioColors {
    val Systolic = Color(0xFFC62828)
    val Diastolic = Color(0xFF1565C0)
    val Pulse = Color(0xFF2E7D32)
    val PulsePressure = Color(0xFF6A1B9A)
    val Threshold = Color(0xFFEF6C00)
    val Target = Color(0xFF2E7D32)
    val Morning = Color(0xFFF9A825)
    val Evening = Color(0xFF3949AB)
    val Done = Color(0xFF2E7D32)
    val Incomplete = Color(0xFFF9A825)
    val Missed = Color(0xFFC62828)
    val Pending = Color(0xFF90A4AE)
}

private val Light = lightColorScheme(
    primary = Color(0xFF0F5C78),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC4E7F5),
    onPrimaryContainer = Color(0xFF001F2A),
    secondary = Color(0xFF4D616B),
    secondaryContainer = Color(0xFFD0E6F1),
    tertiary = Color(0xFF5D5B7D),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFF9DEDC),
    background = Color(0xFFF7F9FB),
    surface = Color(0xFFF7F9FB),
    surfaceVariant = Color(0xFFDCE4E9),
    surfaceContainer = Color(0xFFEDF1F4),
    surfaceContainerHigh = Color(0xFFE6EBEF),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF7FD0EF),
    onPrimary = Color(0xFF003546),
    primaryContainer = Color(0xFF004D64),
    onPrimaryContainer = Color(0xFFC4E7F5),
    secondary = Color(0xFFB4CAD5),
    secondaryContainer = Color(0xFF354A53),
    error = Color(0xFFF2B8B5),
    errorContainer = Color(0xFF8C1D18),
    background = Color(0xFF0F1417),
    surface = Color(0xFF0F1417),
    surfaceContainer = Color(0xFF1B2125),
    surfaceContainerHigh = Color(0xFF252B2F),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
    )
}

val ValueStyle = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold)

@Composable
fun CardioTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
}
