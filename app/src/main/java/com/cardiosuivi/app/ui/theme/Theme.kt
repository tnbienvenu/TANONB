package com.cardiosuivi.app.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * Système de design CardioSuivi 1.1 — identité originale inspirée des principes ergonomiques des applications de santé
 * premium (fond grisé, cartes blanches arrondies, grandes valeurs, hiérarchie marquée). Aucune ressource tierce.
 * Toutes les couleurs, typographies, rayons et espacements de l'application sont définis ici.
 */

/** Couleurs sémantiques des données de santé, déclinées en clair et en sombre. */
@Immutable
data class HealthColors(
    val systolic: Color,
    val diastolic: Color,
    val pulse: Color,
    val pulsePressure: Color,
    val favorable: Color,
    val attention: Color,
    val alert: Color,
    val neutral: Color,
    val morning: Color,
    val evening: Color,
    val threshold: Color,
    val target: Color,
    val card: Color,
    val background: Color,
    val separator: Color,
    val secondaryText: Color,
    val tint: Color,
)

private val LightHealth = HealthColors(
    systolic = Color(0xFFE0474C),
    diastolic = Color(0xFF2F6FDB),
    pulse = Color(0xFF8250DF),
    pulsePressure = Color(0xFFB0438F),
    favorable = Color(0xFF1E8E4E),
    attention = Color(0xFFD97706),
    alert = Color(0xFFC81E1E),
    neutral = Color(0xFF6B7280),
    morning = Color(0xFFD48A06),
    evening = Color(0xFF4F46C8),
    threshold = Color(0xFFD97706),
    target = Color(0xFF1E8E4E),
    card = Color(0xFFFFFFFF),
    background = Color(0xFFF2F3F7),
    separator = Color(0xFFE3E5EA),
    secondaryText = Color(0xFF5F6470),
    tint = Color(0xFFD9363E),
)

private val DarkHealth = HealthColors(
    systolic = Color(0xFFFF6B6F),
    diastolic = Color(0xFF6EA2FF),
    pulse = Color(0xFFB792FF),
    pulsePressure = Color(0xFFE47CC4),
    favorable = Color(0xFF4CD787),
    attention = Color(0xFFFFB340),
    alert = Color(0xFFFF6B6B),
    neutral = Color(0xFF9CA3AF),
    morning = Color(0xFFFFC24D),
    evening = Color(0xFF9A93FF),
    threshold = Color(0xFFFFB340),
    target = Color(0xFF4CD787),
    card = Color(0xFF1C1D21),
    background = Color(0xFF000000),
    separator = Color(0xFF2E3036),
    secondaryText = Color(0xFFA2A6B0),
    tint = Color(0xFFFF5A61),
)

val LocalHealthColors = staticCompositionLocalOf { LightHealth }

/** Espacements (grille de 4 dp). */
object Spacing {
    val xxs = 2.dp
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp
    val xxxl = 32.dp
    /** Marge latérale des écrans. */
    val screen = 16.dp
    /** Espace vertical entre cartes. */
    val cardGap = 14.dp
}

/** Rayons d'arrondi. */
object Radius {
    val card = 22.dp
    val inner = 14.dp
    val field = 14.dp
    val button = 16.dp
    val chip = 12.dp
    val pill = 50
}

/** Dimensions minimales (accessibilité : cibles tactiles ≥ 48 dp). */
object Sizes {
    val touch = 48.dp
    val button = 54.dp
    val iconBadge = 32.dp
    val maxContentWidth = 680.dp
}

/** Typographie : sans-serif système (Roboto / police du constructeur), chiffres tabulaires pour les valeurs. */
private val Base = Typography()
val AppTypography = Base.copy(
    displayLarge = Base.displayLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-1).sp),
    displayMedium = Base.displayMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
    displaySmall = Base.displaySmall.copy(fontWeight = FontWeight.Bold),
    headlineLarge = Base.headlineLarge.copy(fontWeight = FontWeight.Bold, fontSize = 32.sp, letterSpacing = (-0.5).sp),
    headlineMedium = Base.headlineMedium.copy(fontWeight = FontWeight.Bold),
    headlineSmall = Base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = Base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = Base.titleMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    titleSmall = Base.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    bodyLarge = Base.bodyLarge.copy(fontSize = 17.sp, lineHeight = 24.sp),
    bodyMedium = Base.bodyMedium.copy(fontSize = 15.sp, lineHeight = 21.sp),
    bodySmall = Base.bodySmall.copy(fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = Base.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = Base.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = Base.labelSmall.copy(fontSize = 12.sp, lineHeight = 16.sp),
)

/** Styles des valeurs numériques (chiffres tabulaires pour un alignement stable). */
object NumberStyles {
    val hero = TextStyle(fontFamily = FontFamily.Default, fontSize = 52.sp, lineHeight = 56.sp, fontWeight = FontWeight.Bold, letterSpacing = (-1.5).sp, fontFeatureSettings = "tnum")
    val large = TextStyle(fontFamily = FontFamily.Default, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp, fontFeatureSettings = "tnum")
    val medium = TextStyle(fontFamily = FontFamily.Default, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
    val small = TextStyle(fontFamily = FontFamily.Default, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")
}

/** Ancien style conservé pour compatibilité. */
val ValueStyle = NumberStyles.large

/** Couleurs historiques (graphiques, rapports) : désormais dérivées du thème clair. */
object CardioColors {
    val Systolic = LightHealth.systolic
    val Diastolic = LightHealth.diastolic
    val Pulse = LightHealth.pulse
    val PulsePressure = LightHealth.pulsePressure
    val Threshold = LightHealth.threshold
    val Target = LightHealth.target
    val Morning = LightHealth.morning
    val Evening = LightHealth.evening
    val Done = LightHealth.favorable
    val Incomplete = LightHealth.attention
    val Missed = LightHealth.alert
    val Pending = Color(0xFF9AA3AE)
}

private val Light = lightColorScheme(
    primary = Color(0xFFD9363E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE1E2),
    onPrimaryContainer = Color(0xFF410006),
    secondary = Color(0xFF2F6FDB),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCE7FB),
    onSecondaryContainer = Color(0xFF0B2A5E),
    tertiary = Color(0xFF8250DF),
    tertiaryContainer = Color(0xFFEDE3FF),
    error = Color(0xFFC81E1E),
    errorContainer = Color(0xFFFDE2E2),
    onErrorContainer = Color(0xFF5F0A0A),
    background = LightHealth.background,
    onBackground = Color(0xFF111216),
    surface = LightHealth.background,
    onSurface = Color(0xFF111216),
    surfaceVariant = Color(0xFFE8EAEF),
    onSurfaceVariant = LightHealth.secondaryText,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color.White,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color(0xFFF7F8FA),
    surfaceContainerHighest = Color(0xFFECEEF2),
    outline = Color(0xFFB9BDC6),
    outlineVariant = LightHealth.separator,
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFF5A61),
    onPrimary = Color(0xFF3B0004),
    primaryContainer = Color(0xFF5C1518),
    onPrimaryContainer = Color(0xFFFFDADB),
    secondary = Color(0xFF6EA2FF),
    onSecondary = Color(0xFF002B6B),
    secondaryContainer = Color(0xFF1B3561),
    onSecondaryContainer = Color(0xFFDCE7FB),
    tertiary = Color(0xFFB792FF),
    tertiaryContainer = Color(0xFF3B2470),
    error = Color(0xFFFF6B6B),
    errorContainer = Color(0xFF5C1414),
    onErrorContainer = Color(0xFFFFDADA),
    background = DarkHealth.background,
    onBackground = Color(0xFFF2F3F5),
    surface = DarkHealth.background,
    onSurface = Color(0xFFF2F3F5),
    surfaceVariant = Color(0xFF2A2C31),
    onSurfaceVariant = DarkHealth.secondaryText,
    surfaceContainerLowest = Color(0xFF111215),
    surfaceContainerLow = DarkHealth.card,
    surfaceContainer = DarkHealth.card,
    surfaceContainerHigh = Color(0xFF26272C),
    surfaceContainerHighest = Color(0xFF303137),
    outline = Color(0xFF5B5F68),
    outlineVariant = DarkHealth.separator,
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.field),
    small = RoundedCornerShape(Radius.chip),
    medium = RoundedCornerShape(Radius.inner),
    large = RoundedCornerShape(Radius.card),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Vrai si l'utilisateur a désactivé les animations dans Android (accessibilité). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun CardioTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }.getOrDefault(false)
    }
    CompositionLocalProvider(LocalHealthColors provides if (dark) DarkHealth else LightHealth, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, shapes = AppShapes, content = content)
    }
}

/** Accès rapide : `Health.colors.systolic`. */
object Health {
    val colors: HealthColors
        @Composable @ReadOnlyComposable get() = LocalHealthColors.current
}
