package com.example.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.PickVisualMediaRequest
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.health.HealthConnectManager
import com.example.viewmodel.*
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScaleMeasurementsPanel(main: DietaViewModel, model: ScaleMeasurementsViewModel = androidx.lifecycle.viewmodel.compose.viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    var automatic by remember { mutableStateOf(ScaleAutoSyncSupport.enabled(context)) }
    var extraOptions by remember { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, automatic) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && automatic) model.syncAutomatically()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) && automatic) model.syncAutomatically()
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var readingImage by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }
    var dateConfirmed by remember { mutableStateOf(true) }
    val rows by model.rows.collectAsStateWithLifecycle()
    val busy by model.busy.collectAsStateWithLifecycle()
    val message by model.message.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(false) }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    val fields = remember { mutableStateMapOf<String, String>() }
    var error by remember { mutableStateOf<String?>(null) }
    var deletion by remember { mutableStateOf<ScaleMeasurement?>(null) }
    val screenshotPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(6)) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            readingImage = true; error = null
            try {
                val result = RenphoScreenshotImport.read(context, uris)
                fields.clear(); fields.putAll(result.values); imported = true; dateConfirmed = false
                date = LocalDate.now().toString(); error = result.warnings.takeIf { it.isNotEmpty() }?.joinToString("\n")
                open = true
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { error = "Odczyt trwał zbyt długo. Wybierz wyraźniejszy zrzut Renpho i spróbuj ponownie." }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { error = e.message ?: "Nie udało się odczytać zrzutu Renpho." }
            finally { readingImage = false }
        }
    }
    val launcher = rememberLauncherForActivityResult(PermissionController.createRequestPermissionResultContract()) {
        model.sync { if (it == null) main.usability.changed() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Wskaźniki Renpho są szacunkami BIA. Nawodnienie i warunki ważenia mogą zmieniać wyniki. Porównuj trend, nie pojedynczy odczyt.")
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("Automatyczny import z Health Connect", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            Switch(checked = automatic, enabled = !busy, onCheckedChange = { automatic = it; ScaleAutoSyncSupport.setEnabled(context, it) }, modifier = Modifier.testTag("scale_automatic"))
        }
        Text("Pobieramy dostępne pomiary po powrocie do tego ekranu i przy synchronizacji Health Connect, bez zrzutów ekranu. W Renpho włącz zapisywanie danych do Health Connect i przyznaj FITMango uprawnienia poniżej.", style = MaterialTheme.typography.bodySmall)
        Text("Automatycznie dostępne są tylko dane zapisane przez Renpho: waga, tłuszcz, beztłuszczowa masa, kości, masa wody i BMR, jeśli źródło je udostępnia. Nie wszystkie 13 wskaźników ma odpowiednik w Health Connect.", style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { extraOptions = !extraOptions }) { Text(if (extraOptions) "Zwiń dodatkowe opcje" else "Dodatkowe wskaźniki — opcje ręczne") }
        if (extraOptions) {
        OutlinedButton(enabled = !busy && !readingImage, onClick = { fields.clear(); imported = false; dateConfirmed = true; date = LocalDate.now().toString(); error = null; open = true }, modifier = Modifier.fillMaxWidth().testTag("scale_add")) { Text("Dodaj pomiary Renpho") }
        OutlinedButton(enabled = !busy && !readingImage, onClick = { runCatching { screenshotPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.onFailure { error = "Nie udało się otworzyć galerii. Spróbuj ponownie." } }, modifier = Modifier.fillMaxWidth().testTag("scale_import_screenshot")) {
            Text(if (readingImage) "Odczyt zdjęć…" else "Importuj zrzuty Renpho — wszystkie wskaźniki")
        }
        Text("Wybierz do 6 zrzutów jednego ważenia z trzema kolumnami wyników. Odczyt działa na telefonie, bez Gemini. Brakujące lub nieczytelne pola pozostają puste.", style = MaterialTheme.typography.bodySmall)
        }
        OutlinedButton(enabled = !busy && !readingImage, onClick = { runCatching { launcher.launch(HealthConnectManager.SCALE_PERMISSIONS) }
            .onFailure { error = "Otwórz uprawnienia pomiarów ciała w Health Connect." } }, modifier = Modifier.fillMaxWidth()) { Text("Połącz / uprawnienia składu ciała") }
        TextButton(enabled = !busy && !readingImage, onClick = { model.sync { if (it == null) main.usability.changed() } }) { Text(if (busy) "Odczyt / zapis…" else "Odśwież pomiary teraz") }
        val lastRead = ScaleAutoSyncSupport.lastSync(context)
        Text(if (lastRead > 0) "Ostatni odczyt Health Connect: ${java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(lastRead))}" else "Brak zakończonego odczytu Health Connect.", style = MaterialTheme.typography.bodySmall)
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (rows.isEmpty()) Text("Brak zapisanych pomiarów Renpho. Puste pola nie są zerami.")
        else {
            Text("Ostatnia wartość każdego wskaźnika", style = MaterialTheme.typography.titleMedium)
            Text("Wartości mogą pochodzić z różnych dat i źródeł — nie tworzą jednego ważenia.", style = MaterialTheme.typography.bodySmall)
            ScaleMeasurementsSupport.metrics.forEach { (key, metric) ->
                val latest = rows.filter { key in it.values }.maxWithOrNull(compareBy<ScaleMeasurement> { it.date }.thenBy { it.time })
                if (latest != null) Text("${metric.label}: ${ScaleAutoSyncSupport.format(latest.values.getValue(key))} ${metric.unit}\n${latest.date} ${if (latest.time > 0) java.time.Instant.ofEpochMilli(latest.time).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("HH:mm")) else ""} • ${latest.source} • ${if (latest.origin == "health") "Health Connect" else "wpis zatwierdzony ręcznie"}", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { history = !history }) { Text(if (history) "Zwiń historię −" else "Historia / usuń pomiar +") }
            if (history) {
                Text("Ostatnie 20 zapisów. Usunięcie nie kasuje danych w aplikacji Renpho.", style = MaterialTheme.typography.bodySmall)
                rows.sortedWith(compareByDescending<ScaleMeasurement> { it.date }.thenByDescending { it.time }).take(20).forEach { r ->
                    HorizontalDivider()
                    Text("${r.date} • ${r.source}", style = MaterialTheme.typography.labelLarge)
                    Text(r.values.entries.joinToString(" • ") { (k, v) -> "${ScaleMeasurementsSupport.metrics.getValue(k).label}: ${ScaleAutoSyncSupport.format(v)} ${ScaleMeasurementsSupport.metrics.getValue(k).unit}" }, style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !busy && !readingImage, onClick = { deletion = r }) { Text("Usuń zapis") }
                }
            }
        }
        Text("Po zapisaniu pomiary wejdą do następnego raportu AI, jeśli ich data mieści się w analizowanym tygodniu. Nie zmieniają automatycznie celu kalorii ani profilu.", style = MaterialTheme.typography.bodySmall)
    }
    if (open) ModalBottomSheet(onDismissRequest = { if (!busy) open = false }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Pomiar Renpho", style = MaterialTheme.typography.titleLarge)
            Text("Wpisz tylko znane wartości z jednego ważenia. Nie przepisuj różnic (+/−) ani daty pomiaru porównawczego.")
            PlanDateSelector(date, { date = it }, "scale_date", showDays = false)
            Text("Data pomiaru: $date")
            if (imported) {
                Text("Rozpoznano ${fields.values.count { it.isNotBlank() }}/13 pól. Sprawdź każdą wartość. Data porównawcza z Renpho nie jest datą tego pomiaru.")
                Row { Checkbox(dateConfirmed, { dateConfirmed = it }); Text("Potwierdzam datę ważenia i sprawdziłam rozpoznane wartości", Modifier.weight(1f)) }
            }
            ScaleMeasurementsSupport.metrics.filterKeys { it != "waterKg" }.forEach { (key, metric) ->
                OutlinedTextField(fields[key].orEmpty(), { fields[key] = it.take(12) }, label = { Text("${metric.label} ${metric.unit}") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("scale_field_$key"))
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !busy && !readingImage && dateConfirmed, onClick = {
                model.save(date, fields.toMap(), if (imported) "Renpho — odczyt ze zdjęcia" else "Renpho — wpis ręczny") { e -> error = e; if (e == null) { main.usability.changed(); open = false } }
            }, modifier = Modifier.fillMaxWidth().testTag("scale_save")) { Text(if (busy) "Zapisywanie…" else "Zapisz pomiar") }
            TextButton(enabled = !busy && !readingImage, onClick = { open = false }) { Text("Anuluj") }
            Spacer(Modifier.height(24.dp))
        }
    }
    deletion?.let { row -> AlertDialog(onDismissRequest = { deletion = null }, title = { Text("Usunąć zapis pomiaru?") }, text = { Text("${row.date} • ${row.source}. Ponowna synchronizacja może ponownie pobrać rekord Health Connect.") },
        confirmButton = { TextButton(enabled = !busy && !readingImage, onClick = { model.remove(row.id) { if (it == null) main.usability.changed() }; deletion = null }) { Text("Usuń") } },
        dismissButton = { TextButton(onClick = { deletion = null }) { Text("Anuluj") } }) }
}
