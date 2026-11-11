package com.example.ui.components

import com.example.ui.components.FitMangoIcon as Icon

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.viewmodel.DietaViewModel

@Composable
fun BackupSettingsCard(viewModel: DietaViewModel) {
    val backup = viewModel.backup
    val backupStatus by backup.status.state.collectAsStateWithLifecycle()
    val busy by backup.busy.collectAsStateWithLifecycle()
    val message by backup.message.collectAsStateWithLifecycle()
    val preview by backup.preview.collectAsStateWithLifecycle()
    val hasRecovery by backup.hasRecovery.collectAsStateWithLifecycle()
    var backupPassword by remember { mutableStateOf("") }
    var showBackupDetails by remember { mutableStateOf(false) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { backup.export(it, backupPassword) }
    }
    val saveAutomatic = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { backup.exportAutomatic(it) }
    }
    val load = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { backup.prepare(it, backupPassword) }
    }
    val saveRecovery = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri?.let { backup.exportRecovery(it) }
    }
    CollapsibleMangoCard("Kopia zapasowa", "backup", Modifier.testTag("backup_settings_card")) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Backup, null)
                Text("Kopia zapasowa", style = MaterialTheme.typography.titleMedium)
            }
            Text("Ostatnia kopia do pliku: ${backup.status.date(backupStatus.lastExport)}")
            Text("Kopia automatyczna na telefonie: ${backup.status.date(backupStatus.lastAutomatic)}")
            Row { Switch(backupStatus.automatic, { backup.status.automatic(it) }); Text("Automatyczna kopia lokalna") }
            Text("Kopia lokalna pozostaje na telefonie. Eksport zewnętrzny zapisz osobno.", style = MaterialTheme.typography.bodySmall)
            OutlinedButton(onClick = { backup.saveAutomaticNow() }, enabled = !busy) { Text("Zapisz kopię lokalną teraz") }
            if (backupStatus.lastAutomatic > 0) {
                OutlinedButton(onClick = { saveAutomatic.launch("FITMango-automatyczna-${java.time.LocalDate.now()}.zip") },
                    enabled = !busy, modifier = Modifier.testTag("backup_export_automatic")) {
                    Text("Eksportuj kopię automatyczną")
                }
                Text("Wybierz Pobrane (Downloads), Dysk Google lub inne dostępne miejsce. Ta kopia nie jest szyfrowana hasłem — przechowuj ją prywatnie.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { backup.prepareAutomatic() }, enabled = !busy) { Text("Przywróć kopię automatyczną") }
            }
            HorizontalDivider()
            Text("Kopia szyfrowana • dane, zdjęcia i klucze API", style = MaterialTheme.typography.titleSmall)
            OutlinedTextField(value = backupPassword, onValueChange = { backupPassword = it }, label = { Text("Hasło kopii (minimum 8 znaków)") }, visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Zachowaj hasło: bez niego nie odzyskasz danych z zaszyfrowanej kopii. Kopię zapisz poza aplikacją.", style = MaterialTheme.typography.bodySmall)
            Button(onClick = { save.launch("FITMango-kopia-${java.time.LocalDate.now()}.zip") }, enabled = !busy && backupPassword.length >= 8,
                modifier = Modifier.fillMaxWidth().testTag("backup_export")) { Text("Zapisz kopię") }
            OutlinedButton(onClick = { load.launch(arrayOf("application/zip", "application/octet-stream", "application/x-zip-compressed")) }, enabled = !busy,
                modifier = Modifier.fillMaxWidth().testTag("backup_import")) { Text("Przywróć z pliku") }
            if (hasRecovery) TextButton(onClick = { saveRecovery.launch("FITMango-przed-przywroceniem.zip") }, enabled = !busy) {
                Text("Zapisz kopię sprzed przywrócenia")
            }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Przetwarzanie kopii…") }
            message?.let { Text(it, modifier = Modifier.testTag("backup_result")) }
            TextButton(onClick = { showBackupDetails = !showBackupDetails }) {
                Text(if (showBackupDetails) "▲ Ukryj szczegóły" else "▼ Szczegóły bezpieczeństwa")
            }
            if (showBackupDetails) {
                Text("Kopia szyfrowana zawiera klucze Gemini, Pixabay i zdjęcia lokalne. Automatyczna kopia lokalna nie zawiera kluczy. Health Connect wymaga ponownego połączenia.", style = MaterialTheme.typography.bodySmall)
                Text("Nie przywracaj plików 0 KB. W razie błędu zachowaj działającą kopię lokalną.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    preview?.let { prepared ->
        AlertDialog(onDismissRequest = { backup.cancelPreview() },
            title = { Text("Przywrócić kopię?") },
            text = { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Przepisy: ${prepared.archive.data.recipes.size}; posiłki: ${prepared.archive.data.meals.size}; pomiary: ${prepared.archive.data.measurements.size}.")
                Text("Ta operacja zastąpi obecne dane. Najpierw zapisz własną kopię. Aplikacja zachowa też lokalną kopię sprzed przywrócenia.")
                if (prepared.archive.missingFiles.isNotEmpty()) Text("W tej kopii brakuje ${prepared.archive.missingFiles.size} plików. Tekst przepisów i dokumentów pozostaje dostępny.")
            } },
            confirmButton = { Button(onClick = { backup.restore() }, enabled = !busy, modifier = Modifier.testTag("backup_confirm_restore")) { Text("Przywróć dane") } },
            dismissButton = { TextButton(onClick = { backup.cancelPreview() }, enabled = !busy) { Text("Anuluj") } })
    }
}
