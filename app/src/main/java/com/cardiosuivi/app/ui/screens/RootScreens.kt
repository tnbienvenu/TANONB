package com.cardiosuivi.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.cardiosuivi.app.MainActivity
import com.cardiosuivi.app.security.Biometrics
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.components.ConfirmDialog
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.ScrollContent
import com.cardiosuivi.app.ui.components.SectionCard

@Composable
fun OnboardingScreen(onAccept: () -> Unit) {
    var checked by rememberSaveable { mutableStateOf(false) }
    Scaffold { p ->
        ScrollContent(p) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Favorite, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
                Text("Bienvenue dans CardioSuivi", style = MaterialTheme.typography.headlineSmall)
            }
            SectionCard("À quoi sert l'application") {
                Text("CardioSuivi vous aide à suivre votre tension artérielle à domicile : séances de trois mesures, lecture automatique de l'écran du tensiomètre, statistiques, graphiques et rapports à présenter à votre médecin.")
            }
            SectionCard("Ce que l'application ne fait pas") {
                Text("• Elle ne remplace pas un médecin et ne pose pas de diagnostic.")
                Text("• Elle ne modifie jamais un traitement et ne propose pas de posologie.")
                Text("• Elle n'est pas un dispositif médical certifié.")
            }
            SectionCard("En cas d'urgence") {
                Text("Douleur dans la poitrine, essoufflement important, faiblesse ou engourdissement d'un côté du corps, trouble de la parole ou de la vision, confusion, malaise : appelez immédiatement les secours (112, ou le 15 en France). N'attendez pas une analyse de l'application.", fontWeight = FontWeight.Medium)
            }
            SectionCard("Confidentialité") {
                Text("Vos données restent sur ce téléphone, dans une base chiffrée. Aucune télémétrie. Rien n'est envoyé à un service d'IA sans votre consentement explicite.")
            }
            Row(Modifier.fillMaxWidth().clickable { checked = !checked }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = checked, onCheckedChange = { checked = it })
                Text("J'ai compris que CardioSuivi est un outil d'aide au suivi qui ne remplace pas un avis médical.")
            }
            com.cardiosuivi.app.ui.components.PrimaryButton("Commencer", onAccept, enabled = checked)
        }
    }
}

@Composable
fun LockScreen(activity: MainActivity, onUnlocked: () -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    val available = remember { Biometrics.isAvailable(activity) }
    fun prompt() = Biometrics.authenticate(activity, onUnlocked) { error = it }
    LaunchedEffect(Unit) { if (available) prompt() }
    Scaffold { p ->
        Column(Modifier.fillMaxSize().padding(p).padding(24.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Lock, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Text("CardioSuivi est verrouillé", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(16.dp))
            if (!available) {
                Text("Le déverrouillage biométrique n'est plus disponible sur cet appareil (${Biometrics.describe(Biometrics.availability(activity))}).")
                Button(onClick = onUnlocked, modifier = Modifier.padding(top = 16.dp)) { Text("Continuer") }
            } else {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = { prompt() }, modifier = Modifier.padding(top = 16.dp)) { Text("Déverrouiller") }
            }
        }
    }
}

@Composable
fun DatabaseErrorScreen(error: Throwable?, onReset: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    Scaffold { p ->
        ScrollContent(p) {
            Text("Base de données inaccessible", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.error)
            Text("La base chiffrée n'a pas pu être ouverte. Cela peut arriver si la clé de chiffrement de l'appareil a été réinitialisée (par exemple après une modification de la sécurité du téléphone).")
            Text("Détail technique : ${error?.javaClass?.simpleName ?: "inconnu"} — ${error?.message ?: ""}", style = MaterialTheme.typography.bodySmall)
            Text("Si vous disposez d'une sauvegarde chiffrée, vous pourrez la restaurer après réinitialisation.")
            OutlinedButton(onClick = { confirm = true }) { Text("Réinitialiser l'application") }
        }
    }
    if (confirm) ConfirmDialog(
        "Réinitialiser ?", "Toutes les données locales seront définitivement supprimées.", "Réinitialiser",
        onConfirm = { confirm = false; onReset() }, onDismiss = { confirm = false }, destructive = true,
    )
}

private data class MenuItem(val label: String, val subtitle: String, val icon: ImageVector, val route: String, val color: androidx.compose.ui.graphics.Color)

/** Onglet « Profil » : identité, santé, suivi, IA, sécurité et informations, regroupés en sections. */
@Composable
fun MoreScreen(vm: com.cardiosuivi.app.ui.AppViewModel, nav: NavHostController) {
    val profile by vm.profile.collectAsState()
    val settings by vm.settings.collectAsState()
    val h = com.cardiosuivi.app.ui.theme.Health.colors
    val groups = listOf(
        "Santé" to listOf(
            MenuItem("Profil personnel", "Identité, objectif tensionnel, antécédents", Icons.Filled.Person, Routes.PROFILE, h.systolic),
            MenuItem("Traitements et observations", "Traitements, journal", Icons.Filled.Medication, Routes.TREATMENTS, h.pulse),
        ),
        "Suivi" to listOf(
            MenuItem("Rappels", "Jours, horaires, intervalle", Icons.Filled.Alarm, Routes.REMINDERS, h.attention),
            MenuItem("Calendrier des séances", "Séances réalisées et prévues", Icons.Filled.CalendarMonth, Routes.CALENDAR, h.diastolic),
            MenuItem("Automesure standardisée", "Protocole sur 7 jours", Icons.Filled.Science, Routes.HBPM, h.favorable),
            MenuItem("Rapports PDF et CSV", "Exporter et partager", Icons.Filled.PictureAsPdf, Routes.REPORTS, h.alert),
        ),
        "Intelligence artificielle" to listOf(
            MenuItem("Assistant IA", "Questions sur vos mesures", Icons.AutoMirrored.Filled.Chat, Routes.ASSISTANT, MaterialTheme.colorScheme.tertiary),
            MenuItem("Configuration de l'IA", "Fournisseur, clé, consentement, données partagées", Icons.Filled.AutoAwesome, Routes.AI_SETTINGS, MaterialTheme.colorScheme.tertiary),
        ),
        "Sécurité et confidentialité" to listOf(
            MenuItem("Paramètres et sécurité", "Verrouillage biométrique, écran protégé, calculs", Icons.Filled.Settings, Routes.SETTINGS, h.neutral),
            MenuItem("Sauvegarde et restauration", "Fichier chiffré", Icons.Filled.SettingsBackupRestore, Routes.BACKUP, h.diastolic),
            MenuItem("Autorisations", "Diagnostic notifications et alarmes", Icons.Filled.VerifiedUser, Routes.PERMISSIONS, h.favorable),
        ),
        "Informations" to listOf(
            MenuItem("Références médicales", "Recommandations et seuils", Icons.AutoMirrored.Filled.MenuBook, Routes.REFERENCES, h.neutral),
        ),
    )
    ScreenScaffold("Profil", onBack = null) {
        com.cardiosuivi.app.ui.components.HealthCard(onClick = { nav.navigate(Routes.PROFILE) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Icon(Icons.Filled.AccountCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(56.dp))
                Column(Modifier.weight(1f)) {
                    Text(profile.displayName.ifBlank { "Mon profil" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        profile.target?.let { "Objectif : < ${it.systolic}/${it.diastolic} mmHg" } ?: "Objectif tensionnel non renseigné",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "Verrouillage biométrique : " + if (settings.biometricLock) "activé" else "désactivé",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        groups.forEach { (title, items) ->
            Text(title.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
            com.cardiosuivi.app.ui.components.HealthCard(contentPadding = 8.dp) {
                items.forEachIndexed { i, m ->
                    if (i > 0) androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(start = 52.dp))
                    com.cardiosuivi.app.ui.components.SettingsRow(m.label, m.subtitle, m.icon, m.color) { nav.navigate(m.route) }
                }
            }
        }
        com.cardiosuivi.app.ui.components.Disclaimer()
    }
}
