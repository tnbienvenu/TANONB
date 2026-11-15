package com.cardiosuivi.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cardiosuivi.app.ui.theme.Health
import com.cardiosuivi.app.ui.theme.LocalReduceMotion
import com.cardiosuivi.app.ui.theme.NumberStyles
import com.cardiosuivi.app.ui.theme.Radius
import com.cardiosuivi.app.ui.theme.Sizes
import com.cardiosuivi.app.ui.theme.Spacing
import com.cardiosuivi.domain.alerts.Alert
import com.cardiosuivi.domain.alerts.AlertLevel

/** Largeur maximale du contenu : lisible sur écran pliable déplié comme sur l'écran externe. */
val MaxContentWidth = Sizes.maxContentWidth

// ---------------------------------------------------------------------------------------------------------------
// Structure des écrans
// ---------------------------------------------------------------------------------------------------------------

/**
 * Écran standard. Sans [onBack] (onglets principaux), le titre est affiché en grand dans le contenu, à la manière
 * des applications de santé ; avec [onBack], une barre supérieure compacte avec bouton « Retour » est utilisée.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenScaffold(
    title: String,
    onBack: (() -> Unit)?,
    actions: @Composable () -> Unit = {},
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val bg = Health.colors.background
    if (onBack == null) {
        Scaffold(containerColor = bg) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                        .padding(horizontal = Spacing.screen).padding(top = Spacing.l, bottom = Spacing.xxl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
                ) {
                    if (title.isNotBlank()) LargeTitle(title, subtitle, actions)
                    content()
                }
            }
        }
    } else {
        Scaffold(
            containerColor = bg,
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
                    navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") } },
                    actions = { actions() },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = bg, scrolledContainerColor = bg),
                )
            },
        ) { padding -> ScrollContent(padding, content) }
    }
}

@Composable
fun LargeTitle(title: String, subtitle: String? = null, actions: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically) { actions() }
    }
}

@Composable
fun ScrollContent(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
        Column(
            Modifier.widthIn(max = MaxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState()).imePadding()
                .padding(horizontal = Spacing.screen, vertical = Spacing.m),
            verticalArrangement = Arrangement.spacedBy(Spacing.cardGap),
            content = content,
        )
    }
}

/** Titre de section placé au-dessus d'un groupe de cartes. */
@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = Spacing.s), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Cartes
// ---------------------------------------------------------------------------------------------------------------

/** Carte blanche arrondie à ombre légère : brique de base de toute l'interface. */
@Composable
fun HealthCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = Spacing.l,
    border: BorderStroke? = null,
    containerColor: Color = Health.colors.card,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(Radius.card)
    val m = modifier.fillMaxWidth().shadow(1.dp, shape, clip = false, ambientColor = Color.Black.copy(alpha = 0.05f), spotColor = Color.Black.copy(alpha = 0.08f))
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val inner: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.padding(contentPadding), verticalArrangement = Arrangement.spacedBy(Spacing.s), content = content)
    }
    if (onClick != null) Card(onClick = onClick, modifier = m, shape = shape, colors = colors, border = border, content = inner)
    else Card(modifier = m, shape = shape, colors = colors, border = border, content = inner)
}

/** En-tête de carte : icône colorée, titre de catégorie, information à droite et chevron si la carte est cliquable. */
@Composable
fun CardHeader(title: String, icon: ImageVector? = null, color: Color = MaterialTheme.colorScheme.primary, trailing: String? = null, chevron: Boolean = false) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
        if (icon != null) Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
        Text(title, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f).semantics { heading() }, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (chevron) Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
    }
}

/** Carte de section (API historique) : titre sobre et contenu. */
@Composable
fun SectionCard(title: String? = null, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    HealthCard(modifier) {
        if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        content()
    }
}

/** Apparition douce (fondu + léger glissement), désactivée si l'utilisateur a réduit les animations. */
@Composable
fun Appear(index: Int = 0, content: @Composable () -> Unit) {
    if (LocalReduceMotion.current) { content(); return }
    val state = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = state,
        enter = fadeIn(tween(260, delayMillis = 40 * index.coerceAtMost(6))) + slideInVertically(tween(320, delayMillis = 40 * index.coerceAtMost(6))) { it / 12 },
    ) { content() }
}

// ---------------------------------------------------------------------------------------------------------------
// Valeurs médicales
// ---------------------------------------------------------------------------------------------------------------

/**
 * Valeur de santé : libellé en toutes lettres (jamais la couleur seule), grand nombre, unité visible.
 * L'ensemble est lu par TalkBack comme une phrase.
 */
@Composable
fun MetricBlock(
    label: String,
    value: String,
    unit: String,
    color: Color,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = NumberStyles.large,
    shortLabel: String? = null,
) {
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "$label : $value $unit" }) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Text(shortLabel ?: label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = style, color = MaterialTheme.colorScheme.onSurface, maxLines = 1)
            Spacer(Modifier.width(4.dp))
            Text(unit, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp), maxLines = 1)
        }
    }
}

/** Triplet systolique / diastolique / pouls ; passe à la ligne sur écran étroit ou grande police. */
@Composable
fun BpValue(sys: String, dia: String, pulse: String?, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = NumberStyles.large) {
    val c = Health.colors
    androidx.compose.foundation.layout.FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
        MetricBlock("Systolique", sys, "mmHg", c.systolic, style = style, shortLabel = "SYS · Systolique")
        MetricBlock("Diastolique", dia, "mmHg", c.diastolic, style = style, shortLabel = "DIA · Diastolique")
        if (pulse != null) MetricBlock("Pouls", pulse, "bpm", c.pulse, style = style, shortLabel = "PUL · Pouls")
    }
}

@Composable
fun LabeledNumber(label: String, value: String, unit: String, color: Color) = MetricBlock(label, value, unit, color, style = NumberStyles.medium)

/** Ligne clé / valeur (statistiques). */
@Composable
fun KeyValue(key: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.Top) {
        Text(key, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(Spacing.m))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(max = 220.dp))
    }
}

/** Indicateur compact : petit titre, valeur, sous-texte (cartes Tendances). */
@Composable
fun StatTile(title: String, value: String, caption: String? = null, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(Radius.inner)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(Spacing.m)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
        Text(value, style = NumberStyles.small, color = color)
        if (caption != null) Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Badge d'état : icône + texte (jamais la couleur seule). */
@Composable
fun StatusBadge(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (icon != null) Icon(icon, null, tint = color, modifier = Modifier.size(14.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1)
    }
}

fun levelColor(level: AlertLevel): Color = when (level) {
    AlertLevel.URGENT -> Color(0xFFC81E1E)
    AlertLevel.CONSULT_SOON -> Color(0xFFD9480F)
    AlertLevel.CONSULT -> Color(0xFFD97706)
    AlertLevel.ATTENTION -> Color(0xFFB7791F)
    AlertLevel.CHECK_MEASUREMENT -> Color(0xFF6D4AC9)
    AlertLevel.INFO -> Color(0xFF5F6B7A)
}

private fun levelIcon(level: AlertLevel): ImageVector = when (level) {
    AlertLevel.URGENT, AlertLevel.CONSULT_SOON -> Icons.Filled.Error
    AlertLevel.CONSULT, AlertLevel.ATTENTION, AlertLevel.CHECK_MEASUREMENT -> Icons.Filled.Warning
    AlertLevel.INFO -> Icons.Filled.Info
}

@Composable
fun AlertCard(alert: Alert, onCallEmergency: (() -> Unit)? = null) {
    val color = levelColor(alert.level)
    HealthCard(border = BorderStroke(1.dp, color.copy(alpha = 0.5f)), containerColor = Health.colors.card) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.m)) {
            Box(Modifier.size(Sizes.iconBadge).clip(RoundedCornerShape(10.dp)).background(color.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(levelIcon(alert.level), contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(alert.level.label, style = MaterialTheme.typography.labelMedium, color = color)
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(alert.message, style = MaterialTheme.typography.bodyMedium)
                if (alert.references.isNotEmpty()) Text("Réf. : " + alert.references.joinToString { it.shortName }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (alert.level == AlertLevel.URGENT && onCallEmergency != null) {
                    Button(onClick = onCallEmergency, colors = ButtonDefaults.buttonColors(containerColor = color), modifier = Modifier.heightIn(min = Sizes.touch)) { Text("Appeler les secours") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Boutons, sélecteurs, listes
// ---------------------------------------------------------------------------------------------------------------

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null, color: Color? = null) {
    Button(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(Radius.button),
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.button),
        colors = if (color != null) ButtonDefaults.buttonColors(containerColor = color) else ButtonDefaults.buttonColors(),
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(22.dp)); Spacer(Modifier.width(Spacing.s)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    OutlinedButton(
        onClick = onClick, enabled = enabled, shape = RoundedCornerShape(Radius.button),
        modifier = modifier.fillMaxWidth().heightIn(min = Sizes.button),
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(Spacing.s)) }
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Contrôle segmenté (7 jours | 30 jours | 90 jours | Année…). */
@Composable
fun <T> SegmentedControl(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { o ->
            val sel = o == selected
            Box(
                Modifier.weight(1f).heightIn(min = 40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (sel) Health.colors.card else Color.Transparent)
                    .selectable(selected = sel, role = Role.Tab) { onSelect(o) }
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Ligne de réglage : icône dans une pastille colorée, titre, sous-titre, chevron. */
@Composable
fun SettingsRow(title: String, subtitle: String? = null, icon: ImageVector? = null, iconColor: Color = MaterialTheme.colorScheme.primary, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(Radius.inner)).clickable(onClick = onClick).padding(vertical = Spacing.s, horizontal = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.m),
    ) {
        if (icon != null) Box(Modifier.size(Sizes.iconBadge).clip(RoundedCornerShape(9.dp)).background(iconColor), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(19.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = Sizes.touch).clip(RoundedCornerShape(Radius.inner))
            .clickable(enabled = enabled, role = Role.Switch) { onChange(!checked) }.padding(vertical = 6.dp)
            .semantics { stateDescription = if (checked) "Activé" else "Désactivé" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = Spacing.m)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        options.forEach { o ->
            FilterChip(
                selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) },
                shape = RoundedCornerShape(Radius.chip), modifier = Modifier.heightIn(min = 40.dp),
                leadingIcon = if (o == selected) { { Icon(Icons.Filled.CheckCircle, null, Modifier.size(16.dp)) } } else null,
                colors = FilterChipDefaults.filterChipColors(selectedContainerColor = MaterialTheme.colorScheme.primaryContainer),
            )
        }
    }
}

/** Champ numérique : clavier chiffré, unité visible, message d'aide ou d'erreur sous le champ. */
@Composable
fun NumberField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier,
    isError: Boolean = false, supporting: String? = null, unit: String? = null, maxLength: Int = 3,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> onChange(v.filter { it.isDigit() }.take(maxLength)) },
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        suffix = unit?.let { { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) } },
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(Radius.field),
        textStyle = NumberStyles.small,
        modifier = modifier,
    )
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, destructive: Boolean = false) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        shape = RoundedCornerShape(28.dp),
        confirmButton = {
            TextButton(onClick = onConfirm, modifier = Modifier.heightIn(min = Sizes.touch)) {
                Text(confirm, color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = Sizes.touch)) { Text("Annuler") } },
    )
}

@Composable
fun StatusDot(color: Color, size: Int = 10) {
    Box(Modifier.size(size.dp).background(color, CircleShape))
}

@Composable
fun Disclaimer() {
    Text(
        "CardioSuivi est un outil personnel d'aide au suivi. Il ne remplace pas un médecin, ne pose pas de diagnostic et ne modifie pas un traitement. En cas d'urgence, appelez les secours.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.s, horizontal = Spacing.xs),
    )
}

/** État vide : icône, message et action éventuelle. */
@Composable
fun EmptyState(text: String, icon: ImageVector = Icons.Filled.Info, action: (@Composable () -> Unit)? = null) {
    HealthCard {
        Column(Modifier.fillMaxWidth().padding(vertical = Spacing.l), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.s)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHighest), contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            action?.invoke()
        }
    }
}

// ---------------------------------------------------------------------------------------------------------------
// Séance de mesure
// ---------------------------------------------------------------------------------------------------------------

/** Progression des mesures : segments « Mesure 1 / 3 ». */
@Composable
fun StepProgress(done: Int, total: Int, current: Int, modifier: Modifier = Modifier) {
    val c = Health.colors
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = "Mesure $current sur $total, $done enregistrée(s)" }, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("MESURE ${current.coerceAtMost(total)} / $total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(total) { i ->
                val fill = when {
                    i < done -> c.favorable
                    i == done -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.surfaceContainerHighest
                }
                Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(fill))
            }
        }
    }
}

/** Chronomètre circulaire (repos, intervalle entre mesures). */
@Composable
fun CircularCountdown(remaining: Int, total: Int, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, size: Dp = 200.dp) {
    val target = 1f - remaining.toFloat() / total.coerceAtLeast(1)
    val reduce = LocalReduceMotion.current
    val progress by animateFloatAsState(target.coerceIn(0f, 1f), animationSpec = tween(if (reduce) 0 else 900), label = "compte à rebours")
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val text = String.format(com.cardiosuivi.domain.util.Fmt.LOCALE, "%d:%02d", remaining / 60, remaining % 60)
    Box(modifier.size(size).semantics { contentDescription = "Temps restant ${remaining / 60} minute(s) ${remaining % 60} seconde(s)" }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(8.dp)) {
            val stroke = 14.dp.toPx()
            val d = this.size.minDimension - stroke
            val tl = Offset((this.size.width - d) / 2, (this.size.height - d) / 2)
            drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(stroke))
            drawArc(color, -90f, 360f * progress, false, tl, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(text, style = NumberStyles.hero, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Rangée d'actions (boutons côte à côte sur écran large, empilés sur écran étroit). */
@Composable
fun ActionRow(content: @Composable RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.s), verticalAlignment = Alignment.CenterVertically, content = content)
}
