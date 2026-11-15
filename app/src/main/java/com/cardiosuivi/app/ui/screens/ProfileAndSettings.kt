package com.cardiosuivi.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavHostController
import com.cardiosuivi.app.BuildConfig
import com.cardiosuivi.app.backup.RestoreSummary
import com.cardiosuivi.app.report.Sharing
import com.cardiosuivi.app.security.Biometrics
import com.cardiosuivi.app.ui.AppViewModel
import com.cardiosuivi.app.ui.Routes
import com.cardiosuivi.app.ui.components.ChoiceChips
import com.cardiosuivi.app.ui.components.ConfirmDialog
import com.cardiosuivi.app.ui.components.Disclaimer
import com.cardiosuivi.app.ui.components.EmptyState
import com.cardiosuivi.app.ui.components.KeyValue
import com.cardiosuivi.app.ui.components.NumberField
import com.cardiosuivi.app.ui.components.ScreenScaffold
import com.cardiosuivi.app.ui.components.SectionCard
import com.cardiosuivi.app.ui.components.SwitchRow
import com.cardiosuivi.domain.backup.BackupCrypto
import com.cardiosuivi.domain.backup.BackupPayload
import com.cardiosuivi.domain.model.AggregationMethod
import com.cardiosuivi.domain.model.BpTarget
import com.cardiosuivi.domain.model.Observation
import com.cardiosuivi.domain.model.Profile
import com.cardiosuivi.domain.model.Sex
import com.cardiosuivi.domain.model.Treatment
import com.cardiosuivi.domain.util.Fmt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeParseException

private fun parseDate(s: String): LocalDate? = s.trim().takeIf { it.isNotEmpty() }?.let {
    try {
        val p = it.split('/', '-', '.')
        if (p.size == 3 && p[0].length <= 2) LocalDate.of(p[2].toInt(), p[1].toInt(), p[0].toInt()) else LocalDate.parse(it)
    } catch (e: Exception) { null }
}

private fun parseTime(s: String): LocalTime? = s.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it.replace('h', ':').let { t -> if (t.length == 4 && t[1] == ':') "0$t" else t }) }.getOrNull() }

@Composable
fun ProfileScreen(vm: AppViewModel, onBack: () -> Unit) {
    val p by vm.profile.collectAsState()
    var name by rememberSaveable(p) { mutableStateOf(p.displayName) }
    var birth by rememberSaveable(p) { mutableStateOf(p.birthDate?.let { Fmt.date(it) } ?: "") }
    var sex by rememberSaveable(p) { mutableStateOf(p.sex) }
    var height by rememberSaveable(p) { mutableStateOf(p.heightCm?.toString() ?: "") }
    var weight by rememberSaveable(p) { mutableStateOf(p.weightKg?.let { Fmt.d1(it) } ?: "") }
    var tSys by rememberSaveable(p) { mutableStateOf(p.target?.systolic?.toString() ?: "") }
    var tDia by rememberSaveable(p) { mutableStateOf(p.target?.diastolic?.toString() ?: "") }
    var history by rememberSaveable(p) { mutableStateOf(p.medicalHistory) }
    var obs by rememberSaveable(p) { mutableStateOf(p.observations) }
    var message by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Profil personnel", onBack = onBack) {
        Text("Ces informations restent sur votre téléphone (base chiffrée). Elles figurent dans vos rapports PDF.", style = MaterialTheme.typography.bodySmall)
        SectionCard("Identité") {
            OutlinedTextField(name, { name = it }, label = { Text("Nom ou pseudonyme") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(birth, { birth = it }, label = { Text("Date de naissance (JJ/MM/AAAA)") }, singleLine = true, isError = birth.isNotBlank() && parseDate(birth) == null, modifier = Modifier.fillMaxWidth())
            Text("Sexe (facultatif)", style = MaterialTheme.typography.labelLarge)
            ChoiceChips(Sex.entries.toList(), sex, { it.label }) { sex = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Taille (cm)", height, { height = it }, Modifier.weight(1f))
                OutlinedTextField(weight, { weight = it.filter { c -> c.isDigit() || c == ',' || c == '.' }.take(5) }, label = { Text("Poids (kg)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
            }
        }
        SectionCard("Objectif tensionnel personnalisé") {
            Text("À définir avec votre médecin (mesures à domicile). Exemple de saisie : 135 et 85 pour « < 135/85 ».", style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Systolique <", tSys, { tSys = it }, Modifier.weight(1f))
                NumberField("Diastolique <", tDia, { tDia = it }, Modifier.weight(1f))
            }
        }
        SectionCard("Antécédents et observations") {
            OutlinedTextField(history, { history = it }, label = { Text("Antécédents médicaux pertinents") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(obs, { obs = it }, label = { Text("Observations complémentaires") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        }
        Button(modifier = Modifier.fillMaxWidth(), onClick = {
            val bd = parseDate(birth)
            val ts = tSys.toIntOrNull(); val td = tDia.toIntOrNull()
            message = when {
                birth.isNotBlank() && bd == null -> "Date de naissance invalide."
                (ts == null) != (td == null) -> "Renseignez les deux valeurs de l'objectif, ou aucune."
                ts != null && td != null && (ts !in 90..200 || td !in 50..130 || ts <= td) -> "Objectif incohérent : vérifiez les valeurs."
                else -> {
                    vm.saveProfile(Profile(name.trim(), bd, sex, height.toIntOrNull(), weight.replace(',', '.').toDoubleOrNull(),
                        if (ts != null && td != null) BpTarget(ts, td) else null, history.trim(), obs.trim()))
                    "Profil enregistré."
                }
            }
        }) { Text("Enregistrer") }
        message?.let { Text(it) }
    }
}

@Composable
fun TreatmentsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val treatments by vm.treatments.collectAsState()
    val observations by vm.observations.collectAsState()
    // État conservé lors d'une rotation ou d'un pliage/dépliage : identifiant du traitement édité (0 = nouveau).
    var editingId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editing: Treatment? = editingId?.let { id -> if (id == 0L) Treatment(name = "") else treatments.firstOrNull { it.id == id } }
    var newObs by rememberSaveable { mutableStateOf("") }
    var deleteT by remember { mutableStateOf<Treatment?>(null) }

    ScreenScaffold("Traitements et observations", onBack = onBack) {
        SectionCard("Traitements antihypertenseurs") {
            Text("Informations recopiées depuis votre ordonnance. L'application n'émet aucun conseil de posologie.", style = MaterialTheme.typography.bodySmall)
            if (treatments.isEmpty()) Text("Aucun traitement renseigné.")
            treatments.forEach { t ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t.name + if (!t.active) " (arrêté)" else "", style = MaterialTheme.typography.titleSmall)
                        Text(listOfNotNull(t.dosage.ifBlank { null }, t.usualTime?.let { "prise à $it" }, t.startDate?.let { "depuis le ${Fmt.date(it)}" }).joinToString(" · "), style = MaterialTheme.typography.bodySmall)
                        if (t.notes.isNotBlank()) Text(t.notes, style = MaterialTheme.typography.bodySmall)
                    }
                    IconButton(onClick = { editingId = t.id }) { Icon(Icons.Filled.Edit, "Modifier") }
                    IconButton(onClick = { deleteT = t }) { Icon(Icons.Filled.Delete, "Supprimer") }
                }
            }
            OutlinedButton(onClick = { editingId = 0L }) { Text("Ajouter un traitement") }
        }
        SectionCard("Journal d'observations") {
            OutlinedTextField(newObs, { newObs = it }, label = { Text("Nouvelle observation (ex. : changement de traitement, voyage…)") }, modifier = Modifier.fillMaxWidth())
            Button(enabled = newObs.isNotBlank(), onClick = { vm.saveObservation(Observation(date = vm.now().toLocalDate(), text = newObs.trim())); newObs = "" }) { Text("Ajouter") }
            if (observations.isEmpty()) EmptyState("Aucune observation.")
            observations.forEach { o ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(Fmt.date(o.date), style = MaterialTheme.typography.labelMedium)
                        Text(o.text)
                    }
                    IconButton(onClick = { vm.deleteObservation(o.id) }) { Icon(Icons.Filled.Delete, "Supprimer") }
                }
            }
        }
    }

    editing?.let { t ->
        var name by rememberSaveable(t.id) { mutableStateOf(t.name) }
        var dosage by rememberSaveable(t.id) { mutableStateOf(t.dosage) }
        var time by rememberSaveable(t.id) { mutableStateOf(t.usualTime?.toString() ?: "") }
        var start by rememberSaveable(t.id) { mutableStateOf(t.startDate?.let { Fmt.date(it) } ?: "") }
        var active by rememberSaveable(t.id) { mutableStateOf(t.active) }
        var notes by rememberSaveable(t.id) { mutableStateOf(t.notes) }
        AlertDialog(
            onDismissRequest = { editingId = null },
            title = { Text(if (t.id == 0L) "Nouveau traitement" else "Modifier le traitement") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Médicament") }, singleLine = true)
                    OutlinedTextField(dosage, { dosage = it }, label = { Text("Posologie prescrite") }, singleLine = true)
                    val timeBad = time.isNotBlank() && parseTime(time) == null
                    val startBad = start.isNotBlank() && parseDate(start) == null
                    OutlinedTextField(time, { time = it }, label = { Text("Heure habituelle (HH:MM)") }, singleLine = true, isError = timeBad,
                        supportingText = if (timeBad) { { Text("Heure invalide : format HH:MM, de 00:00 à 23:59") } } else null)
                    OutlinedTextField(start, { start = it }, label = { Text("Début (JJ/MM/AAAA)") }, singleLine = true, isError = startBad,
                        supportingText = if (startBad) { { Text("Date invalide : format JJ/MM/AAAA") } } else null)
                    OutlinedTextField(notes, { notes = it }, label = { Text("Notes") })
                    SwitchRow("Traitement en cours", checked = active) { active = it }
                }
            },
            confirmButton = {
                // Une heure ou une date invalide n'est jamais enregistrée silencieusement : l'enregistrement est bloqué.
                val valid = name.isNotBlank() && (time.isBlank() || parseTime(time) != null) && (start.isBlank() || parseDate(start) != null)
                TextButton(enabled = valid, onClick = {
                    vm.saveTreatment(t.copy(name = name.trim(), dosage = dosage.trim(), usualTime = parseTime(time), startDate = parseDate(start), active = active, notes = notes.trim()))
                    editingId = null
                }) { Text("Enregistrer") }
            },
            dismissButton = { TextButton(onClick = { editingId = null }) { Text("Annuler") } },
        )
    }
    deleteT?.let { t -> ConfirmDialog("Supprimer ${t.name} ?", "Ce traitement sera retiré de votre profil.", "Supprimer", onConfirm = { vm.deleteTreatment(t.id); deleteT = null }, onDismiss = { deleteT = null }, destructive = true) }
}

@Composable
fun BackupScreen(vm: AppViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by vm.settings.collectAsState()
    val sessions by vm.sessions.collectAsState()
    var pwd by remember { mutableStateOf("") }
    var pwd2 by remember { mutableStateOf("") }
    var restorePwd by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<BackupPayload, RestoreSummary>?>(null) }
    var confirmWipe by remember { mutableStateOf(0) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            message = try {
                val n = vm.c.backup.export(uri, pwd.toCharArray())
                pwd = ""; pwd2 = ""
                "Sauvegarde chiffrée créée ($n séances)."
            } catch (e: Exception) { "Échec : ${e.message}" } finally { busy = false }
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            try {
                val p = vm.c.backup.read(uri, restorePwd.toCharArray())
                pending = p to vm.c.backup.summarize(p)
                message = null
            } catch (e: Exception) { message = "Échec : ${e.message}" } finally { busy = false }
        }
    }

    ScreenScaffold("Sauvegarde et restauration", onBack = onBack) {
        SectionCard("Créer une sauvegarde chiffrée") {
            Text("Le fichier est chiffré (AES-256-GCM, clé dérivée de votre mot de passe par PBKDF2). Sans ce mot de passe, il est illisible : conservez-le précieusement. Les clés d'API ne sont jamais sauvegardées.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(pwd, { pwd = it }, label = { Text("Mot de passe (${BackupCrypto.MIN_PASSWORD_LENGTH} caractères minimum)") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(pwd2, { pwd2 = it }, label = { Text("Confirmer le mot de passe") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, isError = pwd2.isNotEmpty() && pwd != pwd2, modifier = Modifier.fillMaxWidth())
            Button(enabled = !busy && pwd.length >= BackupCrypto.MIN_PASSWORD_LENGTH && pwd == pwd2, onClick = { createDoc.launch("CardioSuivi_${vm.now().toLocalDate()}.csbk") }) { Text("Choisir l'emplacement et sauvegarder") }
        }
        SectionCard("Restaurer") {
            Text("La restauration REMPLACE toutes les données actuelles par celles de la sauvegarde.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            OutlinedTextField(restorePwd, { restorePwd = it }, label = { Text("Mot de passe de la sauvegarde") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = !busy && restorePwd.isNotEmpty(), onClick = { openDoc.launch(arrayOf("*/*")) }) { Text("Choisir un fichier de sauvegarde") }
        }
        SectionCard("Exporter") {
            OutlinedButton(onClick = {
                val today = vm.now().toLocalDate()
                val from = sessions.minOfOrNull { it.localDate } ?: today
                val f = Sharing.writeCsv(context, sessions, settings.aggregation, from, today, vm.zone)
                Sharing.share(context, f, "text/csv", "Mesures CardioSuivi")
            }) { Text("Exporter toutes les mesures (CSV, non chiffré)") }
        }
        SectionCard("Supprimer les données") {
            Text("Supprime définitivement toutes les mesures, le profil, les traitements, les clés et les paramètres de cet appareil.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { confirmWipe = 1 }) { Text("Supprimer toutes les données", color = MaterialTheme.colorScheme.error) }
        }
        if (busy) CircularProgressIndicator()
        message?.let { Text(it) }
    }

    pending?.let { (p, s) ->
        ConfirmDialog(
            "Restaurer cette sauvegarde ?",
            "Créée le ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(s.createdAt.toEpochMilli()))} : ${s.sessions} séances, ${s.measurements} mesures, ${s.treatments} traitements, ${s.observations} observations. Les données actuelles seront remplacées.",
            "Restaurer",
            onConfirm = {
                pending = null; busy = true
                scope.launch {
                    message = try { vm.c.backup.restore(p); restorePwd = ""; "Restauration terminée." } catch (e: Exception) { "Échec : ${e.message}" } finally { busy = false }
                }
            },
            onDismiss = { pending = null }, destructive = true,
        )
    }
    if (confirmWipe == 1) ConfirmDialog("Supprimer toutes les données ?", "Cette action est irréversible. Pensez à créer une sauvegarde chiffrée avant.", "Continuer",
        onConfirm = { confirmWipe = 2 }, onDismiss = { confirmWipe = 0 }, destructive = true)
    if (confirmWipe == 2) ConfirmDialog("Confirmation définitive", "Toutes les données de CardioSuivi vont être effacées de cet appareil.", "Tout supprimer",
        onConfirm = {
            confirmWipe = 0
            scope.launch {
                vm.c.destroyAllData()
                (context as? android.app.Activity)?.let { com.cardiosuivi.app.ui.restartApp(it) }
            }
        }, onDismiss = { confirmWipe = 0 }, destructive = true)
}

@Composable
fun SettingsScreen(vm: AppViewModel, nav: NavHostController) {
    val settings by vm.settings.collectAsState()
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var emergency by rememberSaveable(settings.emergencyNumber) { mutableStateOf(settings.emergencyNumber) }
    var bioMessage by remember { mutableStateOf<String?>(null) }

    ScreenScaffold("Paramètres et sécurité", onBack = { nav.popBackStack() }) {
        SectionCard("Sécurité") {
            SwitchRow("Verrouillage biométrique", "Empreinte, visage ou code de l'appareil à l'ouverture", settings.biometricLock) { on ->
                if (!on) vm.setBiometric(false)
                else if (activity != null) {
                    val code = Biometrics.availability(activity)
                    if (Biometrics.isAvailable(activity)) Biometrics.authenticate(activity, { vm.setBiometric(true); vm.c.appLock.unlock() }) { bioMessage = it }
                    else bioMessage = Biometrics.describe(code)
                }
            }
            bioMessage?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            SwitchRow("Écran protégé", "Bloque les captures d'écran et masque l'aperçu dans les applications récentes", settings.secureScreen) { vm.setSecureScreen(it) }
            Text("Base de données chiffrée (SQLCipher, AES-256) ; clé protégée par le Keystore Android. Sauvegarde Android dans le cloud désactivée. Aucune télémétrie.", style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Calculs") {
            Text("Méthode de calcul de la valeur de séance", style = MaterialTheme.typography.labelLarge)
            AggregationMethod.entries.forEach { m ->
                SwitchRow(m.label, m.description, settings.aggregation == m) { if (it) vm.setAggregation(m) }
            }
            Text("La méthode retenue est indiquée dans chaque rapport.", style = MaterialTheme.typography.labelSmall)
        }
        SectionCard("Urgences") {
            OutlinedTextField(emergency, { emergency = it.filter { c -> c.isDigit() || c == '+' }.take(6) }, label = { Text("Numéro des services d'urgence") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth())
            Text("112 : numéro d'urgence européen. En France, le 15 (SAMU) est aussi joignable. Adaptez-le à votre pays.", style = MaterialTheme.typography.labelSmall)
            if (emergency != settings.emergencyNumber) OutlinedButton(onClick = { vm.setEmergencyNumber(emergency) }) { Text("Enregistrer") }
        }
        SectionCard("Intelligence artificielle") {
            KeyValue("Fournisseur", settings.aiProvider.label)
            TextButton(onClick = { nav.navigate(Routes.AI_SETTINGS) }) { Text("Configurer l'IA et le consentement") }
        }
        SectionCard("Autres") {
            TextButton(onClick = { nav.navigate(Routes.REMINDERS) }) { Text("Rappels") }
            TextButton(onClick = { nav.navigate(Routes.PERMISSIONS) }) { Text("Diagnostic des autorisations") }
            TextButton(onClick = { nav.navigate(Routes.BACKUP) }) { Text("Sauvegarde, restauration, suppression") }
            TextButton(onClick = { nav.navigate(Routes.REFERENCES) }) { Text("Références médicales") }
        }
        SectionCard("À propos") {
            KeyValue("Version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Text("CardioSuivi est un outil personnel d'aide au suivi tensionnel. Il n'est pas un dispositif médical certifié, ne pose pas de diagnostic et ne remplace pas une consultation.", style = MaterialTheme.typography.bodySmall)
        }
        Disclaimer()
    }
}
