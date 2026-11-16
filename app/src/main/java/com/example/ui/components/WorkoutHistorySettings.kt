package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent
import com.example.viewmodel.*

@Composable
fun WorkoutHistorySettings(workouts: List<WorkoutSession>) {
    val context = LocalContext.current
    var showTransferSettings by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { showTransferSettings = !showTransferSettings },
        modifier = Modifier.fillMaxWidth()) {
        Text(if (showTransferSettings) "▲ Ukryj ustawienia historii" else "⚙ Ustawienia historii treningów")
    }
    if (showTransferSettings) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Kopie, eksport i przywracanie", style = MaterialTheme.typography.titleMedium)
            var transferMessage by rememberSaveable { mutableStateOf<String?>(null) }
            var pendingImport by remember { mutableStateOf<String?>(null) }
            val saveHistory = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
            ) { uri ->
                if (uri != null) {
                    transferMessage = runCatching {
                        context.contentResolver.openOutputStream(uri, "wt")!!.bufferedWriter().use {
                            it.write(WorkoutSessionStore.exportJson(context))
                        }
                        "Historia zapisana w pliku JSON."
                    }.getOrElse { "Eksport nieudany: ${it.message}" }
                }
            }
            val openHistory = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
            ) { uri ->
                if (uri != null) {
                    transferMessage = runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)!!.use { stream ->
                            val output = java.io.ByteArrayOutputStream()
                            val chunk = ByteArray(8192)
                            while (true) {
                                val count = stream.read(chunk)
                                if (count < 0) break
                                require(output.size() + count <= 2_000_000) { "Plik jest za duży" }
                                output.write(chunk, 0, count)
                            }
                            output.toString("UTF-8")
                        }
                        WorkoutSessionStore.validateRaw(bytes)
                        pendingImport = bytes
                        "Plik sprawdzony. Potwierdź przywrócenie."
                    }.getOrElse { "Nie można odczytać historii: ${it.message}" }
                }
            }
            OutlinedButton(onClick = { saveHistory.launch("FITMango-historia-treningow.json") },
                modifier = Modifier.fillMaxWidth()) { Text("📁 Zapisz historię do pliku JSON") }
            OutlinedButton(onClick = { openHistory.launch(arrayOf("application/json", "text/plain")) },
                modifier = Modifier.fillMaxWidth()) { Text("📂 Wczytaj historię z pliku JSON") }
            transferMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (pendingImport != null) {
                AlertDialog(onDismissRequest = { pendingImport = null },
                    title = { Text("Wczytać historię treningów?") },
                    text = { Text("Obecna historia zostanie zachowana jako punkt cofnięcia. " +
                        "Nowa historia zastąpi bieżącą. Po operacji otwórz ponownie ekran.") },
                    confirmButton = {
                        Button(onClick = {
                            transferMessage = runCatching {
                                val count = WorkoutSessionStore.importJson(context, pendingImport!!)
                                "Wczytano $count sesji. Otwórz ponownie ekran treningu."
                            }.getOrElse { "Import nieudany: ${it.message}" }
                            pendingImport = null
                        }) { Text("Wczytaj") }
                    },
                    dismissButton = {
                        TextButton(onClick = { pendingImport = null }) { Text("Anuluj") }
                    })
            }
            OutlinedButton(onClick = {
                runCatching {
                    val json = WorkoutSessionStore.exportJson(context)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_SUBJECT, "FITMango — historia treningów")
                        putExtra(Intent.EXTRA_TEXT, json)
                    }
                    context.startActivity(Intent.createChooser(send, "Eksportuj historię treningów"))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("💾 Eksportuj historię treningów (JSON)") }
            Text("Eksport obejmuje wyłącznie historię treningów. Nie zawiera kluczy API ani danych konta.",
                style = MaterialTheme.typography.bodySmall)
            var confirmRestore by rememberSaveable { mutableStateOf(false) }
            var restoreMessage by rememberSaveable { mutableStateOf<String?>(null) }
            val restoreAvailable = remember(workouts) {
                runCatching { WorkoutSessionStore.restorePoint(context) != null }.getOrDefault(false)
            }
            if (restoreAvailable) {
                OutlinedButton(onClick = { confirmRestore = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("↩ Przywróć poprzednią historię treningów")
                }
            }
            restoreMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (confirmRestore) {
                AlertDialog(
                    onDismissRequest = { confirmRestore = false },
                    title = { Text("Przywrócić historię?") },
                    text = { Text("Zastąpi to bieżącą historię poprzednim punktem przywracania. " +
                        "Obecna historia zostanie zachowana jako punkt cofnięcia. " +
                        "Po przywróceniu otwórz ponownie ekran treningu.") },
                    confirmButton = {
                        Button(onClick = {
                            restoreMessage = runCatching {
                                val count = WorkoutSessionStore.restorePrevious(context)
                                "Przywrócono $count sesji. Odśwież ekran treningu."
                            }.getOrElse { "Nie udało się przywrócić historii: ${it.message ?: "błąd danych"}" }
                            confirmRestore = false
                        }) { Text("Przywróć") }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmRestore = false }) { Text("Anuluj") }
                    }
                )
            }
        }
    }
}
