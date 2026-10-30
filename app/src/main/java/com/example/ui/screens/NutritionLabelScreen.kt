package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.viewmodel.*
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.CancellationException
import java.io.File

@Composable
fun NutritionLabelScreen(onBack: () -> Unit, onProduct: (SubstituteCandidate) -> Unit, ownProduct: Boolean = false,
    savingProduct: Boolean = false, saveMessage: String? = null, initialName: String = "", canSubmit: Boolean = true) {
    val context = LocalContext.current
    var name by rememberSaveable { mutableStateOf(initialName) }
    var calories by rememberSaveable { mutableStateOf("") }
    var protein by rememberSaveable { mutableStateOf("") }
    var carbs by rememberSaveable { mutableStateOf("") }
    var fat by rememberSaveable { mutableStateOf("") }
    var basis by rememberSaveable { mutableStateOf("100") }
    var unit by rememberSaveable { mutableStateOf("g") }
    var confirmed by rememberSaveable { mutableStateOf(false) }
    var readRequest by rememberSaveable { mutableStateOf<String?>(null) }
    val busy = readRequest != null
    var message by remember { mutableStateOf<String?>(null) }
    var rawText by rememberSaveable { mutableStateOf("") }
    var showText by rememberSaveable { mutableStateOf(false) }
    val photo = remember { File(File(context.cacheDir, "nutrition_labels").apply { mkdirs() }, "label.jpg") }
    val photoUri = remember { FileProvider.getUriForFile(context, "${context.packageName}.nutrition_labels", photo) }
    // A camera result can arrive after recreation. Keep both the parent screen and OCR request saved.
    LaunchedEffect(readRequest) {
        val request = readRequest ?: return@LaunchedEffect
        confirmed = false; message = null
        var completed = false
        try {
            rawText = withTimeout(30_000) {
                RecipeImageExtractor.extractText(context, android.net.Uri.parse(request), alignTableRows = true)
            }.take(24000)
            val values = NutritionLabelSupport.parse(rawText)
            calories = values.calories?.toString().orEmpty()
            protein = values.protein?.toString().orEmpty()
            carbs = values.carbs?.toString().orEmpty()
            fat = values.fat?.toString().orEmpty()
            message = if (rawText.isBlank()) "Nie znaleziono czytelnego tekstu. Zrób zdjęcie z bliska, w dobrym świetle lub wybierz zdjęcie z telefonu."
                else "Sprawdź wartości i kolumnę tabeli. Puste pola uzupełnij z etykiety; brak danych nie oznacza zera."
            completed = true
        } catch (_: TimeoutCancellationException) {
            message = "Odczyt trwał zbyt długo. Spróbuj ponownie lub wybierz zdjęcie z telefonu. Możesz też wpisać wartości ręcznie."
            completed = true
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) {
            message = "Nie udało się odczytać zdjęcia. Zrób wyraźne zdjęcie tabeli lub wpisz wartości."
            completed = true
        } finally {
            // Preserve the file/request if the activity is recreated during recognition.
            if (completed) { readRequest = null; if (request == photoUri.toString()) photo.delete() }
        }
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) readRequest = uri.toString()
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if ((success || photo.length() > 0L) && photo.length() > 0L) readRequest = photoUri.toString()
        else { photo.delete(); message = "Nie zapisano zdjęcia. Spróbuj ponownie lub wybierz zdjęcie z telefonu." }
    }
    val candidate = NutritionLabelSupport.product(name, calories, protein, carbs, fat, basis, unit)
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton(onClick = onBack) { Text(if (ownProduct) "Anuluj" else "Wróć do skanera") }
            Text(if (ownProduct) "Dodaj własny produkt" else "Odczytaj etykietę", style = MaterialTheme.typography.headlineSmall)
            if (ownProduct) Text("Wpisz wartości ręcznie lub odczytaj zdjęcie etykiety. Produkt zostanie w bazie na przyszłość.")
            Text("Zrób zdjęcie tabeli wartości odżywczych. Odczyt działa na telefonie, bez klucza API. Przy kilku kolumnach wybierz jedną i sprawdź liczby.")
            Button(enabled = !busy, onClick = { try { photo.delete(); camera.launch(photoUri) } catch (_: Exception) { message = "Nie można uruchomić aparatu. Wybierz zdjęcie z telefonu." } }) { Text("Zrób zdjęcie etykiety") }
            OutlinedButton(enabled = !busy, onClick = { gallery.launch("image/*") }) { Text("Wybierz zdjęcie etykiety") }
            if (busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Odczytywanie…") }
            message?.let { Text(it) }
            saveMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (rawText.isNotBlank()) {
                TextButton(onClick = { showText = !showText }) { Text(if (showText) "Zwiń tekst etykiety" else "Pokaż odczytany tekst") }
                if (showText) Text(rawText)
            }
            OutlinedTextField(name, { name = it; confirmed = false }, label = { Text("Nazwa produktu") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Text("Wartości dotyczą ilości podanej poniżej (np. 100 g albo porcji 30 g):")
            OutlinedTextField(basis, { basis = it; confirmed = false }, label = { Text("Ilość bazowa ($unit)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("g", "ml").forEach { item -> FilterChip(unit == item, { unit = item; confirmed = false }, label = { Text(item) }) }
            }
            @Composable fun field(value: String, label: String, tag: String, change: (String) -> Unit) {
                OutlinedTextField(value, { change(it); confirmed = false }, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag(tag), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
            field(calories, "Kalorie (kcal, nie kJ)", "label_calories") { calories = it }
            field(protein, "Białko (g)", "label_protein") { protein = it }
            field(carbs, "Węglowodany (g)", "label_carbs") { carbs = it }
            field(fat, "Tłuszcz (g)", "label_fat") { fat = it }
            Row { Checkbox(confirmed, { confirmed = it }); Text("Sprawdziłam/em wartości i ilość bazową na etykiecie.", Modifier.padding(top = 12.dp)) }
            if (candidate == null) Text("Wpisz nazwę, ilość bazową, kalorie i pełne makro. Możesz wpisać 0, jeśli etykieta podaje zero.")
            Button(enabled = canSubmit && !busy && !savingProduct && confirmed && candidate != null, onClick = { candidate?.let(onProduct) }, modifier = Modifier.fillMaxWidth().testTag("label_add_to_meal")) { Text(if (savingProduct) "Zapisywanie…" else if (ownProduct) "Zapisz produkt i wybierz posiłek" else "Wybierz posiłek i ilość produktu") }
        }
    }
}
